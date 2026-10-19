import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

// 고정 서명키(선택). 저장소에 올리지 않는 release.properties 또는 CI 환경 변수에서 읽는다.
val releaseProps = Properties().apply {
    rootProject.file("release.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun releaseValue(key: String, env: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: releaseProps.getProperty(key)?.takeIf { it.isNotBlank() }
val releaseStore = rootProject.file(releaseValue("storeFile", "RELEASE_STORE_FILE") ?: "release.jks")
val releaseStorePassword = releaseValue("storePassword", "RELEASE_STORE_PASSWORD")

android {
    namespace = "com.earalarm.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.earalarm.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        if (releaseStore.exists() && releaseStorePassword != null) create("release") {
            storeFile = releaseStore
            storePassword = releaseStorePassword
            keyAlias = releaseValue("keyAlias", "RELEASE_KEY_ALIAS") ?: "earalarm"
            // PKCS12 키는 저장소 비밀번호와 키 비밀번호가 같다.
            keyPassword = releaseValue("keyPassword", "RELEASE_KEY_PASSWORD") ?: releaseStorePassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 고정 서명키가 있으면 그것으로, 없으면 디버그 키로 서명한다.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    testOptions {
        unitTests {
            // Robolectric이 매니페스트·리소스·테마를 읽을 수 있게 한다.
            isIncludeAndroidResources = true
        }
    }
}

// 네이티브 그래픽으로 큰 화면을 그리므로 기본 테스트 힙(512MB)보다 넉넉하게 준다.
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
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
