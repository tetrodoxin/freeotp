package org.fedorahosted.freeotp.ui.main

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.fedorahosted.freeotp.Code
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.Token
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme

/** What the main screen's controls do; the activity implements it. */
interface MainActions {
    fun onActivate(id: String) {}
    fun onToggleSelection(id: String) {}
    fun onClearSelection() {}
    fun onMoveUp() {}
    fun onMoveDown() {}
    fun onEdit() {}
    fun onDelete() {}
    fun onShare(code: String) {}
    fun onBackup() {}
    fun onRestore() {}
    fun onToggleAutoCopy() {}
    fun onToggleSortByMru() {}
    fun onAbout() {}
    fun onScan() {}
    fun onManualAdd() {}
    fun onAddRandom() {}
}

/** How long the hidden random-token mode stays armed, and the window to arm it in. */
private const val RANDOM_MODE_MS = 15_000L

private val Margin = 16.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    state: MainUiState,
    actions: MainActions,
    hasCamera: Boolean,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    gridState: LazyGridState = rememberLazyGridState(),
) {
    var fabVisible by rememberSaveable { mutableStateOf(true) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = state.selecting) { actions.onClearSelection() }
    BackHandler(enabled = fabExpanded && !state.selecting) { fabExpanded = false }

    // Hide the add button while scrolling down, so it doesn't cover the last token.
    val hideOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -1) {
                    fabVisible = false
                    fabExpanded = false
                } else if (available.y > 1) {
                    fabVisible = true
                }
                return Offset.Zero
            }
        }
    }

    Scaffold(
        topBar = { MainTopBar(state, actions) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            AddButton(
                visible = fabVisible && !state.selecting && !state.loading,
                expanded = fabExpanded,
                hasCamera = hasCamera,
                onExpandedChange = { fabExpanded = it },
                actions = actions,
            )
        },
        modifier = Modifier.nestedScroll(hideOnScroll),
    ) { padding ->
        when {
            state.loading -> {}
            state.tokens.isEmpty() -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(Margin),
            ) {
                Text(stringResource(R.string.main_welcome), fontSize = 24.sp, textAlign = TextAlign.Center)
            }
            else -> TokenGrid(state, actions, gridState, padding)
        }
    }
}

@Composable
private fun TokenGrid(
    state: MainUiState,
    actions: MainActions,
    gridState: LazyGridState,
    padding: PaddingValues,
) {
    val direction = LocalLayoutDirection.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(integerResource(R.integer.columns)),
        state = gridState,
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(direction) + Margin,
            end = padding.calculateEndPadding(direction) + Margin,
            top = padding.calculateTopPadding() + Margin,
            // Room for the add button below the last token.
            bottom = padding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Margin),
        horizontalArrangement = Arrangement.spacedBy(Margin),
        modifier = Modifier
            .fillMaxSize()
            .testTag("token_grid"),
    ) {
        items(state.tokens, key = { it.id }) { token ->
            TokenCard(
                token = token,
                code = state.codes[token.id],
                selected = token.id in state.selection,
                // In selection mode a tap changes the selection, as in other Material lists.
                onClick = {
                    if (state.selecting) actions.onToggleSelection(token.id)
                    else actions.onActivate(token.id)
                },
                onToggleSelection = { actions.onToggleSelection(token.id) },
                onShare = actions::onShare,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(state: MainUiState, actions: MainActions) {
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onPrimary,
        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
    )

    if (state.selecting) {
        TopAppBar(
            colors = colors,
            title = { Text(state.selection.size.toString(), Modifier.testTag("selection_count")) },
            navigationIcon = {
                IconButton(onClick = actions::onClearSelection) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.main_clear_selection),
                    )
                }
            },
            actions = {
                BarAction(R.drawable.ic_up, R.string.move_up, state.canMoveUp, actions::onMoveUp)
                BarAction(R.drawable.ic_down, R.string.move_down, state.canMoveDown, actions::onMoveDown)
                BarAction(R.drawable.ic_edit, R.string.edit_token, true, actions::onEdit)
                BarAction(R.drawable.ic_delete, R.string.delete, true, actions::onDelete)
            },
        )
    } else {
        TopAppBar(
            colors = colors,
            title = { Text(stringResource(R.string.app_name)) },
            actions = { OverflowMenu(state, actions) },
        )
    }
}

@Composable
private fun BarAction(@DrawableRes icon: Int, @StringRes label: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}

@Composable
private fun OverflowMenu(state: MainUiState, actions: MainActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, Modifier.testTag("menu")) {
            Icon(painterResource(R.drawable.ic_menu), contentDescription = stringResource(R.string.menu_title))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun item(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) =
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
                    onClick = {
                        open = false
                        onClick()
                    },
                )

            @Composable
            fun check(checked: Boolean, @StringRes label: Int, onClick: () -> Unit) =
                item(if (checked) R.drawable.ic_check_box_checked else R.drawable.ic_check_box_blank, label, onClick)

            item(R.drawable.ic_backup, R.string.backup_title, actions::onBackup)
            item(R.drawable.ic_restore, R.string.restore_title, actions::onRestore)
            check(state.autoCopy, R.string.auto_copy_clipboard, actions::onToggleAutoCopy)
            check(state.sortByMru, R.string.sort_by_recent_use, actions::onToggleSortByMru)
            item(R.drawable.ic_about, R.string.about, actions::onAbout)
        }
    }
}

/**
 * The add button. A tap shows the scan and manual-entry buttons. Long-pressing it three times
 * within 15 s arms the hidden random-token mode for 15 s, in which a tap adds a random token.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddButton(
    visible: Boolean,
    expanded: Boolean,
    hasCamera: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: MainActions,
) {
    var longPresses by remember { mutableIntStateOf(0) }
    val randomMode = longPresses >= 3

    LaunchedEffect(longPresses > 0, randomMode) {
        if (longPresses > 0) {
            delay(RANDOM_MODE_MS)
            longPresses = 0
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MiniFab(R.drawable.ic_edit, R.string.main_enter_manually, "fab_manual") {
                        onExpandedChange(false)
                        actions.onManualAdd()
                    }
                    if (hasCamera) {
                        MiniFab(R.drawable.ic_scan, R.string.main_scan_qr_code, "fab_scan") {
                            onExpandedChange(false)
                            actions.onScan()
                        }
                    }
                }
            }

            val label = stringResource(R.string.manual_add_token)
            Surface(
                shape = FloatingActionButtonDefaults.shape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(56.dp)
                    .semantics {
                        contentDescription = label
                        role = Role.Button
                    }
                    .testTag("fab")
                    .combinedClickable(
                        onClick = {
                            if (randomMode) actions.onAddRandom()
                            else onExpandedChange(!expanded)
                        },
                        onLongClick = { longPresses++ },
                    ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (randomMode) R.drawable.ic_key else R.drawable.ic_add),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniFab(@DrawableRes icon: Int, @StringRes label: Int, tag: String, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag(tag),
    ) {
        Icon(painterResource(icon), contentDescription = stringResource(label), Modifier.size(16.dp))
    }
}

@Preview
@Composable
private fun MainScreenPreview() {
    val tokens = listOf("GitHub", "Google", "Example").mapIndexed { i, issuer ->
        TokenItem("$i", issuer, "alice", Token.Type.TOTP, i == 2, null, R.drawable.ic_totp, 0xFF795548.toInt())
    }
    FreeOTPTheme {
        MainScreen(
            state = MainUiState(loading = false, tokens = tokens, codes = mapOf("0" to Code("123456", 30))),
            actions = object : MainActions {},
            hasCamera = true,
        )
    }
}
