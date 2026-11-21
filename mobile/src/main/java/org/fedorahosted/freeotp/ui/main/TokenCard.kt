package org.fedorahosted.freeotp.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.BidiFormatter
import coil3.compose.AsyncImage
import org.fedorahosted.freeotp.Code
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.Token
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme

private val CardHeight = 72.dp

/**
 * One token. Passive, it shows issuer and account; active ([code] not null), it shows the code
 * with a countdown and a share button. The icon toggles the selection, which replaces it with a
 * check mark.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TokenCard(
    token: TokenItem,
    code: Code?,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .height(CardHeight)
            .semantics { this.selected = selected }
            .testTag("token_${token.id}")
            .combinedClickable(onClick = onClick, onLongClick = onToggleSelection),
    ) {
        Row(Modifier.fillMaxSize()) {
            TokenIcon(token, selected, dimmed = code != null, onClick = onToggleSelection)
            Crossfade(targetState = code, label = "code", modifier = Modifier.weight(1f)) { shown ->
                if (shown == null) TokenLabels(token)
                else ActiveCode(shown, onShare = { onShare(shown.code) })
            }
        }
    }
}

@Composable
private fun TokenIcon(token: TokenItem, selected: Boolean, dimmed: Boolean, onClick: () -> Unit) {
    AnimatedContent(
        targetState = selected,
        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith fadeOut() },
        label = "selection",
        modifier = Modifier
            .size(CardHeight)
            .clickable(role = Role.Checkbox, onClick = onClick),
    ) { isSelected ->
        if (isSelected) {
            Image(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorResource(R.color.purple_700))
                    .padding(8.dp)
                    .testTag("token_check"),
            )
        } else {
            val image = Modifier
                .fillMaxSize()
                .background(Color(token.color))
                .padding(8.dp)
                .alpha(if (dimmed) 0.6f else 1f)
            val fallback = painterResource(token.imageRes)
            if (token.imageUrl == null) {
                Image(fallback, contentDescription = null, modifier = image)
            } else {
                AsyncImage(
                    model = token.imageUrl,
                    contentDescription = null,
                    placeholder = fallback,
                    error = fallback,
                    contentScale = ContentScale.Fit,
                    modifier = image,
                )
            }
        }
    }
}

@Composable
private fun TokenLabels(token: TokenItem) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                token.issuer ?: stringResource(R.string.unknown_issuer),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            token.label?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
        if (token.locked) {
            Icon(
                painterResource(R.drawable.ic_lock),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(16.dp),
            )
        }
    }
}

@Composable
private fun ActiveCode(code: Code, onShare: () -> Unit) {
    val progress = remember(code) { Animatable(code.timeLeft().toFloat() / code.timeValid()) }
    LaunchedEffect(code) {
        progress.animateTo(0f, tween(code.timeLeft().toInt(), easing = LinearEasing))
    }
    val text = remember(code) { BidiFormatter.getInstance().unicodeWrap(formatCode(code.code)) }

    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            BasicText(
                text,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 56.sp),
                maxLines = 2,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("token_code"),
            )
            LinearProgressIndicator(
                progress = { progress.value },
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
        val share = stringResource(R.string.share_code)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onShare)
                .semantics { contentDescription = share }
                .testTag("token_share"),
        ) {
            Icon(
                painterResource(R.drawable.ic_share),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

private val previewToken = TokenItem(
    id = "1", issuer = "Example", label = "jdoe@example.com", type = Token.Type.TOTP,
    locked = true, imageUrl = null, imageRes = R.drawable.ic_totp, color = 0xFF795548.toInt(),
)

@Preview
@Composable
private fun TokenCardPreview() {
    FreeOTPTheme {
        Column(Modifier.padding(8.dp)) {
            TokenCard(previewToken, null, false, {}, {}, {})
            TokenCard(previewToken, Code("123456", 30), false, {}, {}, {}, Modifier.padding(top = 8.dp))
            TokenCard(previewToken, null, true, {}, {}, {}, Modifier.padding(top = 8.dp))
        }
    }
}
