# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

FreeOTP is an Android HOTP/TOTP (RFC 4226 / RFC 6238) authenticator. It is a single Gradle module (`:mobile`) written in Java, package `org.fedorahosted.freeotp`, using view binding. It is being migrated to Kotlin + Compose (see below). The build uses Gradle 9 / AGP 9 (built-in Kotlin) with Kotlin DSL scripts and a version catalog in `gradle/libs.versions.toml`.

## Commands

```sh
./gradlew build -x lint                 # what CI runs on every PR (JDK 17)
./gradlew assembleDebug                 # debug APK, applicationId gets a ".debug" suffix
./gradlew installDebug

# All tests are instrumented (mobile/src/androidTest); there is no src/test.
# They need a running emulator/device because they use the AndroidKeyStore.
./gradlew connectedAndroidTest
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.fedorahosted.freeotp.TokenRFC6238Test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.fedorahosted.freeotp.TokenRFC6238Test#methodName
./gradlew connectedAndroidTest -PtestMinified   # same tests against the R8-minified release build
```

CI (`.github/workflows/build.yml`) runs the instrumented tests on emulators at API 24, 29 and 35, so changes must work across that range (minSdk 23).

## Architecture

### Token storage and key handling (the core of the app)

Secrets are never stored in plaintext. Three layers interact, and you need to know all of them before touching tokens, backup or restore:

1. **AndroidKeyStore**: each token's HMAC secret is imported under the token's UUID alias, and code generation uses that non-extractable key. A `masterKey` alias holds an AES/GCM **encrypt-only** key.
2. **`tokenStore` SharedPreferences** (`data/TokenRepository.kt`): token metadata as Gson JSON keyed by UUID, plus a `tokenOrder` UUID list. `TokenRepository` is UUID-keyed: it adds, deletes and reorders tokens, generates codes (`getCode`), restores backups and migrates legacy pre-2.0 tokens from the old `tokens` prefs (`compat()` / `Token.compat`). `main/Adapter` is now only the RecyclerView adapter over it (position → UUID).
3. **`tokenBackup` SharedPreferences** (`TokenPersistence`): for each token, `<uuid>` → JSON `{key: EncryptedKey}` (the secret encrypted under the master key) and `<uuid>-token` → serialized token metadata, plus `masterKey` → a Gson-serialized `MasterKey`.

`encryptor/`:
- `MasterKey`: a random AES key wrapped by a PBKDF2 key derived from the user's password (PBKDF2-HmacSHA512, falling back to SHA1; 100k iterations). The PBKDF2 algorithm, salt and iteration count are serialized, so old backups stay decryptable.
- `EncryptedKey`: AES/GCM wrapping of a `SecretKey`, with the wrapped key's algorithm name used as AAD.
- `Encryptor`: encrypts new token secrets with the Keystore `masterKey`. It does this without the password, which is why the Keystore copy is encrypt-only.

Because the master key in the Keystore can only encrypt, **restoring always needs the user's password**: `TokenPersistence.restore(pwd)` decrypts `MasterKey`, re-imports it into the Keystore, then unwraps each backed-up secret. `MasterKey`, `EncryptedKey` and `Token` are persisted through Gson reflection, so renaming or retyping their fields breaks existing backups.

Backup paths (see `BACKUP.md`): Android Auto Backup of the `tokenBackup` prefs via `TokenBackupAgent` (`res/xml/backup_rules.xml`, `data_extraction_rules.xml`), and manual export/import of the same prefs map as a Java-serialized object (`copyBackupToExternal` / `restoreBackupFromExternal`).

### App flow

- `PasswordActivity` (Kotlin/Compose, `ui/password/`) is the launcher. It provisions the master password on first run (strength checked with nbvcxz in `PasswordViewModel`) and then hands off to `main.Activity`.
- `main.Activity` handles `otpauth://totp|hotp` VIEW intents, the toolbar menus (backup, restore, scan, manual add) and restore prompts. Its dialogs are Compose (`ui/dialogs/`, shown through `MainDialogsViewModel`); the remaining simple alerts are `AlertDialog`s. QR scanning uses CameraX and ZXing.
- `OnBoardingActivity` (Kotlin/Compose, `ui/onboarding/`) shows the first-run introduction; completion is stored as `onBoardingComplete` in the default shared preferences.
- `ManualAdd` (Kotlin/Compose, `ui/manualadd/`) returns the entered token as an `otpauth://` URI result; `ManualAddForm.toUri()` builds it.
- Sharing a code: the Compose `ShareSheet` (`ui/share/`) lists the `ShareTarget`s of the `Discoverable` implementations in `main/share/` (`Clipboard`, and `Jelling`, which shares over Bluetooth LE GATT).

### Token model

- `Token` parses and validates `otpauth://` URIs (`URI.md` documents the FreeOTP superset of the Google Authenticator format: `image`, `color`, `lock`, and more). `parse()` enforces safety rules such as a minimum 128-bit secret, throwing `Unsafe*Exception`s. `parseUnsafe()` skips those checks for the user-confirmed path.
- `Code` renders the truncated HMAC using an issuer-specific alphabet (Steam uses a custom alphabet and reversed output) and tracks the validity window.
- `utils/Time` is a swappable time source (`Time.INSTANCE`), used so tests can control TOTP time.
- `TokenIcon` derives the icon and color from the issuer. Issuer→Font Awesome icon mappings live in `res/values/drawables.xml` and brand colors in `res/values/colors.xml` (see `BRANDING.md`).

### Tests

The instrumented tests run against the real AndroidKeyStore. `androidTest/.../Context.java` and `MemorySharedPreferences.java` provide a fake Context backed by in-memory SharedPreferences, so `Adapter` and `TokenPersistence` can be tested without touching app data.

## Conventions

- Commit messages: a title under 50 characters, a blank line, then an optional body with lines under 100 characters, then optional footer lines (e.g. `Fixes #<n>`, one per issue). Squash small changes into one commit. UI changes should come with screenshots of the new or changed screens.
- User-visible strings go in `res/values/strings.xml`. There are many translated `values-*` locales, and `generateLocaleConfig` is on. Toast-like messages go through `utils/UserNotifier`.
- Resource IDs are not `final` (AGP 9), so don't `switch` on `R.id.*`; use `if` chains.
- Edge-to-edge inset handling for targetSdk 35+ is centralized in `utils/SystemBarInsets`.
- Store listing metadata lives in `fastlane/metadata/android`.

## Ongoing migration to Kotlin + Compose

The app is being migrated to Kotlin and Jetpack Compose (Material 3) in phases. `MIGRATION.md` holds the decisions, guardrails, phase checklist and status. Read it before touching UI code, and tick off items there as they land. Until the migration is done, parts of the descriptions above (view binding, `UserNotifier`, `SystemBarInsets`, RecyclerView `Adapter`) describe the old code. Compose screens live in `ui/<screen>/` (Compose UI tests in the matching `androidTest` package, which `-PtestMinified` skips); their activities are thin `ComponentActivity` wrappers that call `enableEdgeToEdge()`, and activities with text input need `android:windowSoftInputMode="adjustResize"` so `imePadding()` works. Two rules matter most:
- Never change field names or types of the Gson-persisted classes (`Token`, `MasterKey`, `EncryptedKey`). `BackupFormatGoldenTest` guards the backup format: never regenerate its fixtures to make it pass. After touching these classes or `proguard-rules.pro`, also run the tests with `-PtestMinified`.
- New UI code is Kotlin + Compose; don't add new XML layouts or Java UI classes.
