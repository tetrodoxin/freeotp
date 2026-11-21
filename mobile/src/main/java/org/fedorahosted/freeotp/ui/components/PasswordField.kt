package org.fedorahosted.freeotp.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import org.fedorahosted.freeotp.R

/**
 * Password text field with a show/hide toggle. [busy] adds a small progress indicator next to the
 * toggle; [onIme] runs on the keyboard's [imeAction].
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    imeAction: ImeAction,
    onIme: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
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
        keyboardActions = KeyboardActions(onNext = { onIme() }, onDone = { onIme() }, onGo = { onIme() }),
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
