import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

// 고정 서명키. signing/release.p12는 암호화된 PKCS12 키 파일이라 저장소에 올려도 비밀번호 없이는 쓸 수 없다.
// 비밀번호는 저장소에 두지 않고 GitHub Actions 시크릿 SIGNING_PASSWORD(환경 변수)로만 받는다.
// 로컬 빌드는 gitignore된 루트 release.properties의 storePassword로 대체할 수 있다.
val releaseProps = Properties().apply {
    rootProject.file("release.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val signingPassword: String? =
    System.getenv("SIGNING_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: releaseProps.getProperty("storePassword")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.earalarm.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.earalarm.app"
        minSdk = 28
        targetSdk = 35
        // 예전 1.0.x(실행 번호 기반) 빌드의 versionCode보다 커야 업데이트되므로 10으로 건너뛰었다. 이후 푸시마다 +1.
        versionCode = 10
        versionName = "1.2"
    }

    signingConfigs {
        // 비밀번호가 있을 때만 고정 키 설정을 만든다. PKCS12는 키 비밀번호가 저장소 비밀번호와 같다.
        signingPassword?.let { pw ->
            create("release") {
                storeFile = rootProject.file("signing/release.p12")
                storeType = "PKCS12"
                storePassword = pw
                keyAlias = releaseProps.getProperty("keyAlias")?.takeIf { it.isNotBlank() } ?: "earalarm"
                keyPassword = pw
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 고정 서명키가 있으면 그것으로, 비밀번호가 없으면 디버그 키로 서명한다.
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
