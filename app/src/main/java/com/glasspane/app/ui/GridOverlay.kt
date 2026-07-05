package com.glasspane.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Draws a translucent rule-of-thirds grid (2 vertical + 2 horizontal lines)
 * over the preview/overlays. Purely Compose-drawn, like [OverlayLayersCanvas],
 * so it is excluded from recorded video by the same "never touches the
 * capture surface" construction.
 */
@Composable
fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val lineColor = Color.White.copy(alpha = 0.5f)
        val strokeWidth = 1.dp.toPx()

        val thirdWidth = size.width / 3f
        val thirdHeight = size.height / 3f

        for (i in 1..2) {
            drawLine(
                color = lineColor,
                start = Offset(thirdWidth * i, 0f),
                end = Offset(thirdWidth * i, size.height),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = lineColor,
                start = Offset(0f, thirdHeight * i),
                end = Offset(size.width, thirdHeight * i),
                strokeWidth = strokeWidth
            )
        }
    }
}
