plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    // TODO: Re-enable iOS targets when building on macOS (CI or Mac)
    // listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
    //     target.binaries.framework { baseName = "shared"; isStatic = true }
    // }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // NOTE: kts-core removed — requires Kotlin 2.4+, SpatialEngine stays in :app JVM-only
                // Will be added back when Kotlin is upgraded to 2.x

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
        val androidMain by getting {
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

android {
    namespace = "com.indiana.zwl.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("ZwlDatabase") {
            packageName.set("com.indiana.zwl.data.local")
        }
    }
}
