package org.fedorahosted.freeotp.ui.manualadd

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme

private val FormSaver = Saver<ManualAddForm, List<Any>>(
    save = { listOf(it.account, it.issuer, it.secret, it.type.name, it.digits, it.algorithm, it.period) },
    restore = {
        ManualAddForm(
            account = it[0] as String,
            issuer = it[1] as String,
            secret = it[2] as String,
            type = OtpType.valueOf(it[3] as String),
            digits = it[4] as Int,
            algorithm = it[5] as String,
            period = it[6] as Int,
        )
    },
)

/**
 * Manual token entry. [onAdd] receives the `otpauth://` URI of valid input; validation messages
 * are shown through [snackbar].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAddScreen(
    onAdd: (Uri) -> Unit,
    onNavigateUp: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var form by rememberSaveable(stateSaver = FormSaver) { mutableStateOf(ManualAddForm()) }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manual_add_token)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_up))
                    }
                },
            )
        },
        /* Scaffold only lifts the snackbar above the navigation bar; also keep it above the keyboard. */
        snackbarHost = {
            SnackbarHost(
                snackbar,
                Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = form.account,
                onValueChange = { form = form.copy(account = it) },
                placeholder = { Text(stringResource(R.string.manual_account_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account"),
            )
            OutlinedTextField(
                value = form.issuer,
                onValueChange = { form = form.copy(issuer = it) },
                placeholder = { Text(stringResource(R.string.manual_issuer_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("issuer"),
            )
            OutlinedTextField(
                value = form.secret,
                onValueChange = { form = form.copy(secret = it) },
                label = { Text(stringResource(R.string.manual_secret_text)) },
                placeholder = { Text(stringResource(R.string.manual_secret_base32_text)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("secret"),
            )

            Section(R.string.manual_type_text) {
                Choice(
                    options = OtpType.entries,
                    selected = form.type,
                    label = { stringResource(if (it == OtpType.TOTP) R.string.manual_button_totp_text else R.string.manual_button_hotp_text) },
                    onSelect = { form = form.copy(type = it) },
                )
            }
            Section(R.string.manual_digits_text) {
                Choice(
                    options = listOf(6, 8),
                    selected = form.digits,
                    label = { stringResource(if (it == 6) R.string.manual_button_digits_six else R.string.manual_button_digits_eight) },
                    onSelect = { form = form.copy(digits = it) },
                )
            }
            Dropdown(
                label = R.string.manual_algorithm,
                description = R.string.manual_accessibility_algorithm,
                options = stringArrayResource(R.array.algorithms_array).toList(),
                selected = form.algorithm,
                onSelect = { form = form.copy(algorithm = it) },
                tag = "algorithm",
            )
            Dropdown(
                label = R.string.manual_interval,
                description = R.string.manual_accessibility_interval,
                options = stringArrayResource(R.array.intervals_array).toList(),
                selected = form.period.toString(),
                onSelect = { form = form.copy(period = it.toInt()) },
                tag = "interval",
            )

            Button(
                onClick = {
                    val error = form.validate()
                    if (error == null) {
                        onAdd(form.toUri())
                    } else {
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            snackbar.showSnackbar(resources.getString(error))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add"),
            ) {
                Text(stringResource(R.string.manual_add_token))
            }
        }
    }
}

@Composable
private fun Section(@StringRes title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label(option))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(
    @StringRes label: Int,
    @StringRes description: Int,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    tag: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val accessibilityLabel = stringResource(description)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .semantics { contentDescription = accessibilityLabel }
                .testTag(tag),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    modifier = Modifier.testTag("$tag:$option"),
                )
            }
        }
    }
}

@Preview
@Composable
private fun ManualAddScreenPreview() {
    FreeOTPTheme { ManualAddScreen(onAdd = {}, onNavigateUp = {}) }
}
