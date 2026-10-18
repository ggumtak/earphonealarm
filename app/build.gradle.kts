plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.earalarm.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.earalarm.app"
        minSdk = 28
        targetSdk = 35
        // CI는 실행 번호를 넘겨 매 빌드가 이전 설치 위에 업데이트되게 한다.
        val build = System.getenv("APP_BUILD_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"
    }

    // 고정 서명키. 키 파일은 암호화된 채로 저장소에 두고, 비밀번호는 GitHub Secret(SIGNING_PASSWORD)로만 받는다.
    val signingPassword = System.getenv("SIGNING_PASSWORD")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (signingPassword != null) create("release") {
            storeFile = rootProject.file("signing/release.p12")
            storeType = "PKCS12"
            storePassword = signingPassword
            keyAlias = "earalarm"
            keyPassword = signingPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
}
