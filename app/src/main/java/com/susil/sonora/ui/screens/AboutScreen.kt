package com.susil.sonora.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.susil.sonora.BuildConfig
import com.susil.sonora.R
import com.susil.sonora.ui.components.lightweightLiquidGlass

private const val INSTAGRAM_URL = "https://www.instagram.com/susil.archive_"
private const val TELEGRAM_URL = "https://t.me/mikey3op"
private const val UPI_SUPPORT_URL =
    "upi://pay?pa=iamsusil@fam&pn=Susil%20Kumar&am=&tn=Thank%20You%20so%20much%20for%20this%20support"

private val AboutShape = RoundedCornerShape(28.dp)
private val AboutSmallShape = RoundedCornerShape(18.dp)

/** Sonora's About page, styled as a quiet brand story rather than a settings form. */
@Composable
fun AboutScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val open: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to colors.background,
                    0.42f to colors.background,
                    1f to colors.surface,
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AboutHero()

            AboutGlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AboutEyebrow("THE PEOPLE BEHIND SONORA")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.susil_avatar_512),
                            contentDescription = "Susil Kumar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(CircleShape),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Susil Kumar",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Lead Developer",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AboutSocialButton(
                            label = "Instagram",
                            iconRes = R.drawable.instagram,
                            onClick = { open(INSTAGRAM_URL) },
                        )
                        AboutSocialButton(
                            label = "Telegram",
                            iconRes = R.drawable.telegram,
                            onClick = { open(TELEGRAM_URL) },
                        )
                    }
                }
            }

            AboutSupportCard(onClick = { open(UPI_SUPPORT_URL) })

            Text(
                text = "Made with love for music lovers",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun AboutHero() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(AboutShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.primary.copy(alpha = 0.92f),
                        colors.tertiary.copy(alpha = 0.72f),
                        colors.secondary.copy(alpha = 0.54f),
                    ),
                ),
            )
            .padding(horizontal = 24.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(60.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = Color.White,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.about_settings_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.82f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.18f),
        ) {
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun AboutGlassCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .lightweightLiquidGlass(
                shape = AboutShape,
                fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .padding(18.dp),
    ) {
        content()
    }
}

@Composable
private fun RowScope.AboutSocialButton(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(AboutSmallShape)
            .background(colors.surface.copy(alpha = 0.72f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
        Spacer(Modifier.width(5.dp))
        Icon(
            imageVector = Icons.Rounded.OpenInNew,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun AboutSupportCard(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .lightweightLiquidGlass(
                shape = AboutShape,
                fallbackColor = colors.surfaceContainerHigh,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = AboutSmallShape,
            color = colors.primaryContainer,
        ) {
            Image(
                painter = painterResource(R.drawable.upi),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(9.dp).size(width = 50.dp, height = 26.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = "Support Sonora",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Help keep the music experience growing via UPI",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ArrowOutward,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun AboutEyebrow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing,
    )
}
