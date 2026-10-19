package com.earalarm.app

import android.Manifest
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.earalarm.app.data.local.AlarmEntity
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * 실제 화면을 JVM에서 그려 PNG로 남기는 스크린샷 테스트.
 *
 * - `./gradlew testDebugUnitTest` : Roborazzi가 꺼진 상태라 캡처는 건너뛰고 화면 동작만 확인한다.
 * - `./gradlew recordRoborazziDebug` : app/screenshots 폴더의 PNG 를 새로 만든다.
 *
 * 화면에 `while (true) { delay(...) }` 루프가 있어 waitForIdle로는 안정 상태가 오지 않는다.
 * 그래서 시계를 직접 돌린다(mainClock.autoAdvance = false).
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi-night")
class ScreenshotTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        // 알림 권한 요청 UI가 화면에 끼어들지 않도록 미리 허용해 둔다.
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // 화면이 뜨기 전에 알람 2개를 저장소에 직접 넣는다(AlarmManager를 거치지 않는다).
        val repository = (app as EarAlarmApp).container.alarmRepository
        runBlocking {
            repository.save(AlarmEntity(hour = 6, minute = 0, repeatMask = 127, label = "출근"))
            repository.save(AlarmEntity(hour = 11, minute = 10, enabled = false))
        }

        compose.mainClock.autoAdvance = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
        settle()
    }

    @After
    fun tearDown() {
        runCatching { scenario?.close() }
    }

    @Test
    fun timer() {
        capture("01_timer.png")
    }

    @Test
    fun alarms() {
        step("알람 탭") { compose.onAllNodesWithText("알람").onFirst().performClick() }
        settle()
        capture("02_alarms.png")
    }

    @Test
    fun alarmEditor() {
        step("알람 탭") { compose.onAllNodesWithText("알람").onFirst().performClick() }
        settle()
        step("알람 추가") { compose.onAllNodesWithContentDescription("알람 추가").onFirst().performClick() }
        settle()
        capture("03_alarm_editor.png")
    }

    @Test
    fun settings() {
        step("설정") { compose.onAllNodesWithContentDescription("설정").onFirst().performClick() }
        settle()
        capture("04_settings.png")
    }

    /**
     * Room·DataStore는 실제 스레드에서 돌고 Compose 시계는 멈춰 있으므로,
     * 실시간으로 조금씩 기다리면서 시계와 메인 루퍼를 같이 돌린다.
     */
    private fun settle(rounds: Int = 30, frameMs: Long = 100) {
        repeat(rounds) {
            Thread.sleep(40)
            ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeBy(frameMs)
        }
        ShadowLooper.idleMainLooper()
    }

    /** 한 단계가 실패해도 지금 보이는 화면은 그대로 캡처하도록 예외를 삼킨다. */
    private fun step(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            System.err.println("ScreenshotTest step '$name' failed: $e")
        }
    }

    /** 다이얼로그·바텀시트까지 포함해 화면 전체를 app/screenshots 아래에 저장한다. */
    private fun capture(fileName: String) {
        step("capture $fileName") { captureScreenRoboImage("screenshots/$fileName") }
    }
}
