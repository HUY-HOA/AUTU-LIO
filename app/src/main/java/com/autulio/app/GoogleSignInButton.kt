package com.autulio.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.StrokeCap

private val GoogleButtonText = Color(0xFF1F1F1F)
private val GoogleButtonBorder = Color(0xFF747775)

@Composable
internal fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
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
                GoogleGradientMark(Modifier.size(20.dp))
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

/** Four-color Google G mark, drawn with vector arcs and a blue crossbar. */
@Composable
private fun GoogleGradientMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val side = minOf(size.width, size.height)
        val stroke = side * 0.205f
        val inset = stroke / 2f
        val arcTopLeft = Offset(inset, inset)
        val arcSize = Size(side - stroke, side - stroke)
        val ring = Stroke(width = stroke, cap = StrokeCap.Butt)

        // Google brand palette: red, yellow, green and blue.
        drawArc(Color(0xFFEA4335), -180f, 90.5f, false, arcTopLeft, arcSize, style = ring)
        drawArc(Color(0xFFFBBC05), -90f, 90.5f, false, arcTopLeft, arcSize, style = ring)
        drawArc(Color(0xFF34A853), 0f, 90.5f, false, arcTopLeft, arcSize, style = ring)
        drawArc(Color(0xFF4285F4), 90f, 90.5f, false, arcTopLeft, arcSize, style = ring)

        // The horizontal blue bar creates the recognizable G silhouette.
        drawRect(
            color = Color(0xFF4285F4),
            topLeft = Offset(side * 0.50f, side * 0.3975f),
            size = Size(side * 0.47f, stroke)
        )
    }
}
