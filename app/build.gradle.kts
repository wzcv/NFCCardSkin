plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.wu.nfccardskin"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.wu.nfccardskin"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                // Only shrink libraries; module code (hook entry, provider) stays as written.
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**", "coil3.**", "okhttp3.**", "okio.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coui.ui)
    implementation(libs.coui.preference)
    implementation(libs.coui.icons)
    implementation(libs.coui.blur)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Xposed: the API is provided by LSPosed at runtime; the service lib talks to the manager.
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
