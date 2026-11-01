package com.susil.sonora.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.susil.sonora.BuildConfig
import com.susil.sonora.R

private const val INSTAGRAM_URL = "https://www.instagram.com/imsusil25.exe"
private const val TELEGRAM_URL = "https://t.me/mikey3op"
private const val UPI_SUPPORT_URL =
    "upi://pay?pa=iamsusil@fam&pn=Susil%20Kumar&am=&tn=Thank%20You%20so%20much%20for%20this%20support"

@Composable
private fun RowScope.AboutSocialButton(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .height(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Sonora's About page, following the supplied hero / developer / support layout. */
@Composable
fun AboutScreen(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var contentAnimated by remember { mutableStateOf(false) }
    val contentScale by animateFloatAsState(
        targetValue = if (contentAnimated) 1f else 0.92f,
        animationSpec = tween(durationMillis = 350),
        label = "aboutContentScale",
    )
    LaunchedEffect(Unit) { contentAnimated = true }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(contentScale),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = stringResource(R.string.app_name),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(58.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDividerAbout()
        Spacer(Modifier.height(28.dp))

        Text(
            text = "Lead Developer",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(36.dp))
                .scale(contentScale),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.susil_avatar_512),
                contentDescription = "Susil Kumar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Susil Kumar",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AboutSocialButton(
                    label = "Instagram",
                    iconRes = R.drawable.instagram,
                    onClick = { runCatching { uriHandler.openUri(INSTAGRAM_URL) } },
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(72.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                )
                AboutSocialButton(
                    label = "Telegram",
                    iconRes = R.drawable.telegram,
                    onClick = { runCatching { uriHandler.openUri(TELEGRAM_URL) } },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Support Development",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { runCatching { uriHandler.openUri(UPI_SUPPORT_URL) } },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.upi),
                    contentDescription = "UPI",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 80.dp, height = 36.dp),
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Tap to Support",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "via UPI (India)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            text = "♥ Made with love for music lovers",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HorizontalDividerAbout() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 32.dp),
        thickness = 2.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
    )
}


@Composable
private fun HorizontalDividerAbout() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 32.dp),
        thickness = 2.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
    )
}
