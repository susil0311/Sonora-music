package com.susil.sonora.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.susil.sonora.R
import com.susil.sonora.sharedui.resources.Res
import com.susil.sonora.sharedui.resources.ic_google_g
import com.susil.sonora.sharedui.resources.ic_logo
import org.jetbrains.compose.resources.painterResource
import com.susil.sonora.ui.icons.SonoraIcons
import com.susil.sonora.ui.player.MeshGradientBackground
import com.susil.sonora.ui.player.MESH_CYCLE_MILLIS
import com.susil.sonora.ui.player.MeshPalette
import com.susil.sonora.ui.player.WelcomeMeshPalettes
import com.susil.sonora.ui.player.cyclingMeshPalette

private const val INSTAGRAM_URL = "https://www.instagram.com/susil.archive_"
private const val UPI_SUPPORT_URL =
    "upi://pay?pa=iamsusil@fam&pn=Susil%20Kumar&am=&tn=Thank%20You%20so%20much%20for%20this%20support"

/**
 * The first-run pages: a name for Listen Together, a personal welcome, then
 * the optional YouTube sign-in.
 *
 * Shown once — to new installs and, because the flag it sets is new, to anyone
 * updating from a build without it. The sign-in page is skipped for an account
 * that is already signed in; asking somebody to connect what is connected reads
 * as the app having forgotten them.
 *
 * Always drawn dark, whatever the theme, for the same reason Replay is: the
 * mesh is colour, and white type is what sits on colour.
 */
@Composable
fun OnboardingScreen(
    signedIn: Boolean,
    initialName: String,
    /** Saves the party name. Called on Next, before the second page. */
    onNameChosen: (String) -> Unit,
    /** Opens the Google sign-in. The caller finishes onboarding once it lands. */
    onSignIn: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableIntStateOf(PAGE_NAME) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = page != PAGE_NAME) {
        page = if (page == PAGE_CONNECT) PAGE_WELCOME else PAGE_NAME
    }
    // "Sign in with Google" goes through the app's own sign-in WebView, raised
    // over this page; the account landing is what ends the welcome.
    LaunchedEffect(signedIn, page) {
        if (signedIn && page == PAGE_CONNECT) onFinish()
    }

    // The mesh never settles on one palette: each crossfade is as long as the
    // hold, so one runs straight into the next.
    val palette = cyclingMeshPalette(if (page == PAGE_CONNECT) ConnectPalettes else WelcomeMeshPalettes)

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            // Swallows every touch the pages don't, so nothing reaches the app
            // laid out underneath — and a tap on the backdrop puts the keyboard
            // away, which is where a thumb goes to dismiss one.
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
    ) {
        MeshGradientBackground(
            palette = palette,
            trackKey = page,
            continuous = true,
            driftMillis = 6_000,
            colorFadeMillis = MESH_CYCLE_MILLIS,
        )
        // The mesh carries its own light scrim for a player's controls; two
        // full-width buttons and a paragraph want the foot of it a shade deeper.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.05f),
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.45f),
                    ),
                ),
        )

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState > initialState
                val slide = tween<IntOffset>(420)
                (slideInHorizontally(slide) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(320, 100)))
                    .togetherWith(
                        slideOutHorizontally(slide) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)),
                    )
            },
            label = "onboardingPage",
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
        ) { shown ->
            OnboardingPage {
                when (shown) {
                    PAGE_NAME -> NamePage(
                        name = name,
                        onNameChange = { name = it.take(NAME_LIMIT) },
                        onNext = next@{
                            val chosen = name.trim()
                            if (chosen.isEmpty()) return@next
                            focusManager.clearFocus()
                            onNameChosen(chosen)
                            page = PAGE_WELCOME
                        },
                    )
                    PAGE_WELCOME -> WelcomePage(
                        name = name.trim(),
                        onBack = { page = PAGE_NAME },
                        onLater = { if (signedIn) onFinish() else page = PAGE_CONNECT },
                    )
                    else -> ConnectPage(
                        onBack = { page = PAGE_WELCOME },
                        onSignIn = onSignIn,
                        onSkip = onFinish,
                    )
                }
            }
        }
    }
}

