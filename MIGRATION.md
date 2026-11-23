# Migration to Kotlin and Jetpack Compose

This document tracks the migration of the app from Java + Views (view binding) to Kotlin + Jetpack
Compose. The migration happens in phases. Each phase leaves the app shippable, and CI (instrumented
tests on API 26, 29 and 35) stays green throughout.

## Decisions

| Topic | Decision |
|---|---|
| UI toolkit | Jetpack Compose, Material 3, using the existing brand colors from `colors.xml` (light + night) |
| Language | Kotlin for all new and migrated UI code; Java and Kotlin coexist during the migration |
| Release minification | R8 enabled, guarded by backup-format golden tests and Gson keep rules |
| Navigation | End state is a single activity with Navigation Compose |
| Image loading | Picasso is replaced by Coil |
| Crypto/persistence core | Stays Java until the end (see guardrails); converting it is optional |

## Guardrails

These hold for every phase:

- **Backup compatibility is untouchable.** `Token`, `MasterKey` and `EncryptedKey` are persisted
  with Gson reflection, and the `tokenBackup` prefs are exported as a Java-serialized map. Field
  names, field types and the export format must not change. The golden tests from Phase 0 must
  pass on every change.
- **Gson and Kotlin do not mix safely.** Gson bypasses constructors, so Kotlin non-null types and
  default values are not enforced. Gson-persisted classes are converted only in Phase 6, with
  `@SerializedName` on every field.
- **R8 keep rules** must cover every Gson-persisted class and everything reachable from it.
  `Token` pins its JSON names with `@SerializedName`, but `MasterKey` and `EncryptedKey` are
  stored under their raw Java field names (`mCipher`, `mSalt`, ...), so those names are part of
  the backup format.
- **Security behavior is preserved**: `FLAG_SECURE` on screens that show codes, the
  `UserNotAuthenticatedException` → confirm-device-credential flow, the
  `KeyPermanentlyInvalidatedException` handling, and unsafe-URI confirmation (`parseUnsafe`).
- **The entry points stay stable**: the launcher activity, the `otpauth://totp|hotp` VIEW intent
  filter, `TokenBackupAgent`, and the backup/data-extraction rules.
- `Time.INSTANCE` remains the only time source for code generation and countdowns, so tests can
  control time.
- User-visible strings stay in `res/values/strings.xml` (and the translated locales); Compose uses
  `stringResource`.

## Phases

### Phase 0: Foundation (no visible change)

- [x] Gradle 9.6, AGP 9.4 (built-in Kotlin) and the Compose compiler plugin; Kotlin DSL build
      scripts with a version catalog (`gradle/libs.versions.toml`); JVM target 17; compileSdk 37
      (required by the Compose BOM), targetSdk unchanged at 35
- [x] Add the Compose BOM, `activity-compose`, `lifecycle-viewmodel-compose`, Material 3 and
      `ui-test-junit4`
- [x] Remove legacy dependencies: `com.android.support.constraint`, `com.android.support.test`
      runner and espresso 3.0.2 (unused), `byte-buddy`, Jetifier; androidx test runner instead
- [x] Resource IDs are no longer `final` under AGP 9: `switch` on `R.id` replaced by `if` chains
- [x] `FreeOTPTheme` (Material 3) built from `colors.xml` with dark mode; the XML theme remains
      for the window. No dynamic (wallpaper) color, to keep the brand purple
- [x] Backup-format golden tests (`BackupFormatGoldenTest`, fixtures in
      `androidTest/assets/golden/`): a backup exported by the pre-migration code must still
      deserialize, round-trip byte-for-byte through Gson, decrypt, restore and produce RFC codes.
      Never regenerate the fixtures to make a failing test pass
- [x] Enable R8 and resource shrinking for release (`proguard-rules.pro`, `res/raw/keep.xml`
      for the name-only `fa_*`/`brand_*` resources). `./gradlew connectedAndroidTest
      -PtestMinified` runs the instrumented tests against the minified release build (Compose UI
      tests skipped there); removing the Gson keep rules makes 5 golden tests fail, so the guard
      works. Release APK: 15.0 MB → 4.0 MB. Smoke-tested on a signed release build: onboarding,
      password strength check (nbvcxz), provisioning, adding tokens via intent, code generation
- [x] CI job `minified-backup-tests` runs `BackupFormatGoldenTest` with `-PtestMinified` on API 29
      on every PR

### Phase 1: Standalone screens

- [x] `OnBoardingActivity` + `WelcomeFragment`, `BackupsFragment`, `KeystoreFragment` →
      Compose screens with `HorizontalPager` (`ui/onboarding/`)
- [x] `ManualAdd` → Compose form (`ui/manualadd/`); keeps returning the `otpauth://` URI as the
      activity result, in the same format (pinned by `ManualAddFormTest`). Digits no longer come
      from the localized button text
