package com.earalarm.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.earalarm.app.ui.theme.EarAlarmColors
import com.earalarm.app.update.AppUpdater
import com.earalarm.app.update.UpdateRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

private enum class UpdateAction { CHECK, PERMISSION, DOWNLOAD, INSTALL }

@Composable
internal fun AppUpdateSection(version: String, build: Long) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val updater = remember(context) { AppUpdater(context) }
    val scope = rememberCoroutineScope()
    val progress = remember { MutableStateFlow(0) }
    val percent by progress.collectAsState()
    var update by remember { mutableStateOf<UpdateRelease?>(null) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var action by remember { mutableStateOf<UpdateAction?>(null) }
    var message by remember { mutableStateOf("") }

    val systemInstaller = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        action = null
        message = "설치 화면에서 취소했다면 다시 설치할 수 있어요."
    }

    fun launchInstaller(file: File) {
        try {
            action = UpdateAction.INSTALL
            systemInstaller.launch(updater.installer(file))
        } catch (_: Exception) {
            action = null
            message = "안드로이드 설치 화면을 열지 못했어요."
        }
    }

    fun downloadOrInstall(release: UpdateRelease) {
        val existing = downloaded?.takeIf { it.isFile }
        if (existing != null) {
            launchInstaller(existing)
            return
        }
        progress.value = 0
        action = UpdateAction.DOWNLOAD
        scope.launch {
            try {
                val file = updater.download(release) { progress.value = it }
                downloaded = file
                action = null
                message = "다운로드가 끝났어요. 설치를 눌러 계속할 수 있어요."
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) launchInstaller(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppUpdater.InvalidUpdate) {
                action = null
                message = e.message.orEmpty()
            } catch (_: Exception) {
                action = null
                message = "다운로드를 완료하지 못했어요. 연결을 확인하고 다시 시도해 주세요."
            }
        }
    }

    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        action = null
        val release = update
        if (release != null && updater.canInstall()) downloadOrInstall(release)
        else message = "이어폰 알람 앱의 ‘이 출처 허용’을 켜면 업데이트를 설치할 수 있어요."
    }

    suspend fun checkForUpdates() {
        action = UpdateAction.CHECK
        try {
            val release = updater.check()
            update = release.takeIf { it.isNewerThan(version) }
            message = if (update == null) "최신 버전을 사용하고 있어요." else "새 버전 v${release.version}이 있어요."
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            message = "업데이트를 확인할 수 없어요. 잠시 뒤 다시 시도해 주세요."
        } finally {
            action = null
        }
    }

    LaunchedEffect(Unit) { checkForUpdates() }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("설치된 버전 v$version ($build)", color = EarAlarmColors.Text, fontSize = 15.sp)
        if (message.isNotEmpty()) Text(message, color = EarAlarmColors.TextMuted, fontSize = 13.sp)
        OutlinedButton(
            enabled = action == null,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = EarAlarmColors.TealBright),
            onClick = {
                val release = update
                when {
                    release == null -> scope.launch { checkForUpdates() }
                    updater.canInstall() -> downloadOrInstall(release)
                    else -> {
                        try {
                            action = UpdateAction.PERMISSION
                            installPermission.launch(updater.installSettings())
                        } catch (_: Exception) {
                            action = null
                            message = "앱 설치 권한 설정을 열지 못했어요."
                        }
                    }
                }
            },
        ) {
            Text(when (action) {
                UpdateAction.CHECK -> "업데이트 확인 중…"
                UpdateAction.PERMISSION -> "설치 권한 확인 중…"
                UpdateAction.DOWNLOAD -> "내려받는 중… $percent%"
                UpdateAction.INSTALL -> "설치 화면 여는 중…"
                null -> update?.let { "v${it.version} 설치" } ?: "업데이트 확인"
            })
        }
    }
}
