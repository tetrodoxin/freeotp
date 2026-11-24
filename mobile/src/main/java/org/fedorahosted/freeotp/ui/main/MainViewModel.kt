package org.fedorahosted.freeotp.ui.main

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fedorahosted.freeotp.Code
import org.fedorahosted.freeotp.Token
import org.fedorahosted.freeotp.TokenIcon
import org.fedorahosted.freeotp.TokenPersistence
import org.fedorahosted.freeotp.data.TokenRepository
import java.security.KeyStoreException
import javax.crypto.SecretKey

/** What the token list shows for one token. */
data class TokenItem(
    val id: String,
    /** Null when the token has no issuer; the UI shows "unknown issuer". */
    val issuer: String?,
    val label: String?,
    val type: Token.Type,
    /** The code needs the device credential (the token's `lock` flag). */
    val locked: Boolean,
    val imageUrl: String?,
    /** Issuer icon, or the HOTP/TOTP icon; also the fallback while [imageUrl] loads. */
    @DrawableRes val imageRes: Int,
    @ColorInt val color: Int,
)

/** How a token looks: its icon and color. Needs resources, so it is injected. */
fun interface TokenAppearance {
    /** Returns [token] as a list item with the given [id]. */
    fun itemOf(id: String, token: Token): TokenItem

    companion object {
        fun from(context: Context) = TokenAppearance { id, token ->
            val icon = TokenIcon(token, context)
            TokenItem(
                id = id,
                issuer = token.issuer,
                label = token.label,
                type = token.type,
                locked = token.lock,
                imageUrl = icon.imageUrl?.takeIf { it.isNotEmpty() },
                imageRes = icon.imageRes,
                color = icon.color,
            )
        }
    }
}

data class MainUiState(
    /** The token store is still being opened (and legacy tokens migrated). */
    val loading: Boolean = true,
    /** The token store couldn't be opened; the list stays empty. */
    val loadFailed: Boolean = false,
    val tokens: List<TokenItem> = emptyList(),
    /** Selected token UUIDs; selection mode is on while this isn't empty. */
    val selection: Set<String> = emptySet(),
    /** Currently shown codes by token UUID; a code is removed when it expires. */
    val codes: Map<String, Code> = emptyMap(),
    val autoCopy: Boolean = false,
    val sortByMru: Boolean = false,
    /** A backup restore (PBKDF2) is running. */
    val restoring: Boolean = false,
) {
    val selecting: Boolean get() = selection.isNotEmpty()

    /** The first selected token in list order; the edit action applies to it. */
    val firstSelected: TokenItem? get() = tokens.firstOrNull { it.id in selection }

    val canMoveUp: Boolean get() = selecting && tokens.firstOrNull()?.id !in selection
    val canMoveDown: Boolean get() = selecting && tokens.lastOrNull()?.id !in selection
}

/** One-shot effects the UI carries out. */
sealed interface MainEvent {
    /** Auto-copy is on: put [code] on the clipboard. */
    data class CopyCode(val code: String) : MainEvent

    /** The token's key needs the device credential; call [MainViewModel.activate] again after it. */
    data class AuthenticationRequired(val token: TokenItem) : MainEvent

    /** The token's key was invalidated (e.g. the screen lock was removed); the token is deleted. */
    data object KeyInvalidated : MainEvent

    /** The URI is valid but fails the safety checks; ask, then [MainViewModel.addToken] unsafe. */
    data class UnsafeUri(val uri: Uri) : MainEvent

    data object InvalidUri : MainEvent

    /** Storing the token's key failed; [lockRequired] if the keystore refused it (as before, the
     *  UI then asks for a screen lock), otherwise a generic error. */
    data class AddFailed(val lockRequired: Boolean) : MainEvent

    data class ScrollTo(val index: Int) : MainEvent

    data object RestoreDone : MainEvent

    data object RestoreBadPassword : MainEvent
}

/**
 * State of the main screen: the token list, the selection (by UUID, so it survives reordering),
 * the shown codes and the settings.
 *
 * All [TokenRepository] calls run one at a time on [storageDispatcher], including opening it
 * (Keystore access and legacy migration) and restoring (PBKDF2).
 */
