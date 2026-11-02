plugins {
    id("com.android.application")
}

android {
    namespace = "com.malik.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.malik.app"
        minSdk = 26
        targetSdk = 34
        // رقم الإصدار يزيد مع كل بناء على GitHub حتى يُحدَّث التطبيق فوق القديم دون فقدان التقدم
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // مفتاح توقيع ثابت: كل نسخة جديدة تُثبَّت فوق القديمة وتحتفظ بنجوم مالك وتسجيلاته
    signingConfigs {
        create("release") {
            storeFile = file("malik.keystore")
            storePassword = "malikapp2026"
            keyAlias = "malik"
            keyPassword = "malikapp2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
}
