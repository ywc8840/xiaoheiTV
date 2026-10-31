plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.xiaohei.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xiaohei.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.6.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.8.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.1")
    implementation("androidx.media3:media3-ui:1.8.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
