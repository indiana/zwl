package com.indiana.zwl.di

import javax.inject.Qualifier

/**
 * Background dispatcher for I/O-bound work (sync, engine init). Injected so
 * unit tests can supply a controlled [kotlinx.coroutines.test.TestDispatcher]
 * instead of real threads — required for deterministic coroutine tests.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
