package com.indiana.zwl.data.sync

import android.content.Context
import androidx.work.ListenableWorker.Result as WorkResult
import androidx.work.WorkerParameters
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.usecase.SyncPoiUseCase
import com.indiana.zwl.domain.usecase.SyncZonesUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncWorkerTest {

    private val syncZonesUseCase: SyncZonesUseCase = mockk()
    private val syncPoiUseCase: SyncPoiUseCase = mockk()
    private val context: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)

    private fun createWorker() = SyncWorker(context, workerParams, syncZonesUseCase, syncPoiUseCase)

    private fun <T> reportFailures(block: () -> T): T {
        try {
            return block()
        } catch (e: Throwable) {
            println("SyncWorkerTest failure: " + e.stackTraceToString())
            throw e
        }
    }

    @Test
    fun `doWork runs zone and poi sync concurrently`() = reportFailures {
        runBlocking {
            coEvery { syncZonesUseCase() } coAnswers {
                delay(500)
                Result.success(emptyList<Zone>())
            }
            coEvery { syncPoiUseCase() } coAnswers {
                delay(500)
                Result.success(Unit)
            }

            val start = System.nanoTime()
            val result = createWorker().doWork()
            val elapsedMs = (System.nanoTime() - start) / 1_000_000

            assertEquals(WorkResult.success(), result)
            assertTrue(
                "Zone and POI syncs appear to run sequentially (took ${elapsedMs}ms for two 500ms syncs)",
                elapsedMs < 900
            )
        }
    }

    @Test
    fun `doWork returns success when both syncs succeed`() = reportFailures {
        runBlocking {
            coEvery { syncZonesUseCase() } returns Result.success(emptyList<Zone>())
            coEvery { syncPoiUseCase() } returns Result.success(Unit)

            assertEquals(WorkResult.success(), createWorker().doWork())
        }
    }

    @Test
    fun `doWork returns retry when a sync fails`() = reportFailures {
        runBlocking {
            coEvery { syncZonesUseCase() } returns Result.failure(Exception("zones sync failed"))
            coEvery { syncPoiUseCase() } returns Result.success(Unit)

            assertEquals(WorkResult.retry(), createWorker().doWork())
        }
    }
}
