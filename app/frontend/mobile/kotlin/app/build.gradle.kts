import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "co.edu.konradlorenz.kapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "co.edu.konradlorenz.kapp"
        // 26 is the first release with adaptive icons, which is what the launcher icon uses.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-alpha.1"
    }

    buildTypes {
        // Where the API is, per build type. Debug talks to the Prism mocks on the developer's
        // computer: 10.0.2.2 is the emulator's name for it, and a phone on the same Wi-Fi needs
        // the computer's LAN IP instead (app/backend/microservices/mock/README.md). The trailing
        // slash is required by Retrofit.
        //
        // Release has its own value so it can never point at a mock. KApp has no deployed server
        // yet, so the host is a placeholder until there is one; it is HTTPS because release
        // refuses plain HTTP.
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:4000/\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://kapp.konradlorenz.edu.co/\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
