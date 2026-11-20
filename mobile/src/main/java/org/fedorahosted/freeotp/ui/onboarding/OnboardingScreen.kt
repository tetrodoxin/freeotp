package org.fedorahosted.freeotp.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme

private const val PAGE_COUNT = 3

/**
 * First-run introduction: welcome, Android Keystore migration, backup and restore.
 *
 * [onFinished] is called by the "Get started" button on the last page. Back steps to the
 * previous page; on the first page it is left to the activity, which closes onboarding without
 * marking it completed (as before the Compose migration).
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Scaffold(
        bottomBar = {
            PageIndicator(
                pageCount = PAGE_COUNT,
                currentPage = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("onboarding_pager"),
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> KeystorePage()
                else -> BackupsPage(onFinished)
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    @DrawableRes image: Int,
    tintImage: Boolean,
    imageDescription: String?,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(image),
            contentDescription = imageDescription,
            colorFilter = if (tintImage) ColorFilter.tint(MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f),
        )
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun PageTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(0.8f),
    )
}

@Composable
private fun PageBody(text: AnnotatedString) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .padding(top = 8.dp),
    )
}

@Composable
private fun WelcomePage() {
    OnboardingPage(image = R.drawable.ic_freeotp, tintImage = false, imageDescription = null) {
        Text(
            text = stringResource(R.string.app_welcome),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun KeystorePage() {
    OnboardingPage(
        image = R.drawable.ic_key,
        tintImage = true,
        imageDescription = stringResource(R.string.keystore_image),
    ) {
        PageTitle(R.string.android_keystore_title)
        PageBody(AnnotatedString(stringResource(R.string.keystore_migration_explanation)))
    }
}

@Composable
private fun BackupsPage(onFinished: () -> Unit) {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
    OnboardingPage(
        image = R.drawable.ic_restore,
        tintImage = true,
        imageDescription = stringResource(R.string.backup_imageview),
    ) {
        PageTitle(R.string.backup_restore_title)
        PageBody(
            AnnotatedString.fromHtml(
                stringResource(R.string.google_auto_backup_link),
                linkStyles = linkStyles,
            ),
        )
        PageBody(AnnotatedString(stringResource(R.string.manual_backup_explanation)))
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFinished) {
            Text(stringResource(R.string.get_started))
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = 16.dp),
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .semantics { contentDescription = "${index + 1} / $pageCount" }
                    .testTag("onboarding_page_$index"),
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Preview
@Composable
private fun OnboardingScreenPreview() {
    FreeOTPTheme { OnboardingScreen(onFinished = {}) }
}
