package com.duddletech.blockpuzzlegame.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duddletech.blockpuzzlegame.ui.theme.BlockPuzzleTheme
import com.duddletech.blockpuzzlegame.ui.theme.TextGold
import com.duddletech.blockpuzzlegame.ui.theme.WarningRed

/**
 * Full-screen warning shown while the game-over grace period counts down.
 *
 * Deliberately non-interactive: it draws only a banner and a large translucent
 * digit, with no `clickable` or `pointerInput` modifier, so every touch falls
 * through to the grid, tray, and hold box underneath. The player needs those
 * to rescue the board — this overlay must never eat a drag.
 *
 * @param secondsRemaining seconds left on the clock (counts down to 1).
 * @param dimmed fades the overlay while a shape is being dragged so the digit
 *        doesn't obscure the drop target.
 */
@Composable
fun GameOverWarningOverlay(
    secondsRemaining: Int,
    dimmed: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    val overlayAlpha by animateFloatAsState(
        targetValue = if (dimmed) 0.15f else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "warningAlpha"
    )

    // Banner breathes so it reads as urgent even against a busy board.
    // Bounded rather than infinite: an infinite animation never lets the Compose
    // test clock go idle, which hangs waitForIdle() and every assertion after it.
    // 12 × 600ms comfortably outlives the countdown.
    val bannerPulse = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        bannerPulse.animateTo(
            targetValue = 0.45f,
            animationSpec = repeatable(
                iterations = 12,
                animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    // Each new digit punches in from oversized
    val punch = remember { Animatable(1.35f) }
    LaunchedEffect(secondsRemaining) {
        punch.snapTo(1.35f)
        punch.animateTo(1f, tween(durationMillis = 350, easing = FastOutSlowInEasing))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("game_over_warning"),
        contentAlignment = Alignment.Center
    ) {
        // Fill most of the screen without overflowing on either axis. Converting
        // dp → sp through density keeps the digit a fixed physical size, so a
        // large accessibility font scale can't blow it off-screen.
        val digitDp = minOf(maxWidth.value * 1.05f, maxHeight.value * 0.6f)
        val digitFontSize = with(density) { digitDp.dp.toSp() }

        val digitStyle = TextStyle(
            fontSize = digitFontSize,
            lineHeight = digitFontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = punch.value
                scaleY = punch.value
                alpha = overlayAlpha
            }
        ) {
            // Translucent fill with a brighter outline on top — the outline keeps
            // the digit legible over both the dark board and bright blocks.
            Text(
                text = "$secondsRemaining",
                style = digitStyle.copy(color = WarningRed.copy(alpha = 0.30f))
            )
            Text(
                text = "$secondsRemaining",
                style = digitStyle.copy(
                    color = TextGold.copy(alpha = 0.55f),
                    drawStyle = Stroke(width = 6f, join = StrokeJoin.Round)
                )
            )
        }

        Text(
            text = WARNING_TEXT,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight * 0.12f)
                .graphicsLayer { alpha = overlayAlpha * bannerPulse.value }
                .background(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 20.dp, vertical = 10.dp),
            style = TextStyle(
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = WarningRed,
                letterSpacing = 1.sp,
                shadow = Shadow(
                    color = Color(0xCC000000),
                    offset = Offset(2f, 3f),
                    blurRadius = 4f
                )
            ),
            textAlign = TextAlign.Center
        )
    }
}

/** Banner copy — also the hook UI tests assert on. */
const val WARNING_TEXT = "Game about to END!"

@Preview(showBackground = true, backgroundColor = 0xFF3E2723)
@Composable
private fun GameOverWarningPreview() {
    BlockPuzzleTheme {
        GameOverWarningOverlay(secondsRemaining = 3, dimmed = false)
    }
}