/** The column every page is laid out in, capped so a tablet doesn't stretch it. */
@Composable
private fun OnboardingPage(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .padding(horizontal = 28.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.NamePage(
    name: String,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    // With the keyboard up there is room for the title or the logo, not both;
    // the title is the one that says where you are.
    val keyboardUp = WindowInsets.isImeVisible
    AnimatedVisibility(
        visible = !keyboardUp,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            Spacer(Modifier.height(56.dp))
            Icon(
                painter = painterResource(Res.drawable.ic_logo),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(width = 35.dp, height = 30.dp),
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Title(stringResource(R.string.onboarding_title))

    Spacer(Modifier.weight(1f))

    Text(
        text = stringResource(R.string.onboarding_name_prompt),
        style = MaterialTheme.typography.titleMedium,
        fontSize = 17.sp,
        color = Color.White.copy(alpha = 0.78f),
        modifier = Modifier.padding(start = 4.dp),
    )
    Spacer(Modifier.height(12.dp))
    NameField(value = name, onValueChange = onNameChange, onDone = onNext)
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.onboarding_name_footer),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.55f),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    Spacer(Modifier.height(28.dp))
    PillButton(
        label = stringResource(R.string.onboarding_next),
        onClick = onNext,
        enabled = name.isNotBlank(),
        primary = true,
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ColumnScope.WelcomePage(
    name: String,
    onBack: () -> Unit,
    onLater: () -> Unit,
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    Spacer(Modifier.height(12.dp))
    BackButton(onBack)
    Spacer(Modifier.height(8.dp))
    val body = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            StampBanner(minOf(220.dp, maxHeight - 8.dp, maxWidth))
        }
        Spacer(Modifier.height(8.dp))
        RevealText(
            text = stringResource(R.string.onboarding_welcome_title, name),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 26.sp,
                lineHeight = 30.sp,
                letterSpacing = (-0.6).sp,
            ),
            delayMillis = 950,
            color = Color.White,
        )
        Spacer(Modifier.height(10.dp))
        RevealText(stringResource(R.string.onboarding_welcome_intro), body, 1100)
        Spacer(Modifier.height(8.dp))
        RevealText(stringResource(R.string.onboarding_welcome_promise), body, 1220)
        Spacer(Modifier.height(8.dp))
        RevealText(stringResource(R.string.onboarding_welcome_design), body, 1340)
        Spacer(Modifier.height(8.dp))
        RevealText(stringResource(R.string.onboarding_welcome_thanks), body, 1460)
    }

    Spacer(Modifier.height(12.dp))
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(480, delayMillis = 1650)) +
            slideInVertically(tween(480, delayMillis = 1650)) { it / 3 },
    ) {
        PillButton(
            label = stringResource(R.string.onboarding_support_developer),
            onClick = { runCatching { uriHandler.openUri(UPI_SUPPORT_URL) } },
            primary = true,
        )
    }
    Spacer(Modifier.height(12.dp))
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(480, delayMillis = 1740)) +
            slideInVertically(tween(480, delayMillis = 1740)) { it / 3 },
    ) {
        PillButton(
            label = stringResource(R.string.onboarding_follow_instagram),
            onClick = { runCatching { uriHandler.openUri(INSTAGRAM_URL) } },
            primary = false,
        )
    }
    Spacer(Modifier.height(10.dp))
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(480, delayMillis = 1830)) +
            slideInVertically(tween(480, delayMillis = 1830)) { it / 3 },
    ) {
        PillButton(
            label = stringResource(R.string.onboarding_maybe_later),
            onClick = onLater,
            primary = false,
        )
    }
    Spacer(Modifier.height(16.dp))
}

private class Spark(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val kind: Int,
    val spin: Float,
    val color: Color,
)

