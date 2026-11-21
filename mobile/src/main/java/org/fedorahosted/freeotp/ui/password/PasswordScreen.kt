package org.fedorahosted.freeotp.ui.password

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.ui.components.PasswordField
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
                imeAction = ImeAction.Next,
                onIme = {},
                modifier = Modifier.testTag("password"),
                busy = state.evaluating,
            )

            if (state.confirmVisible) {
                PasswordField(
                    value = state.confirm,
                    onValueChange = onConfirmChange,
                    label = stringResource(R.string.password_confirm),
                    error = if (state.confirmMismatch) stringResource(R.string.password_match) else null,
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
