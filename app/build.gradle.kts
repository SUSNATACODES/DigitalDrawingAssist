plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.susnatacodes.digitaldrawingassist"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.susnatacodes.digitaldrawingassist"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 🔐 SIGNING CONFIG (IMPORTANT)
    signingConfigs {
        create("release") {
            storeFile = file("drawassist.keystore")
            storePassword = "susnatacodesdrawassist9641"
            keyAlias = "drawassist"
            keyPassword = "susnatacodesdrawassist9641"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false

            // ✅ CONNECT SIGNING
            signingConfig = signingConfigs.getByName("release")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}