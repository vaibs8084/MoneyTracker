plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.vaibhav.moneytracker"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.vaibhav.moneytracker"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/INDEX.LIST"
        }
    }

    testOptions {
        execution = "ANDROID_TEST_ORCHESTRATOR"
    }
}

dependencies {

    implementation(
        platform(
            libs.androidx.compose.bom
        )
    )

    implementation(
        libs.androidx.activity.compose
    )

    implementation(
        libs.androidx.compose.material3
    )

    implementation(
        libs.androidx.compose.ui
    )

    implementation(
        libs.androidx.compose.ui.graphics
    )

    implementation(
        libs.androidx.compose.ui.tooling.preview
    )

    implementation(
        libs.androidx.core.ktx
    )

    implementation(
        libs.androidx.lifecycle.runtime.ktx
    )

    implementation(
        libs.androidx.lifecycle.viewmodel.ktx
    )

    implementation(
        libs.androidx.lifecycle.viewmodel.compose
    )

    // Room
    implementation(
        libs.androidx.room.runtime
    )

    implementation(
        libs.androidx.room.ktx
    )

    ksp(
        libs.androidx.room.compiler
    )

    // Coroutines
    implementation(
        libs.kotlinx.coroutines.android
    )

    // PDFBox for PDF statement parsing
    implementation(
        libs.pdfbox.android
    )

    // Google Auth & Credentials Foundation
    implementation(
        libs.play.services.auth
    )

    implementation(
        libs.credentials
    )

    implementation(
        libs.credentials.play.services.auth
    )

    implementation(
        libs.googleid
    )

    // Google Drive & Sheets REST API Foundation
    implementation(
        libs.google.apiclient
    )

    implementation(
        libs.google.apidrive
    )

    implementation(
        libs.google.apisheets
    )

    implementation(
        libs.google.http.client.gson
    )

    implementation(
        libs.google.http.client.android
    )

    // Tests
    testImplementation(
        libs.junit
    )

    androidTestImplementation(
        platform(
            libs.androidx.compose.bom
        )
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    androidTestUtil(
        libs.androidx.test.orchestrator
    )

    androidTestUtil(
        libs.androidx.test.services
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )
}
