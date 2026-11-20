package org.fedorahosted.freeotp.ui.password

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.gosimple.nbvcxz.Nbvcxz
import me.gosimple.nbvcxz.resources.ConfigurationBuilder
import java.util.Locale

/** Result of a password strength estimation. [warning] may be null even when not [safe]. */
data class Strength(val safe: Boolean, val warning: String?)

fun interface PasswordEvaluator {
    suspend fun evaluate(password: String): Strength
}

/**
 * nbvcxz strength estimation on a background thread.
 *
 * Cancelling the calling coroutine never interrupts the worker thread: an interrupt that lands
 * while nbvcxz opens its dictionaries makes the resource stream null and crashes the app. A
 * superseded estimation just runs to completion and its result is dropped.
 */
class NbvcxzEvaluator(private val locale: () -> Locale) : PasswordEvaluator {
    override suspend fun evaluate(password: String): Strength = withContext(Dispatchers.Default) {
        val cfg = ConfigurationBuilder().setLocale(locale()).createConfiguration()
        val result = Nbvcxz(cfg).estimate(password)
        Strength(result.isMinimumEntropyMet, result.feedback.warning)
    }
}

data class PasswordUiState(
    val password: String = "",
    val confirm: String = "",
    /** A strength estimation for [password] is running. */
    val evaluating: Boolean = false,
    /** nbvcxz warning for a weak [password]; null when strong or not yet evaluated. */
    val warning: String? = null,
    /** The confirmation field is shown once [password] has been evaluated. */
    val confirmVisible: Boolean = false,
    val provisioning: Boolean = false,
    val provisioned: Boolean = false,
) {
    val confirmMismatch: Boolean get() = confirm.isNotEmpty() && confirm != password

    /* As before the migration, a weak password only warns; matching confirmation enables Done. */
    val doneEnabled: Boolean
        get() = confirm.isNotEmpty() && confirm == password && !provisioning && !provisioned
}

/**
 * State for choosing the master password. [provision] creates and stores the master key; it runs
 * on a background thread because PBKDF2 with 100k iterations takes a noticeable time.
 *
 * The password is deliberately not kept in saved instance state, so it is never written to disk.
 */
class PasswordViewModel(
    private val evaluator: PasswordEvaluator,
    private val provision: (String) -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(PasswordUiState())
    val state: StateFlow<PasswordUiState> = _state.asStateFlow()

    private var evaluation: Job? = null

    fun onPasswordChange(password: String) {
        if (password == _state.value.password)
            return

        evaluation?.cancel()
        _state.value = PasswordUiState(password = password, evaluating = password.isNotEmpty())
        if (password.isEmpty())
            return

        evaluation = viewModelScope.launch {
            val strength = evaluator.evaluate(password)
            _state.update {
                if (it.password != password) it
                else it.copy(
                    evaluating = false,
                    confirmVisible = true,
                    warning = if (strength.safe) null else strength.warning,
                )
            }
        }
    }

    fun onConfirmChange(confirm: String) {
        _state.update { it.copy(confirm = confirm) }
    }

    fun onDone() {
        val current = _state.value
        if (!current.doneEnabled)
            return

        _state.update { it.copy(provisioning = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { provision(current.password) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                /* Unchanged from before the migration: log and continue to the token list. */
                Log.e(LOGTAG, "Exception", e)
            }
            _state.update { it.copy(provisioning = false, provisioned = true) }
        }
    }

    private companion object {
        const val LOGTAG = "PasswordViewModel"
    }
}
