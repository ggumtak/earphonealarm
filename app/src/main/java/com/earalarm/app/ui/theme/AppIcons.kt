package com.earalarm.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** 스크린샷의 얇은 선 아이콘. Icon()의 tint로 색을 입힌다. */
object AppIcons {
    val Hourglass by lazy {
        lineIcon("hourglass", "M6 3h12", "M6 21h12",
            "M7.5 3v3.2c0 1 .5 1.9 1.3 2.5L12 12l3.2 -3.3c.8 -.6 1.3 -1.5 1.3 -2.5V3",
            "M7.5 21v-3.2c0 -1 .5 -1.9 1.3 -2.5L12 12l3.2 3.3c.8 .6 1.3 1.5 1.3 2.5V21",
            filled = listOf("M9 20.2h6v-1.4c0 -.6 -.3 -1.1 -.8 -1.5L12 15.2l-2.2 2.1c-.5 .4 -.8 .9 -.8 1.5z"))
    }
    val Alarm by lazy {
        lineIcon("alarm", "M19.5 13a7.5 7.5 0 1 1 -15 0a7.5 7.5 0 1 1 15 0", "M12 9.2v3.8h3.2",
            "M3 6.5L6 3.5", "M21 6.5L18 3.5", "M6.6 19.2L5 21", "M17.4 19.2L19 21")
    }
    val Plus by lazy { lineIcon("plus", "M12 5v14", "M5 12h14") }
    val Close by lazy { lineIcon("close", "M6 6l12 12", "M18 6L6 18") }
    val Play by lazy { lineIcon("play", "M8 5.2v13.6l10.6 -6.8z") }
    val Pause by lazy { lineIcon("pause", "M8.5 5.5v13", "M15.5 5.5v13") }
    val Sort by lazy { lineIcon("sort", "M7 20V4", "M3.5 7.5L7 4l3.5 3.5", "M17 4v16", "M13.5 16.5L17 20l3.5 -3.5") }
    val Speaker by lazy {
        lineIcon("speaker", "M4 9.5h3.5L12 5.5v13L7.5 14.5H4z", "M15 9.5a3.5 3.5 0 0 1 0 5", "M17.5 7.2a6.8 6.8 0 0 1 0 9.6")
    }
    val SpeakerOff by lazy {
        lineIcon("speaker_off", "M4 9.5h3.5L12 5.5v13L7.5 14.5H4z", "M15.5 9.5l5 5", "M20.5 9.5l-5 5")
    }
    val Headphones by lazy {
        lineIcon("headphones", "M4 15v-3a8 8 0 0 1 16 0v3",
            "M4 14.5h2.5a1 1 0 0 1 1 1V19a1 1 0 0 1 -1 1H5a1 1 0 0 1 -1 -1z",
            "M20 14.5h-2.5a1 1 0 0 0 -1 1V19a1 1 0 0 0 1 1H19a1 1 0 0 0 1 -1z")
    }
    val Back by lazy { lineIcon("back", "M19 12H5", "M11 6l-6 6 6 6") }
    val ChevronRight by lazy { lineIcon("chevron", "M9 6l6 6 -6 6") }
    val Delete by lazy {
        lineIcon("delete", "M4 7h16", "M10 11v6", "M14 11v6",
            "M6 7l1 12.5c.1 .8 .7 1.5 1.5 1.5h7c.8 0 1.4 -.7 1.5 -1.5L18 7", "M9 7V4.5c0 -.3 .2 -.5 .5 -.5h5c.3 0 .5 .2 .5 .5V7")
    }
    val Warning by lazy {
        lineIcon("warning", "M12 9.5v4", "M12 16.8v.2",
            "M10.3 4.2L2.8 17.5c-.7 1.2 .2 2.5 1.6 2.5h15.2c1.4 0 2.3 -1.3 1.6 -2.5L13.7 4.2c-.7 -1.2 -2.7 -1.2 -3.4 0z")
    }
    val Music by lazy {
        lineIcon("music", "M9 18V5.5l11 -2V16", "M9 18a3 3 0 1 1 -6 0a3 3 0 1 1 6 0", "M20 16a3 3 0 1 1 -6 0a3 3 0 1 1 6 0")
    }

    /** 톱니 8개짜리 설정 아이콘. 좌표를 계산해서 그린다. */
    val Settings by lazy {
        ImageVector.Builder("settings", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                gear(this)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
                moveTo(15f, 12f); arcTo(3f, 3f, 0f, true, true, 9f, 12f); arcTo(3f, 3f, 0f, true, true, 15f, 12f); close()
            }
        }.build()
    }

    private fun gear(builder: PathBuilder) {
        val teeth = 8
        val points = buildList {
            for (k in 0 until teeth) {
                val center = Math.toRadians(k * 360.0 / teeth - 90)
                listOf(-17.0 to 7.2, -9.0 to 9.6, 9.0 to 9.6, 17.0 to 7.2).forEach { (offset, radius) ->
                    val angle = center + Math.toRadians(offset)
                    add((12 + radius * cos(angle)).toFloat() to (12 + radius * sin(angle)).toFloat())
                }
            }
        }
        builder.moveTo(points[0].first, points[0].second)
        points.drop(1).forEach { builder.lineTo(it.first, it.second) }
        builder.close()
    }

    private fun lineIcon(name: String, vararg strokes: String, filled: List<String> = emptyList()): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            strokes.forEach {
                addPath(addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
            filled.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()
}
