package com.autulio.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GoogleButtonText = Color(0xFF1F1F1F)
private val GoogleButtonBorder = Color(0xFF747775)

@Composable
internal fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        modifier = modifier
            .height(48.dp)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        shape = shape,
        color = Color.White,
        border = BorderStroke(1.dp, GoogleButtonBorder)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = GoogleButtonText,
                    strokeWidth = 2.dp
                )
            } else {
                GoogleGradientMark(Modifier.size(width = 20.dp, height = 20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (loading) "Đang mở Google…" else "Đăng nhập bằng Google",
                color = GoogleButtonText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/**
 * Current gradient Google G mark, drawn as vector paths so it stays sharp
 * at every screen density and is available without a network image request.
 */
@Composable
private fun GoogleGradientMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        scale(size.width / 23.5f, size.height / 24f, pivot = Offset.Zero) {
            val mark = Path().apply {
                moveTo(12f, 10f)
                lineTo(12f, 14.5f)
                lineTo(18.47f, 14.5f)
                cubicTo(17.97f, 17.2f, 15.47f, 19.24f, 12f, 19.24f)
                cubicTo(8.1f, 19.24f, 4.9f, 15.94f, 4.9f, 12f)
                cubicTo(4.9f, 8.05f, 8.1f, 4.75f, 12f, 4.75f)
                cubicTo(13.8f, 4.75f, 15.35f, 5.35f, 16.6f, 6.55f)
                lineTo(20f, 3.15f)
                cubicTo(18f, 1.2f, 15.24f, 0f, 12f, 0f)
                cubicTo(5.4f, 0f, 0f, 5.4f, 0f, 12f)
                cubicTo(0f, 18.6f, 5.4f, 24f, 12f, 24f)
                cubicTo(19f, 24f, 23.5f, 19.1f, 23.5f, 12.3f)
                cubicTo(23.5f, 11.5f, 23.4f, 10.76f, 23.3f, 10f)
                close()
            }
            drawPath(
                path = mark,
                brush = Brush.sweepGradient(
                    0.00f to Color(0xFFFF4641),
                    0.11f to Color(0xFFFD5061),
                    0.24f to Color(0xFF3186FF),
                    0.36f to Color(0xFF00A5B7),
                    0.48f to Color(0xFF0EBC5F),
                    0.62f to Color(0xFF6CC500),
                    0.74f to Color(0xFFFFD314),
                    0.86f to Color(0xFFFCB900),
                    1.00f to Color(0xFFFF4641),
                    center = Offset(12f, 12f)
                )
            )
            drawRect(
                color = Color(0xFF3186FF),
                topLeft = Offset(11f, 8f),
                size = Size(12.5f, 8f)
            )
        }
    }
}
