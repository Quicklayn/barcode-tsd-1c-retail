plugins {
    id("com.android.application")
}

android {
    namespace = "ru.local.barcodetsd"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.local.barcodetsd"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