private val SparkColors = listOf(
    Color.White,
    Color(0xFFFFD54F),
    Color(0xFFFF6E9C),
    Color(0xFF4DD0E1),
    Color(0xFFFF9100),
    Color(0xFFB388FF),
)

/**
 * The welcome seal is a vinyl record punched onto the page. It drops in
 * oversized and tilted, smacks down with a haptic thump, a flash, shockwave
 * rings, a squash-and-stretch and a burst of confetti and music notes, then
 * spins up like a record and settles into a slow drift with a rotating glare.
 * Tap it to punch it again.
 */
@Composable
private fun StampBanner(diameter: Dp) {
    val k = diameter.value / 220f
    val scale = remember { Animatable(3.6f) }
    val alpha = remember { Animatable(0f) }
    val tilt = remember { Animatable(-24f) }
    val ring = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    val squash = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val kick = remember { Animatable(0f) }
    var round by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val notes = remember(measurer) {
        listOf("♪", "♫").map { measurer.measure(it, TextStyle(fontSize = 22.sp)) }
    }
    val sparks = remember {
        val rnd = kotlin.random.Random(11)
        List(36) { i ->
            Spark(
                angle = (i / 36f) * 2f * PI.toFloat() + rnd.nextFloat() * 0.3f,
                speed = 0.9f + rnd.nextFloat() * 1.3f,
                size = 4f + rnd.nextFloat() * 5f,
                kind = i % 3,
                spin = rnd.nextFloat() * 720f - 360f,
                color = SparkColors[i % SparkColors.size],
            )
        }
    }
    val drift by rememberInfiniteTransition(label = "vinyl").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "drift",
    )

    LaunchedEffect(round) {
        scale.snapTo(3.6f); alpha.snapTo(0f); tilt.snapTo(-24f); ring.snapTo(0f)
        shake.snapTo(0f); pop.snapTo(0f); squash.snapTo(0f); burst.snapTo(0f)
        flash.snapTo(0f); kick.snapTo(0f)
        delay(if (round == 0) 300 else 80)
        coroutineScope {
            launch { alpha.animateTo(1f, tween(80)) }
            launch { tilt.animateTo(-8f, tween(140, easing = FastOutLinearInEasing)) }
            scale.animateTo(1f, tween(140, easing = FastOutLinearInEasing))
            // PUNCH
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            launch { ring.animateTo(1f, tween(800, easing = LinearOutSlowInEasing)) }
            launch { burst.animateTo(1f, tween(1300, easing = LinearEasing)) }
            launch {
                flash.snapTo(1f)
                flash.animateTo(0f, tween(280))
            }
            launch {
                shake.snapTo(1f)
                shake.animateTo(0f, tween(460, easing = LinearEasing))
            }
            launch {
                squash.snapTo(1f)
                squash.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 380f))
            }
            launch { tilt.animateTo(-6f, spring(dampingRatio = 0.35f, stiffness = 300f)) }
            launch { kick.animateTo(900f, tween(1800, easing = LinearOutSlowInEasing)) }
            launch {
                delay(130)
                pop.animateTo(1f, spring(dampingRatio = 0.36f, stiffness = 320f))
            }
            launch {
                delay(260)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = sin(shake.value * 38f) * 16f * shake.value
                translationY = cos(shake.value * 31f) * 11f * shake.value
            }
            .drawBehind {
                val c = center
                val base = (diameter / 2).toPx()
                val f = flash.value
                if (f > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.6f * f), Color.Transparent),
                            center = c,
                            radius = base * 3.2f,
                        ),
                        radius = base * 3.2f,
                        center = c,
                    )
                }
                val r = ring.value
                if (r > 0f && r < 1f) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f * (1f - r)),
                        radius = base + this.size.maxDimension * 0.6f * r,
                        center = c,
                        style = Stroke(width = 6.dp.toPx() * (1f - r) + 1f),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.3f * (1f - r)),
                        radius = base + this.size.maxDimension * 0.45f * r,
                        center = c,
                        style = Stroke(width = 3.dp.toPx() * (1f - r) + 1f),
                    )
                    for (i in 0 until 18) {
                        val a = i * 2.0 * PI / 18
                        val dx = cos(a).toFloat()
                        val dy = sin(a).toFloat()
                        val inner = base + 8.dp.toPx() + 46.dp.toPx() * r
                        val outer = inner + 26.dp.toPx() * (1f - r)
                        drawLine(
                            color = Color.White.copy(alpha = 0.8f * (1f - r)),
                            start = Offset(c.x + dx * inner, c.y + dy * inner),
                            end = Offset(c.x + dx * outer, c.y + dy * outer),
                            strokeWidth = 3.dp.toPx(),
                        )
                    }
                }
                val t = burst.value
                if (t > 0f && t < 1f) {
                    val ease = 1f - (1f - t) * (1f - t) * (1f - t)
                    val fade = (1f - t * t).coerceIn(0f, 1f)
                    sparks.forEachIndexed { i, sp ->
                        val dist = base * 0.9f + sp.speed * base * 1.1f * ease
                        val pos = Offset(
                            c.x + cos(sp.angle) * dist,
                            c.y + sin(sp.angle) * dist + 220.dp.toPx() * t * t,
                        )
                        val u = sp.size * density
                        when (sp.kind) {
                            0 -> drawCircle(sp.color.copy(alpha = fade), radius = u * 0.7f, center = pos)
                            1 -> rotate(sp.spin * t, pos) {
                                drawRoundRect(
                                    color = sp.color.copy(alpha = fade),
                                    topLeft = Offset(pos.x - u, pos.y - u * 0.5f),
                                    size = Size(u * 2f, u),
                                    cornerRadius = CornerRadius(u * 0.4f),
                                )
                            }
                            else -> {
                                val layout = notes[i % 2]
                                rotate(sp.spin * t * 0.3f, pos) {
                                    drawText(
                                        textLayoutResult = layout,
                                        color = sp.color,
                                        topLeft = Offset(
                                            pos.x - layout.size.width / 2f,
                                            pos.y - layout.size.height / 2f,
                                        ),
                                        alpha = fade,
                                    )
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(diameter)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { round++ }
                .graphicsLayer {
                    scaleX = scale.value * (1f + 0.14f * squash.value)
                    scaleY = scale.value * (1f - 0.16f * squash.value)
                    rotationZ = tilt.value
                    this.alpha = alpha.value
                }
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
                .drawBehind {
                    val c = center
                    val rad = this.size.minDimension / 2f
                    var g = rad * 0.2f
                    while (g < rad * 0.97f) {
                        drawCircle(Color.White.copy(alpha = 0.06f), radius = g, style = Stroke(1.dp.toPx()))
                        g += 5.dp.toPx()
                    }
                    rotate(kick.value + drift, c) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.08f to Color.White.copy(alpha = 0.26f),
                                    0.16f to Color.Transparent,
                                    0.5f to Color.Transparent,
                                    0.58f to Color.White.copy(alpha = 0.2f),
                                    0.66f to Color.Transparent,
                                    1f to Color.Transparent,
                                ),
                                center = c,
                            ),
                            radius = rad * 0.97f,
                            center = c,
                        )
                    }
                }
                .border(5.dp, Color.White.copy(alpha = 0.95f), CircleShape)
                .padding(11.dp)
                .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "★  ★  ★",
                    fontSize = (11 * k).sp,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height((6 * k).dp))
                Text(
                    text = "WELCOME TO",
                    fontSize = (14 * k).sp,
                    fontWeight = FontWeight.W800,
                    letterSpacing = (4 * k).sp,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SONORA!",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = (31 * k).sp,
                            lineHeight = (35 * k).sp,
                            letterSpacing = (-0.6).sp,
                        ),
                        color = Color.White,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width((6 * k).dp))
                    Text(
                        text = "🎶",
                        fontSize = (26 * k).sp,
                        modifier = Modifier.graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                            this.alpha = pop.value.coerceIn(0f, 1f)
                        },
                    )
                }
            }
        }
    }
}

