plugins {
    id("com.android.application")
}

android {
    namespace = "com.sunpanel.widget"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sunpanel.widget"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        // debug：不混淆，方便调试
        debug {
            isMinifyEnabled = false
        }
        release {
            // release：开启 R8 混淆 + 资源压缩
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

// Kotlin 编译器配置（必须在 android{} 外部，项目级扩展）
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // 网络请求: Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Gson 解析
    implementation("com.google.code.gson:gson:2.10.1")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Fragment + WebView
    implementation("androidx.fragment:fragment-ktx:1.7.1")

    // WorkManager (后台定时刷新)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}