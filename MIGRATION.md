# Migration to Kotlin and Jetpack Compose

This document tracks the migration of the app from Java + Views (view binding) to Kotlin + Jetpack
Compose. The migration happens in phases. Each phase leaves the app shippable, and CI (instrumented
tests on API 24, 29 and 35) stays green throughout.

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

- [ ] Add the Kotlin Android plugin (Kotlin 2.x) and the Compose compiler plugin; JVM target 17
      for Java and Kotlin
- [ ] Add the Compose BOM, `activity-compose`, `lifecycle-viewmodel-compose`, Material 3 and
      `ui-test-junit4`
- [ ] Replace legacy dependencies: `com.android.support.constraint`, `com.android.support.test`
      runner and espresso 3.0.2 → androidx equivalents; move `byte-buddy` out of `implementation`
- [ ] `FreeOTPTheme` (Material 3) built from `colors.xml` with dark mode; the XML theme remains
      for the window
- [ ] Backup-format golden tests: fixed serialized `MasterKey`, `EncryptedKey`, `Token` JSON and
      an exported prefs map, asserting they still deserialize, decrypt and produce known codes
- [ ] Enable R8 for release (separate step) with Gson keep rules; verify the golden tests and a
      manual backup → restore round trip on a release build

### Phase 1: Standalone screens

- [ ] `OnBoardingActivity` + `WelcomeFragment`, `BackupsFragment`, `KeystoreFragment` →
      Compose screens with `HorizontalPager`
- [ ] `ManualAdd` → Compose form; keeps returning the `otpauth://` URI as the activity result
- [ ] `PasswordActivity` → Compose + ViewModel; the `AsyncTask` nbvcxz evaluator becomes a
      coroutine

### Phase 2: Dialogs and sharing

- [ ] `EditTokenDialogFragment`, `RestoreDialogFragment`, `RestoreCancelDialogFragment` →
      Compose dialogs (hosted in `ComposeView` until the main screen is migrated)
- [ ] `ShareFragment` → `ModalBottomSheet`; `Discoverable`, `Clipboard` and `Jelling` stay as
      non-UI logic
- [ ] Replace Picasso with Coil

### Phase 3: Main screen

- [ ] Extract `TokenRepository` from `main/Adapter` (add, delete, move, MRU sort, `getCode`,
      restore, legacy `compat()`), with no UI change; `TokenAdapterTest` keeps passing
- [ ] `MainViewModel`: token list, selection as a set of UUIDs (not positions), settings
      (auto-copy, sort by MRU)
- [ ] Compose token grid: `LazyVerticalGrid` with stable UUID keys and item animations; token card
      with countdown, active/passive transition and selection animation
- [ ] `Scaffold`: top bar with selection mode (edit, move up/down, delete) and overflow menu
      (backup, restore, auto-copy, sort, about), expandable FAB hidden on scroll, `SnackbarHost`
      replacing `UserNotifier`
- [ ] Confirm-device-credential flow via an `ActivityResultLauncher` keyed by token UUID
- [ ] About dialog with `AnnotatedString.fromHtml`; edge-to-edge via `enableEdgeToEdge()` and
      Scaffold insets, replacing `SystemBarInsets`
- [ ] Keep the hidden random-token mode (long-press the FAB three times)

### Phase 4: QR scanning

- [ ] `ScanDialogFragment` → Compose screen; CameraX `PreviewView` in `AndroidView` (or the
      `camera-compose` viewfinder), ZXing analyzer unchanged, permission request via
      `rememberLauncherForActivityResult`

### Phase 5: Cleanup and single activity

- [ ] Single activity with Navigation Compose (password → onboarding → main → manual add / scan);
      launcher and VIEW intent keep working
- [ ] Remove layouts, view binding, `SelectableAdapter`, `GridLayoutItemDecoration`,
      `SquareFrameLayout`, `SystemBarInsets`, Picasso and unused Material Components resources

### Phase 6 (optional): Core to Kotlin

- [ ] Low risk: `Code`, `utils/Time`, `TokenIcon`
- [ ] High risk, only behind the golden tests: `Token`, `encryptor/*`, `TokenPersistence`, with
      identical field names and `@SerializedName`

## Status

Not started.