class MainViewModel(
    openRepository: () -> TokenRepository,
    private val appearance: TokenAppearance,
    private val settings: SharedPreferences,
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : ViewModel() {
    private val _state = MutableStateFlow(
        MainUiState(
            autoCopy = settings.getBoolean(AUTO_COPY_CLIPBOARD, false),
            sortByMru = settings.getBoolean(SORT_BY_MRU, false),
        )
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events: Flow<MainEvent> = _events.receiveAsFlow()

    private val expiry = mutableMapOf<String, Job>()

    private val repository: Deferred<TokenRepository?> = viewModelScope.async(storageDispatcher) {
        try {
            openRepository()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(LOGTAG, "Exception", e)
            null
        }
    }

    init {
        viewModelScope.launch {
            val repo = repository.await()
            if (repo == null) {
                _state.update { it.copy(loading = false, loadFailed = true) }
                return@launch
            }
            withContext(storageDispatcher) { refresh(repo) }
        }
    }

    /** Runs [block] on the storage thread, unless the repository couldn't be opened. */
    private suspend fun <T> storage(block: (TokenRepository) -> T): T? {
        val repo = repository.await() ?: return null
        return withContext(storageDispatcher) { block(repo) }
    }

    /** Reloads the list from [repo]; selection and codes of removed tokens are dropped. */
    private fun refresh(repo: TokenRepository) {
        val tokens = repo.ids.mapNotNull { id ->
            val token = repo.get(id)
            if (token == null) Log.e(LOGTAG, "Unreadable token $id")
            token?.let { appearance.itemOf(id, it) }
        }
        val ids = tokens.mapTo(HashSet()) { it.id }
        _state.update {
            it.copy(
                loading = false,
                tokens = tokens,
                selection = it.selection.filterTo(LinkedHashSet()) { id -> id in ids },
                codes = it.codes.filterKeys { id -> id in ids },
            )
        }
    }

    /** Shows the token's current code (generating the next one for HOTP). */
    fun activate(id: String) {
        viewModelScope.launch {
            val code = try {
                storage { it.getCode(id) } ?: return@launch
            } catch (e: UserNotAuthenticatedException) {
                _state.value.tokens.find { it.id == id }
                    ?.let { _events.send(MainEvent.AuthenticationRequired(it)) }
                return@launch
            } catch (e: KeyPermanentlyInvalidatedException) {
                storage { repo ->
                    try {
                        repo.delete(id)
                    } catch (f: Exception) {
                        Log.e(LOGTAG, "Exception", f)
                    }
                    refresh(repo)
                }
                _events.send(MainEvent.KeyInvalidated)
                return@launch
            }

            _state.update { it.copy(codes = it.codes + (id to code)) }
            scheduleExpiry(id, code)

            val current = _state.value
            if (current.autoCopy) _events.send(MainEvent.CopyCode(code.code))
            if (current.sortByMru) storage { repo -> if (repo.sortByMostRecentlyUsed()) refresh(repo) }
        }
    }

    private fun scheduleExpiry(id: String, code: Code) {
        expiry.remove(id)?.cancel()
        expiry[id] = viewModelScope.launch {
            while (code.isValid) delay(code.timeLeft().coerceAtLeast(MIN_EXPIRY_CHECK_MS))
            _state.update { if (it.codes[id] === code) it.copy(codes = it.codes - id) else it }
            expiry.remove(id)
        }
    }

    fun toggleSelection(id: String) {
        _state.update {
            it.copy(selection = if (id in it.selection) it.selection - id else it.selection + id)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun moveSelectionUp() = moveSelection(-1)

    fun moveSelectionDown() = moveSelection(1)

    /** Moves every selected token one place; does nothing if one is already at the edge. */
    private fun moveSelection(step: Int) {
        val selection = _state.value.selection
        viewModelScope.launch {
            val first = storage { repo ->
                var positions = selection.map { repo.indexOf(it) }.filter { it >= 0 }.sorted()
                if (step > 0) positions = positions.reversed()
                if (positions.isEmpty()) return@storage null
                val edge = if (step < 0) 0 else repo.size - 1
                if (positions.first() == edge) return@storage null

                positions.forEach { repo.move(it, it + step) }
                refresh(repo)
                positions.minOf { it + step }
            } ?: return@launch
            _events.send(MainEvent.ScrollTo(first))
        }
    }

    fun deleteSelected() {
        val selection = _state.value.selection
        viewModelScope.launch {
            storage { repo ->
                for (id in selection) {
                    try {
                        repo.delete(id)
                    } catch (e: Exception) {
                        Log.e(LOGTAG, "Exception", e)
                    }
                }
                refresh(repo)
            }
        }
    }

    fun setLabel(id: String, account: String, issuer: String) {
        viewModelScope.launch {
            storage { repo ->
                try {
                    repo.setLabel(id, account, issuer)
                } catch (e: Exception) {
                    Log.e(LOGTAG, "Exception", e)
                }
                refresh(repo)
            }
        }
    }

    /**
     * Adds a token from an `otpauth://` URI. With [safe], URIs that fail the safety checks
     * (e.g. a short secret) raise [MainEvent.UnsafeUri] instead of being added.
     */
    fun addToken(uri: Uri, safe: Boolean = true) {
        val pair = try {
            if (safe) Token.parse(uri) else Token.parseUnsafe(uri)
        } catch (e: Token.UnsafeUriException) {
            _events.trySend(MainEvent.UnsafeUri(uri))
            return
        } catch (e: Token.InvalidUriException) {
            _events.trySend(MainEvent.InvalidUri)
            return
        }
        add(pair.first, pair.second)
    }

    /** The hidden random-token mode (long-press the add button three times). */
    fun addRandomToken() {
        val pair = Token.random()
        add(pair.first, pair.second)
    }

    private fun add(key: SecretKey, token: Token) {
        viewModelScope.launch {
            val event = try {
                storage { repo ->
                    val id = repo.add(key, token)
                    refresh(repo)
                    MainEvent.ScrollTo(repo.indexOf(id))
                } ?: return@launch
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(LOGTAG, "Exception", e)
                MainEvent.AddFailed(lockRequired = e.javaClass == KeyStoreException::class.java)
            }
            _events.send(event)
        }
    }

    fun toggleAutoCopy() {
        val enabled = !_state.value.autoCopy
        settings.edit { putBoolean(AUTO_COPY_CLIPBOARD, enabled) }
        _state.update { it.copy(autoCopy = enabled) }
    }

    /** Toggles sorting by most recent use; enabling it sorts right away. */
    fun toggleSortByMru() {
        val enabled = !_state.value.sortByMru
        settings.edit { putBoolean(SORT_BY_MRU, enabled) }
        _state.update { it.copy(sortByMru = enabled) }
        if (enabled) viewModelScope.launch {
            storage { repo -> if (repo.sortByMostRecentlyUsed()) refresh(repo) }
        }
    }

    /** Restores the tokens from the backup prefs; raises [MainEvent.RestoreDone] or a bad password. */
    fun restore(password: String) {
        if (_state.value.restoring) return
        _state.update { it.copy(restoring = true) }
        viewModelScope.launch {
            val event = try {
                storage { repo ->
                    repo.restore(password)
                    refresh(repo)
                }
                MainEvent.RestoreDone
            } catch (e: TokenPersistence.BadPasswordException) {
                MainEvent.RestoreBadPassword
            } finally {
                _state.update { it.copy(restoring = false) }
            }
            _events.send(event)
        }
    }

    companion object {
        private const val LOGTAG = "MainViewModel"

        /** Minimum delay between expiry checks, so a code at the end of its window can't spin. */
        private const val MIN_EXPIRY_CHECK_MS = 500L

        const val SETTINGS = "settings"
        const val AUTO_COPY_CLIPBOARD = "copyClipboard"
        const val SORT_BY_MRU = "sortByMostRecentlyUsed"

        fun factory(context: Context) = viewModelFactory {
            val app = context.applicationContext
            initializer {
                MainViewModel(
                    openRepository = { TokenRepository(app) },
                    appearance = TokenAppearance.from(app),
                    settings = app.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE),
                )
            }
        }
    }
}
