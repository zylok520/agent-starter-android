package io.livekit.android.nrc.voiceassistant.ui.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.min

/** Resolution-independent portrait; mouth geometry follows received audio only. */
@Composable
fun LocalAvatar(mouthOpening: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "语音助手数字人" }) {
        val portraitScale = min(size.width / 320f, size.height / 350f)
        withTransform({
            translate((size.width - 300f * portraitScale) / 2f, (size.height - 330f * portraitScale) / 2f)
            scale(portraitScale, portraitScale, pivot = Offset.Zero)
        }) {
            val hair = Color(0xFF253549)
            val skin = Color(0xFFF3C8AD)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFBCDDEC), Color(0x227DC4E4)), Offset(150f, 160f), 150f), 148f, Offset(150f, 160f))
            drawOval(hair, Offset(64f, 29f), Size(172f, 237f))
            val shoulders = Path().apply {
                moveTo(30f, 320f); cubicTo(32f, 246f, 83f, 238f, 119f, 232f)
                lineTo(181f, 232f); cubicTo(217f, 238f, 268f, 246f, 270f, 320f); close()
            }
            drawPath(shoulders, Brush.verticalGradient(listOf(Color(0xFF5B93AF), Color(0xFF25485E)), 232f, 320f))
            drawRoundRect(skin, Offset(125f, 208f), Size(50f, 57f), CornerRadius(20f))
            val collar = Path().apply {
                moveTo(111f, 236f); lineTo(150f, 270f); lineTo(130f, 289f); lineTo(96f, 245f); close()
                moveTo(189f, 236f); lineTo(150f, 270f); lineTo(170f, 289f); lineTo(204f, 245f); close()
            }
            drawPath(collar, Color(0xFFEDF6FA))
            drawOval(skin, Offset(67f, 125f), Size(25f, 45f))
            drawOval(skin, Offset(208f, 125f), Size(25f, 45f))
            drawOval(Brush.verticalGradient(listOf(Color(0xFFFFDDC7), skin), 65f, 226f), Offset(79f, 55f), Size(142f, 177f))
            val fringe = Path().apply {
                moveTo(73f, 130f); cubicTo(56f, 34f, 127f, 14f, 178f, 33f)
                cubicTo(228f, 31f, 244f, 78f, 222f, 137f)
                lineTo(208f, 104f); cubicTo(182f, 101f, 159f, 74f, 153f, 62f)
                cubicTo(142f, 90f, 107f, 107f, 85f, 111f); close()
            }
            drawPath(fringe, hair)
            drawLine(hair, Offset(102f, 125f), Offset(128f, 121f), 4f, StrokeCap.Round)
            drawLine(hair, Offset(172f, 121f), Offset(198f, 125f), 4f, StrokeCap.Round)
            for (x in listOf(116f, 184f)) {
                drawOval(Color.White, Offset(x - 15f, 137f), Size(30f, 14f))
                drawCircle(Color(0xFF466977), 7f, Offset(x, 144f))
                drawCircle(hair, 4f, Offset(x, 144f))
                drawCircle(Color.White, 2f, Offset(x + 2f, 141f))
            }
            drawOval(Color(0x40E68A86), Offset(91f, 162f), Size(28f, 13f))
            drawOval(Color(0x40E68A86), Offset(181f, 162f), Size(28f, 13f))
            drawLine(Color(0xFFC98F79), Offset(150f, 151f), Offset(146f, 170f), 2f, StrokeCap.Round)
            drawLine(Color(0xFFC98F79), Offset(146f, 170f), Offset(153f, 172f), 2f, StrokeCap.Round)
            val opening = mouthOpening.coerceIn(0f, 1f)
            if (opening < 0.02f) {
                drawArc(Color(0xFF995B5E), 10f, 160f, false, Offset(133f, 180f), Size(34f, 13f), style = Stroke(3f, cap = StrokeCap.Round))
            } else {
                val width = 30f + opening * 10f
                val height = 4f + opening * 28f
                val origin = Offset(150f - width / 2f, 190f - height / 2f)
                drawOval(Color(0xFF512B3C), origin, Size(width, height))
                drawOval(Color(0xFFE99AA2), Offset(150f - width * 0.28f, origin.y + height * 0.65f), Size(width * 0.56f, height * 0.28f))
                drawOval(Color(0xFFAF6B73), origin, Size(width, height), style = Stroke(2f))
            }
        }
    }
}