- [x] `PasswordActivity` → Compose + `PasswordViewModel` (`ui/password/`); the `AsyncTask` nbvcxz
      evaluator becomes a coroutine that is never interrupted, and provisioning (PBKDF2) moved off
      the main thread
- [x] `adjustResize` for the activities with text input, so `imePadding()` and snackbars work
      with the keyboard open
- Kept as before on purpose: a weak password only warns (Done is enabled once confirmed);
  a provisioning failure is logged and the app continues; ManualAdd defaults to HOTP/SHA256
- New strings `navigate_up`, `password_show`, `password_hide` are translated to German only

### Phase 2: Dialogs and sharing

- [x] `EditTokenDialogFragment`, `RestoreDialogFragment`, `RestoreCancelDialogFragment` →
      Compose dialogs (`ui/dialogs/`). `MainDialogsViewModel` holds the open dialog and
      `MainDialogHost` renders it in a `ComposeView` added to `main.Activity`, until the main
      screen is migrated. Open dialogs now survive rotation; before, a recreated restore dialog
      lost its listener and its buttons did nothing. The edit dialog got a Cancel button
- [x] `ShareFragment` → `ModalBottomSheet` (`ui/share/ShareSheet.kt`); `Discoverable`,
      `Clipboard` and `Jelling` stay as non-UI logic. `ShareTarget` replaces the RecyclerView
      adapter items; the sheet renders immutable `ShareTargetUi` snapshots of them. The target
      order no longer treats different titles as equal, and turning on Bluetooth is only asked
      once per sheet instead of on every return to the app
- [x] Replace Picasso with Coil 3 (`coil-network-okhttp`); the View-based token list loads
      through `ui/image/TokenImages`. Token images given as `android.resource://…/drawable/<name>`
      no longer load (Coil only takes the numeric resource ID form) and fall back to the issuer
      icon; http(s) images are unaffected
- The password text field is shared (`ui/components/PasswordField`)
- Unchanged: restoring still runs PBKDF2 on the main thread (moves with `TokenRepository` in
  Phase 3)

### Phase 3: Main screen

- [x] Extract `TokenRepository` from `main/Adapter` (add, delete, move, MRU sort, `getCode`,
      restore, legacy `compat()`), with no UI change; `TokenAdapterTest` keeps passing.
      `data/TokenRepository.kt` is UUID-keyed and has no Android UI dependencies; `Adapter` maps
      positions to UUIDs and keeps the code-expiry `Handler`. Covered by `TokenRepositoryTest`.
      Fixed on the way: a failed legacy migration undid the wrong token (position 0 instead of
      the one just added), and a failing `TokenPersistence` init crashed add/delete/restore
      with an NPE instead of skipping the backup mirror
- [x] `MainViewModel`: token list, selection as a set of UUIDs (not positions), settings
      (auto-copy, sort by MRU) (`ui/main/`, `MainViewModelTest`). It owns the one
      `TokenRepository` and runs every call to it on a single background thread, including
      opening it and restoring (PBKDF2 is off the main thread now). Shown codes and their expiry
      live in the state; side effects (clipboard, device credential, dialogs, scrolling) are
      `MainEvent`s. There must be only one `TokenRepository` per process: the ViewModel's
- Restore fix: `TokenPersistence.needsRestore` compared UUIDs with `==` and so never skipped
  anything on a device; it is removed. A token whose keystore key was lost gets it back on
  restore and keeps its place instead of being listed twice (which would crash a keyed
  `LazyVerticalGrid`)
- [x] Compose token grid: `LazyVerticalGrid` with stable UUID keys and item animations; token card
      with countdown, active/passive transition and selection animation (`ui/main/TokenCard.kt`,
      `MainScreen.kt`)
- [x] `Scaffold`: top bar with selection mode (edit, move up/down, delete) and overflow menu
      (backup, restore, auto-copy, sort, about), expandable FAB hidden on scroll, `SnackbarHost`
      replacing `UserNotifier`
- [x] Confirm-device-credential flow via an `ActivityResultLauncher` keyed by token UUID
- [x] About dialog with `AnnotatedString.fromHtml`; edge-to-edge via `enableEdgeToEdge()` and
      Scaffold insets, replacing `SystemBarInsets`
- [x] Keep the hidden random-token mode (long-press the FAB three times)
- `main.Activity` is Kotlin now (same class name, so the manifest entries, the VIEW intent filter
  and `ScanDialogFragment.addToken` keep working). The remaining `AlertDialog`s (delete, unsafe and
  invalid URI, lock required, add error, key invalidated) are Compose dialogs in `MainDialogHost`.
  `FreeOTPTheme` got neutral surface container colors instead of Material's purple tints