/** Fades up from below once the stamp has landed; no per-character typing. */
@Composable
private fun RevealText(
    text: String,
    style: TextStyle,
    delayMillis: Int,
    color: Color = Color.White.copy(alpha = 0.80f),
) {
    val progress = remember(text) { Animatable(0f) }
    LaunchedEffect(text) {
        delay(delayMillis.toLong())
        progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    Text(
        text = text,
        style = style,
        color = color,
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 24.dp.toPx()
        },
    )
}

@Composable
private fun ColumnScope.ConnectPage(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    BackButton(onBack)
    Spacer(Modifier.height(20.dp))
    Title(stringResource(R.string.onboarding_connect_title))
    Spacer(Modifier.height(14.dp))
    Text(
        text = stringResource(R.string.onboarding_connect_body),
        style = MaterialTheme.typography.bodyLarge,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        color = Color.White.copy(alpha = 0.72f),
    )

    Spacer(Modifier.weight(1f))

    PillButton(
        label = stringResource(R.string.onboarding_sign_in_google),
        onClick = onSignIn,
        primary = true,
        leading = {
            Image(
                painter = painterResource(Res.drawable.ic_google_g),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
    )
    Spacer(Modifier.height(12.dp))
    PillButton(
        label = stringResource(R.string.onboarding_skip_sign_in),
        onClick = onSkip,
        primary = false,
    )
    Spacer(Modifier.height(16.dp))
}

/** Replay's heading, scaled up: the one line each page exists to say. */
@Composable
private fun Title(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = 40.sp,
            lineHeight = 44.sp,
            letterSpacing = (-1.2).sp,
        ),
        color = Color.White,
        modifier = modifier,
    )
}

