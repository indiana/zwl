plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.indiana.zwl.shared"
        compileSdk = 37
        minSdk = 26

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // TODO: Re-enable iOS targets when building on macOS (CI or Mac)
    // listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
    //     target.binaries.framework { baseName = "shared"; isStatic = true }
    // }

    sourceSets {
        val commonMain = getByName("commonMain") {
            dependencies {
                // KTS spatial library — Kotlin 2.4+ resolved
                implementation(libs.kts.core)
                implementation(libs.kts.io.wkt)

                // Database
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)

                // HTTP
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)

                // Serialization
                implementation(libs.kotlinx.serialization.json)

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.kotlinxCoroutines.get()}")

                // DI
                implementation(libs.koin.core)
            }
        }
        val androidMain = getByName("androidMain") {
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.sqldelight.android.driver)
                implementation(libs.koin.android)
            }
        }
        // TODO: Uncomment when iOS targets are enabled
        // val iosMain by getting {
        //     dependencies {
        //         implementation(libs.ktor.client.darwin)
        //         implementation(libs.sqldelight.native.driver)
        //     }
        // }
    }
}

sqldelight {
    databases {
        create("ZwlDatabase") {
            packageName.set("com.indiana.zwl.data.local")
        }
    }
}
