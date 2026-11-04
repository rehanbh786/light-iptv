plugins {
    id("com.android.application")
}

android {
    namespace = "com.lightiptv.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lightiptv.app"
        minSdk = 21
        targetSdk = 33
        versionCode = 3
        versionName = "1.2"
    }

    // Same key on every GitHub build, so new versions install over old ones.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // Plain Java + Media3, no native libraries: one APK runs on
    // 32-bit and 64-bit boxes alike.
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.annotation:annotation-experimental:1.3.1")
}