- Behavior changes:
  - In selection mode a tap toggles the selection (as in Material lists) instead of showing the
    code; Back and the arrow in the top bar clear it, and the top bar shows the count
  - The add button hides in selection mode. Without a camera it stays (manual entry only);
    before, the whole button was hidden, so manual entry was unreachable
  - The restore dialog shows progress and is disabled while PBKDF2 runs off the main thread
  - Codes are grouped before bidi-wrapping (the bidi marks broke the grouping in RTL locales),
    and sharing uses the raw code instead of the displayed text
  - Auto-copied codes are marked sensitive (`EXTRA_IS_SENSITIVE`, API 33+), so the clipboard
    preview doesn't show them
  - A token from a VIEW intent is no longer added a second time when the activity is recreated
  - The runtime request for `USE_FINGERPRINT` is gone: it is a normal permission, granted at
    install
- Left for Phase 5: `Adapter`, `ViewHolder`, `SelectableAdapter`, `GridLayoutItemDecoration`,
  `activity_main.xml`, `token.xml`, `menu_main.xml`, `UserNotifier` and `SystemBarInsets` are
  unused by the app now; `TokenAdapterTest`, `TokenCompatTest`, `TokenPersistenceTest` and
  `BackupFormatGoldenTest` still drive storage through `Adapter` and move to `TokenRepository`
  when it is deleted

### Phase 4: QR scanning

- [x] `ScanDialogFragment` → Compose screen; CameraX `PreviewView` in `AndroidView` (or the
      `camera-compose` viewfinder), ZXing analyzer unchanged, permission request via
      `rememberLauncherForActivityResult`. `ui/scan/ScanDialog.kt` is a square dialog
      (`MainDialog.Scan`) with a `PreviewView` (TextureView mode) bound to the activity
      lifecycle; decoding (`ui/scan/QrCodes.kt`) is still ZXing, same hints. Covered by
      `QrCodesTest` (round trip through the decoder) and `ScanDialogTest` (fake camera)
- Fixed on the way: a code could be reported more than once (frames kept being decoded until
  the camera stop, posted to the UI thread, ran), so a token could be added twice; the decoder
  ignored the Y plane's row stride, which skews the image on cameras that pad rows; a frame
  whose decoding threw anything but `NotFoundException` was never closed
- `main.Activity` is a `ComponentActivity` now (no fragments left). `SquareFrameLayout` and
  `fragment_scan.xml` are gone
- Not verified on a real camera: the emulator's camera only delivers black frames (the old
  scanner shows the same), so the live path was checked up to frames reaching the analyzer

### Phase 5: Cleanup and single activity

- [x] Single activity with Navigation Compose (password → onboarding → main → manual add / scan);
      launcher and VIEW intent keep working. `main.Activity` hosts a `NavHost` with string
      routes (no serialization plugin for four routes); scan stays a dialog of the token list.
      The launcher is an `<activity-alias>` named `.PasswordActivity`, so home-screen shortcuts
      to the old launcher component keep working
- [x] Remove layouts, view binding, `SelectableAdapter`, `GridLayoutItemDecoration`,
      `SystemBarInsets` and unused Material Components resources. Also gone: `Adapter`,
      `ViewHolder`, `UserNotifier`, `TokenImages`, the AppCompat, Material Components and
      ConstraintLayout dependencies (the window theme is a platform `Theme.Material` now) and the
      API 35 edge-to-edge opt-out. The storage tests use `TokenRepository` directly
      (`TokenAdapterTest` is `TokenStoreTest`); release APK 4.7 MB → 4.1 MB
- [x] Replace the screenshots in `screenshots/` (used by README.md) with the Material 3 UI; the
      fastlane phone screenshots are the same files
- Behavior changes:
  - Tokens from VIEW intents are added once the token list shows. Before provisioning, the link
    used to bypass the password screen and the token was never backed up; now the password
    comes first and the token is backed up. A link that arrives while manual entry is open
    returns to the list, as the old `singleTask` activity did
  - `FLAG_SECURE` now covers every screen, including onboarding and the password screen

### Phase 6 (optional): Core to Kotlin

- [ ] Low risk: `Code`, `utils/Time`, `TokenIcon`
- [ ] High risk, only behind the golden tests: `Token`, `encryptor/*`, `TokenPersistence`, with
      identical field names and `@SerializedName`

## After the migration

- **minSdk raised from 23 to 26 (Android 8).** On API 23–25, backups made on API 26+ couldn't be
  restored: `MasterKey` prefers PBKDF2-HmacSHA512, which Android only provides from API 26 on, so
  the master key couldn't be derived and restore reported a wrong password. Raising minSdk fixes
  that without touching the crypto core. It also allowed Navigation 2.10 (needs minSdk 24). The
  SHA1 fallback in `MasterKey` stays, so old backups remain readable. CI's oldest emulator moved
  from API 24 to 26

## Status

Phases 0–5 done and merged into `master`. The migration is complete; Phase 6 (core to Kotlin) is
optional.