/**
 * The name, set in the heading's own face and weight so what you type reads
 * as part of the page rather than a form on top of it.
 */
@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    val style = MaterialTheme.typography.displayLarge.copy(
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.6).sp,
        color = Color.White,
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = stringResource(R.string.onboarding_name_hint),
                style = style,
                color = Color.White.copy(alpha = 0.38f),
                maxLines = 1,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A full-width capsule. Primary is white on the mesh, the way Apple Music's
 * own welcome sheet does it; secondary is the same shape in frosted white, so
 * the pair reads as a choice rather than a button and an afterthought.
 */
@Composable
private fun PillButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 900f), label = "pillScale")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(180), label = "pillAlpha")
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(CircleShape)
            .background(if (primary) Color.White else Color.White.copy(alpha = 0.16f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 17.sp,
            fontWeight = FontWeight.W700,
            color = if (primary) Color.Black else Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = SonoraIcons.ChevronRight,
            contentDescription = stringResource(R.string.onboarding_back),
            tint = Color.White,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { scaleX = -1f },
        )
    }
}

private const val PAGE_NAME = 0
private const val PAGE_WELCOME = 1
private const val PAGE_CONNECT = 2

/** Long enough for a full name; the party server keeps the first 80. */
private const val NAME_LIMIT = 40

/** The account page keeps leaning into YouTube's red while it moves. */
private val ConnectPalettes = listOf(
    MeshPalette(listOf(Color(0xFFFF1E3C), Color(0xFFC2185B), Color(0xFFFF8F3D), Color(0xFF4A1C8C))),
    MeshPalette(listOf(Color(0xFFE53935), Color(0xFFFF6E40), Color(0xFFAD1457), Color(0xFF311B92))),
    MeshPalette(listOf(Color(0xFFFF3D00), Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFFB71C1C))),
    MeshPalette(listOf(Color(0xFFF50057), Color(0xFFFF9100), Color(0xFFC51162), Color(0xFF4527A0))),
)
