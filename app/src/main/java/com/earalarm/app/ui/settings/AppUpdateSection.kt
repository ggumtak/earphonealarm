package com.earalarm.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earalarm.app.ui.theme.EarAlarmColors
import com.earalarm.app.update.ReleaseChecker
import com.earalarm.app.update.UpdateRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AppUpdateSection(version: String, build: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember(version) { mutableStateOf<UpdateRelease?>(null) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    suspend fun checkForUpdates() {
        checking = true
        try {
            val release = ReleaseChecker.check()
            update = release.takeIf { it.isNewerThan(version) }
            message = if (update == null) "설치된 버전이 최신 공개 버전과 같거나 더 새로워요."
                else "새 버전 v${release.version}이 있어요."
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            message = "업데이트를 확인할 수 없어요. 잠시 뒤 다시 시도해 주세요."
        } finally {
            checking = false
        }
    }

    LaunchedEffect(version) { checkForUpdates() }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("설치된 버전 v$version ($build)", color = EarAlarmColors.Text, fontSize = 15.sp)
        if (message.isNotEmpty()) Text(message, color = EarAlarmColors.TextMuted, fontSize = 13.sp)
        OutlinedButton(
            enabled = !checking,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = EarAlarmColors.TealBright),
            onClick = {
                val release = update
                if (release == null) {
                    scope.launch { checkForUpdates() }
                } else {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)))
                    } catch (_: ActivityNotFoundException) {
                        message = "릴리스 페이지를 열 브라우저를 찾을 수 없어요."
                    }
                }
            },
        ) {
            Text(when {
                checking -> "업데이트 확인 중…"
                update != null -> "브라우저에서 새 버전 보기"
                else -> "업데이트 확인"
            })
        }
    }
}
