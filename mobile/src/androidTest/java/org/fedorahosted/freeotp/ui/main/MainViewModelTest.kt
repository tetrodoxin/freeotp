package org.fedorahosted.freeotp.ui.main

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.fedorahosted.freeotp.MemoryContext
import org.fedorahosted.freeotp.TokenPersistence
import org.fedorahosted.freeotp.data.TokenRepository
import org.fedorahosted.freeotp.utils.Time
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/**
 * [MainViewModel] against a real [TokenRepository] (AndroidKeyStore, in-memory prefs). All
 * coroutines, including the storage thread, run on one test dispatcher, so every call has
 * finished when it returns. [Time] follows the dispatcher's virtual clock, so `advanceTimeBy`
 * drives code expiry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MainViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val created = mutableListOf<MainViewModel>()
    private lateinit var context: Context
    private val realTime = Time.INSTANCE

    private fun model() = MainViewModel(
        openRepository = { TokenRepository(context) },
        appearance = { id, token ->
            TokenItem(id, token.issuer, token.label, token.type, token.lock, null, 0, 0)
        },
        settings = context.getSharedPreferences(MainViewModel.SETTINGS, 0),
        storageDispatcher = dispatcher,
    ).also { created += it }

    private val MainViewModel.ids get() = state.value.tokens.map { it.id }

    private fun TestScope.events(model: MainViewModel): MutableList<MainEvent> {
        val events = mutableListOf<MainEvent>()
        backgroundScope.launch(dispatcher) { model.events.collect { events += it } }
        return events
    }

    private fun MainViewModel.add(issuer: String) =
        addToken(Uri.parse("otpauth://hotp/$issuer:alice?secret=$SECRET&counter=0"))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = MemoryContext()
        Time.INSTANCE = object : Time() {
            override fun current() = START + dispatcher.scheduler.currentTime
        }
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.flatMap { it.ids }.toSet().forEach { keyStore.deleteEntry(it) }
        Time.INSTANCE = realTime
        Dispatchers.resetMain()
    }

    @Test
    fun loadsTokensAndSettings() = runTest(dispatcher) {
        context.getSharedPreferences(MainViewModel.SETTINGS, 0).edit()
            .putBoolean(MainViewModel.AUTO_COPY_CLIPBOARD, true).commit()
        val first = model()
        first.add("A")
        first.add("B")

        val model = model()

        assertFalse(model.state.value.loading)
        assertEquals(listOf("A", "B"), model.state.value.tokens.map { it.issuer })
        assertTrue(model.state.value.autoCopy)
        assertFalse(model.state.value.sortByMru)
    }

    @Test
    fun addScrollsToNewTokenAndRejectsUnsafeOrInvalidUris() = runTest(dispatcher) {
        val model = model()
        val events = events(model)
        // 80 bits: below the 128-bit minimum, but the keystore accepts it.
        val unsafe = Uri.parse("otpauth://totp/Short:alice?secret=GEZDGNBVGY3TQOJQ")

        model.add("A")
        model.addToken(unsafe)
        model.addToken(Uri.parse("https://example.com"))

        assertEquals(
            listOf(MainEvent.ScrollTo(0), MainEvent.UnsafeUri(unsafe), MainEvent.InvalidUri),
            events,
        )
        assertEquals(1, model.ids.size)

        model.addToken(unsafe, safe = false)
        assertEquals(listOf("A", "Short"), model.state.value.tokens.map { it.issuer })
    }

    @Test
    fun addReportsKeystoreRefusal() = runTest(dispatcher) {
        val model = model()
        val events = events(model)

        // 40 bits: too short for the keystore to import (older keystores, e.g. API 24, take it).
        model.addToken(Uri.parse("otpauth://totp/Tiny:alice?secret=GEZDGNBV"), safe = false)
        // AGP reports assumption failures as test failures, so return instead of assuming.
        if (events == listOf(MainEvent.ScrollTo(0))) return@runTest

        assertEquals(listOf(MainEvent.AddFailed(lockRequired = true)), events)
        assertTrue(model.ids.isEmpty())
    }

    @Test
    fun activateShowsCodeUntilItExpires() = runTest(dispatcher) {
        val model = model()
        model.add("A")
        val id = model.ids.single()

        model.activate(id)
        assertEquals(RFC4226[0], model.state.value.codes[id]?.code)

        // HOTP codes stay visible for the token period (30 s) after they were generated.
        advanceTimeBy(29_000)
        runCurrent()
        assertTrue(id in model.state.value.codes)

        advanceTimeBy(2_000)
        runCurrent()
        assertNull(model.state.value.codes[id])

        model.activate(id)
        assertEquals(RFC4226[1], model.state.value.codes[id]?.code)
    }

    @Test
    fun autoCopyCopiesActivatedCode() = runTest(dispatcher) {
        val model = model()
        val events = events(model)
        model.add("A")
        val id = model.ids.single()

        model.activate(id)
        model.toggleAutoCopy()
        model.activate(id)

        assertEquals(listOf(MainEvent.CopyCode(RFC4226[1])), events.filterIsInstance<MainEvent.CopyCode>())
        assertTrue(context.getSharedPreferences(MainViewModel.SETTINGS, 0)
            .getBoolean(MainViewModel.AUTO_COPY_CLIPBOARD, false))
    }

    @Test
    fun sortByMruSortsOnEnableAndAfterEachUse() = runTest(dispatcher) {
        val model = model()
        listOf("A", "B", "C").forEach { model.add(it) }
        val (a, b, c) = model.ids

        model.activate(c)
        Thread.sleep(5) // last use is wall-clock time
        model.toggleSortByMru()
        assertEquals(listOf(c, a, b), model.ids)

        Thread.sleep(5)
        model.activate(b)
        assertEquals(listOf(b, c, a), model.ids)
        assertEquals(listOf(b, c, a), model().ids)
    }

    @Test
    fun selectionFollowsTokensWhenMoved() = runTest(dispatcher) {
        val model = model()
        val events = events(model)
        listOf("A", "B", "C", "D").forEach { model.add(it) }
        val (a, b, c, d) = model.ids
        events.clear()

        model.toggleSelection(b)
        model.toggleSelection(d)
        assertTrue(model.state.value.canMoveUp)
        assertFalse(model.state.value.canMoveDown)
        assertEquals(b, model.state.value.firstSelected?.id)

        model.moveSelectionUp()
        assertEquals(listOf(b, a, d, c), model.ids)
        assertEquals(setOf(b, d), model.state.value.selection)
        assertEquals(listOf(MainEvent.ScrollTo(0)), events)

        // b is at the top now: moving up again does nothing.
        model.moveSelectionUp()
        assertEquals(listOf(b, a, d, c), model.ids)

        model.toggleSelection(b)
        model.moveSelectionDown()
        assertEquals(listOf(b, a, c, d), model.ids)
        assertEquals(listOf(b, a, c, d), model().ids)
    }

    @Test
    fun deleteSelectedRemovesTokensAndSelection() = runTest(dispatcher) {
        val model = model()
        listOf("A", "B", "C").forEach { model.add(it) }
        val (a, b, c) = model.ids
        model.activate(b)

        model.toggleSelection(b)
        model.toggleSelection(c)
        model.deleteSelected()

        assertEquals(listOf(a), model.ids)
        assertTrue(model.state.value.selection.isEmpty())
        assertTrue(model.state.value.codes.isEmpty())
        assertFalse(keyStore.containsAlias(b))
    }

    @Test
    fun setLabelUpdatesItem() = runTest(dispatcher) {
        val model = model()
        model.add("A")
        val id = model.ids.single()

        model.setLabel(id, "bob", "Example")

        val item = model().state.value.tokens.single()
        assertEquals("bob", item.label)
        assertEquals("Example", item.issuer)
    }

    @Test
    fun restoreBringsBackTokensOrReportsBadPassword() = runTest(dispatcher) {
        TokenPersistence(context).provision(PASSWORD)
        val original = model()
        listOf("A", "B").forEach { original.add(it) }
        val ids = original.ids

        // Lose the token store and keys, keep the tokenBackup prefs.
        ids.forEach { keyStore.deleteEntry(it) }
        context.getSharedPreferences("tokenStore", 0).edit().clear().commit()

        val model = model()
        val events = events(model)
        assertTrue(model.ids.isEmpty())

        model.restore("wrong")
        assertEquals(listOf(MainEvent.RestoreBadPassword), events)
        assertTrue(model.ids.isEmpty())

        model.restore(PASSWORD)
        assertEquals(MainEvent.RestoreDone, events.last())
        assertFalse(model.state.value.restoring)
        assertEquals(ids.toSet(), model.ids.toSet())

        model.activate(ids[0])
        assertEquals(RFC4226[0], model.state.value.codes[ids[0]]?.code)
    }

    @Test
    fun restoreReimportsLostKeyWithoutDuplicating() = runTest(dispatcher) {
        TokenPersistence(context).provision(PASSWORD)
        val model = model()
        listOf("A", "B").forEach { model.add(it) }
        val ids = model.ids

        keyStore.deleteEntry(ids[0])
        model.restore(PASSWORD)

        assertEquals(ids, model.ids)
        assertTrue(keyStore.containsAlias(ids[0]))
        model.activate(ids[0])
        assertEquals(RFC4226[0], model.state.value.codes[ids[0]]?.code)
    }

    private companion object {
        /** The RFC 4226 test secret "12345678901234567890". */
        const val SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val RFC4226 = listOf("755224", "287082")
        const val PASSWORD = "correct horse battery staple"
        const val START = 1_700_000_000_000L
    }
}
