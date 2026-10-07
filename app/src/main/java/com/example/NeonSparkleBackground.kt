package com.example

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Neon Pixel Sparkle Loop Background
 *
 * Direct Kotlin/Compose Canvas port of the provided HTML5 Canvas JS code:
 * - Smooth sinusoidal twinkle with soft neon radial aura glow
 * - HSL spectral color palette (hue 180° to 310°: Cyan, Azure, Purple, Magenta)
 * - Very slow upward particle floating with gentle sinusoidal drift
 * - Tiny cross sparkles on brighter particles (pulse > 0.78)
 * - Seamless recycling and horizontal wrap
 */
private class PixelParticle(
    var x: Float,
    var y: Float,
    var size: Float,
    var speed: Float,
    var drift: Float,
    var phase: Float,
    var twinkle: Float,
    var alpha: Float,
    var hue: Float
)

private fun hslToColor(hue: Float, saturation: Float, lightness: Float, alpha: Float = 1f): Color {
    val h = (hue % 360f + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
        alpha = alpha.coerceIn(0f, 1f)
    )
}

@Composable
fun LoopingNeonSparkleBackground(
    modifier: Modifier = Modifier
) {
    var lastTimeMs by remember { mutableLongStateOf(0L) }
    var clockTimeMs by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var particles by remember { mutableStateOf<List<PixelParticle>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // Initialize particles matching JS count formula: Math.floor((W * H) / 9000)
    LaunchedEffect(canvasSize) {
        val w = canvasSize.width
        val h = canvasSize.height
        if (w > 0f && h > 0f) {
            val count = ((w * h) / 9000f).toInt().coerceIn(24, 65)
            particles = List(count) {
                PixelParticle(
                    x = Random.nextFloat() * w,
                    y = Random.nextFloat() * h,
                    size = 1.2f + Random.nextFloat() * 2.6f, // random(1.2, 3.8)
                    speed = 0.12f + Random.nextFloat() * 0.30f, // random(0.12, 0.42)
                    drift = -0.12f + Random.nextFloat() * 0.24f, // random(-0.12, 0.12)
                    phase = Random.nextFloat() * (2f * PI.toFloat()), // random(0, Math.PI * 2)
                    twinkle = 0.8f + Random.nextFloat() * 1.0f, // random(0.8, 1.8)
                    alpha = 0.25f + Random.nextFloat() * 0.65f, // random(0.25, 0.9)
                    hue = 180f + Random.nextFloat() * 130f // random(180, 310)
                )
            }
        }
    }

    // Animation loop update matching JS animate(time)
    LaunchedEffect(Unit) {
        lastTimeMs = SystemClock.uptimeMillis()
        while (true) {
            withFrameMillis {
                val now = SystemClock.uptimeMillis()
                val delta = if (lastTimeMs == 0L) 16f else (now - lastTimeMs).coerceAtMost(32L).toFloat()
                lastTimeMs = now
                clockTimeMs = now

                val w = canvasSize.width
                val h = canvasSize.height
                if (w > 0f && h > 0f && particles.isNotEmpty()) {
                    particles.forEach { p ->
                        // Very slow upward movement: p.y -= p.speed * delta * 0.06
                        p.y -= p.speed * delta * 0.06f

                        // Gentle horizontal floating: p.x += Math.sin(time*0.00035 + p.phase) * p.drift * delta * 0.08
                        p.x += sin(clockTimeMs * 0.00035f + p.phase) * p.drift * delta * 0.08f

                        // Seamless recycling when particle leaves top
                        if (p.y < -15f) {
                            p.y = h + (5f + Random.nextFloat() * 75f)
                            p.x = Random.nextFloat() * w
                            p.phase = Random.nextFloat() * (2f * PI.toFloat())
                        }

                        // Horizontal wrap
                        if (p.x < -20f) p.x = w + 20f
                        if (p.x > w + 20f) p.x = -20f
                    }
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (size != canvasSize) {
            canvasSize = size
        }

        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Subtle purple atmospheric glow from JS reference at bottom center (W*0.5, H*1.05, radius H*0.8)
        val glowRadius = h * 0.8f
        val glowCenter = Offset(w * 0.5f, h * 1.05f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xA0, 0x2D, 0xFF, (255 * 0.18f).toInt()),
                    Color(0x5A, 0x1E, 0xDC, (255 * 0.08f).toInt()),
                    Color.Transparent
                ),
                center = glowCenter,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = glowCenter
        )

        // Render particles matching JS drawPixel(p, time)
        particles.forEach { p ->
            drawPixelSparkle(p, clockTimeMs)
        }
    }
}

private fun DrawScope.drawPixelSparkle(p: PixelParticle, timeMs: Long) {
    // Smooth sinusoidal twinkle: 0.5 + sin(time * 0.001 * twinkle + phase) * 0.5
    val pulse = 0.5f + sin(timeMs * 0.001f * p.twinkle + p.phase) * 0.5f
    val alpha = p.alpha * (0.45f + pulse * 0.55f)

    if (alpha <= 0.01f) return

    val densityVal = density
    val px = p.x * densityVal
    val py = p.y * densityVal
    val baseSize = p.size * densityVal

    // Soft neon aura
    val auraAlpha = alpha * 0.35f
    val glowSize = baseSize * 5f

    if (auraAlpha > 0.005f) {
        val auraBrush = Brush.radialGradient(
            colors = listOf(
                hslToColor(p.hue, 1f, 0.75f, auraAlpha * 0.95f),
                hslToColor(p.hue, 1f, 0.65f, auraAlpha * 0.35f),
                Color.Transparent
            ),
            center = Offset(px, py),
            radius = glowSize
        )
        drawRect(
            brush = auraBrush,
            topLeft = Offset(px - glowSize, py - glowSize),
            size = Size(glowSize * 2f, glowSize * 2f)
        )
    }

    // Pixel core
    val lightness = 0.78f + pulse * 0.20f
    val coreColor = hslToColor(p.hue, 1f, lightness, alpha)
    val s = baseSize * (0.75f + pulse * 0.35f)
    val coreSizeInt = ceil(s).coerceAtLeast(1f)

    drawRect(
        color = coreColor,
        topLeft = Offset(floor(px - s / 2f), floor(py - s / 2f)),
        size = Size(coreSizeInt, coreSizeInt)
    )

    // Tiny cross sparkle on brighter pixels (pulse > 0.78 && size > 2)
    if (pulse > 0.78f && p.size > 2f) {
        val crossAlpha = alpha * 0.55f
        val crossColor = hslToColor(p.hue, 1f, lightness, crossAlpha)

        val armLength = ceil(s * 3.4f)
        val barThick = 1f * densityVal

        // Horizontal bar
        drawRect(
            color = crossColor,
            topLeft = Offset(floor(px - s * 1.7f), floor(py - barThick / 2f)),
            size = Size(armLength, barThick)
        )

        // Vertical bar
        drawRect(
            color = crossColor,
            topLeft = Offset(floor(px - barThick / 2f), floor(py - s * 1.7f)),
            size = Size(barThick, armLength)
        )
    }
}
