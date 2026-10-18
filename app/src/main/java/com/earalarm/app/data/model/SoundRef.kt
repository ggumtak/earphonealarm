package com.earalarm.app.data.model

import android.annotation.SuppressLint
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import com.earalarm.app.R

/**
 * 알람음 참조. DataStore와 Room에는 [encode]한 문자열로 저장한다.
 * - "raw:default_alarm_1" : res/raw 에 넣은 기본 알람음
 * - "uri:content://..."   : 사용자가 기기에서 추가한 음악 (SAF 영구 권한)
 * - "system"              : 시스템 기본 알람음
 */
sealed interface SoundRef {
    fun encode(): String

    data class BuiltIn(val resName: String) : SoundRef {
        override fun encode() = "$PREFIX_RAW$resName"
    }

    data class Custom(val uri: String) : SoundRef {
        override fun encode() = "$PREFIX_URI$uri"
    }

    data object SystemDefault : SoundRef {
        override fun encode() = VALUE_SYSTEM
    }

    companion object {
        private const val PREFIX_RAW = "raw:"
        private const val PREFIX_URI = "uri:"
        private const val VALUE_SYSTEM = "system"

        val DEFAULT: SoundRef = BuiltIn(BuiltInSounds.all.first().resName)

        fun decode(value: String?): SoundRef = when {
            value == null -> DEFAULT
            value.startsWith(PREFIX_RAW) -> BuiltIn(value.removePrefix(PREFIX_RAW))
            value.startsWith(PREFIX_URI) -> Custom(value.removePrefix(PREFIX_URI))
            value == VALUE_SYSTEM -> SystemDefault
            else -> DEFAULT
        }
    }
}

data class BuiltInSound(val resName: String, @StringRes val labelRes: Int)

/** app/src/main/res/raw/ 에 같은 이름의 파일(default_alarm_1.mp3 등)을 넣으면 사용된다. */
object BuiltInSounds {
    val all = listOf(
        BuiltInSound("default_alarm_1", R.string.sound_builtin_1),
        BuiltInSound("default_alarm_2", R.string.sound_builtin_2),
        BuiltInSound("default_alarm_3", R.string.sound_builtin_3),
        BuiltInSound("default_alarm_4", R.string.sound_builtin_4),
    )
}

/**
 * 실제로 재생할 Uri. res/raw 에 해당 파일이 없으면 시스템 기본 알람음으로 대체해서
 * 파일을 넣기 전에도 앱이 정상 동작하도록 한다.
 */
@SuppressLint("DiscouragedApi")
fun SoundRef.resolveUri(context: Context): Uri = when (this) {
    is SoundRef.BuiltIn -> {
        val id = context.resources.getIdentifier(resName, "raw", context.packageName)
        if (id != 0) Uri.parse("android.resource://${context.packageName}/$id") else systemDefaultAlarmUri()
    }
    is SoundRef.Custom -> Uri.parse(uri)
    SoundRef.SystemDefault -> systemDefaultAlarmUri()
}

fun systemDefaultAlarmUri(): Uri =
    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ?: Settings.System.DEFAULT_RINGTONE_URI
