package org.fedorahosted.freeotp.ui.password

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.ui.theme.FreeOTPTheme

/** Master password setup shown on first run, before any token can be added. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordScreen(
    state: PasswordUiState,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.password_info), style = MaterialTheme.typography.bodyMedium)

            PasswordField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.password),
                error = state.warning,
                busy = state.evaluating,
                imeAction = ImeAction.Next,
                onIme = {},
                modifier = Modifier.testTag("password"),
            )

            if (state.confirmVisible) {
                PasswordField(
                    value = state.confirm,
                    onValueChange = onConfirmChange,
                    label = stringResource(R.string.password_confirm),
                    error = if (state.confirmMismatch) stringResource(R.string.password_match) else null,
                    busy = false,
                    imeAction = ImeAction.Done,
                    onIme = onDone,
                    modifier = Modifier.testTag("confirm"),
                )
            }

            Button(
                onClick = onDone,
                enabled = state.doneEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("done"),
            ) {
                if (state.provisioning) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.done))
                }
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    busy: Boolean,
    imeAction: ImeAction,
    onIme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onNext = { onIme() }, onDone = { onIme() }),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy)
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        painter = painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                        contentDescription = stringResource(if (visible) R.string.password_hide else R.string.password_show),
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview
@Composable
private fun PasswordScreenPreview() {
    FreeOTPTheme {
        PasswordScreen(
            state = PasswordUiState(
                password = "password",
                warning = "This is a top-10 common password.",
                confirmVisible = true,
            ),
            onPasswordChange = {},
            onConfirmChange = {},
            onDone = {},
        )
    }
}
