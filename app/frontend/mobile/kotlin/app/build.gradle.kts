import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "co.edu.konradlorenz.kapp"
    // 37 because current Compose, Coil and OkHttp releases refuse to build against anything older.
    compileSdk = 37

    defaultConfig {
        applicationId = "co.edu.konradlorenz.kapp"
        // 26 is the first release with adaptive icons, which is what the launcher icon uses.
        minSdk = 26
        // Targeting 37 turns on Android 17's local network protection: a connection to a private
        // address times out unless the app holds ACCESS_LOCAL_NETWORK. Only a debug build talks to
        // one, so only the debug manifest declares it and LocalNetworkAccess asks for it.
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-alpha.1"

        // Microsoft sign-in (issue #46, ADR 0003), from Gradle properties so no id or tenant is
        // committed: ~/.gradle/gradle.properties on a developer's machine, -P on CI. All three come
        // from the university's app registration (#62); until it exists they are empty, and a
        // debug build signs in with a fake ID token the mocks accept. The module README has the
        // details, including how to get the signature hash of a signing key.
        val msal = { name: String -> providers.gradleProperty("kapp.msal.$name").getOrElse("") }
        buildConfigField("String", "MSAL_CLIENT_ID", "\"${msal("clientId")}\"")
        buildConfigField("String", "MSAL_TENANT_ID", "\"${msal("tenantId")}\"")
        buildConfigField("String", "MSAL_SIGNATURE_HASH", "\"${msal("signatureHash")}\"")
        // The redirect path BrowserTabActivity answers to, in AndroidManifest.xml.
        manifestPlaceholders["msalSignatureHash"] = msal("signatureHash")
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
        //
        // Two Gradle properties move a debug build from the mocks to the real services on the same
        // computer, with sign-in through the compose file's fake OIDC server. Set them in
        // ~/.gradle/gradle.properties or with -P, never in the committed gradle.properties:
        //   kapp.api.baseUrl=http://10.0.2.2:8080/
        //   kapp.dev.oidcIssuer=http://10.0.2.2:8090/kapp
        // See "Running against the local backend" in the module README.
        debug {
            val property = { name: String, default: String -> providers.gradleProperty(name).getOrElse(default) }
            buildConfigField("String", "API_BASE_URL", "\"${property("kapp.api.baseUrl", "http://10.0.2.2:4000/")}\"")
            buildConfigField("String", "DEV_OIDC_ISSUER", "\"${property("kapp.dev.oidcIssuer", "")}\"")
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

    implementation(libs.msal)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
}
