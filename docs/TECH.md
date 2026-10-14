# Technical design — Payment Recorder

Companion to [PRD.md](PRD.md).

## Repository layout (monorepo)

```
core/            Pure Kotlin/JVM: SMS parser, Jalali calendar, money, category rules, CSV export.
                 No Android dependencies → unit-tested on the JVM and reusable by the v2 backend.
android/app/     Android app (Compose, Room). Gradle path :app.
backend/         (v2) Ktor + PostgreSQL, will depend on :core.
web/             (v2) dashboard.
docs/            PRD, this file.
.github/workflows/android.yml   CI: tests + APKs.
```

One Gradle build at the repo root (`settings.gradle.kts`) with a version catalog in `gradle/libs.versions.toml`.

## Stack

| | |
|---|---|
| Language / UI | Kotlin 2.0.21, Jetpack Compose (Material 3, BOM 2024.12.01) |
| Storage | Room 2.6.1 (KSP) |
| Build | AGP 8.7.3, Gradle 8.11.1, JDK 17 |
| SDK | min 26 (Android 8) · target/compile 35 |
| Other | androidx.biometric 1.1.0, coroutines/Flow |

No DI framework: `App` holds `Prefs`, `AppDatabase`, `Repo` (the app is small).

## Data flow

```
SMS arrives ──► SmsReceiver ─┐
                             ├─► Repo.handleSms ─► dedupe ─► SmsParser ─┬─ Parsed ──────► transactions (+account, +category rule)
Inbox scan ──► Repo.scanInbox┘                                         ├─ NeedsReview ─► review_items
                                                                       └─ Ignored
Room Flow ─► MainViewModel ─► Compose screens (Home / Accounts / Review / Settings / Detail)
```

`Repo` is the only writer and serializes SMS handling with a `Mutex`, so the live receiver and a running scan can't create duplicate accounts.

## Parser (`core/.../parser`)

1. **Normalize** (`TextNormalizer`): Persian/Arabic digits → ASCII, ي/ك → ی/ک, Arabic separators, ZWNJ, bidi marks.
2. **Ignore** OTP/login messages (`رمز پویا`, `کد تایید`, …) and senders the user ignored.
3. **Bank** (`Banks`): alphanumeric sender keywords, then body patterns that require `بانک X` (bank names like سامان/سینا/آینده are also names/words). Numeric short-code senders are *learned*: when the user trusts a sender in the review list it is stored in `senders` and passed to the parser as a `SenderDirectory`.
4. **Numbers** (`NumberScanner`): masked account/card numbers (`123***4567`, `…1234`, `12xx34`, long/dashed numbers near «حساب/کارت») are extracted as the account key (last 4 digits) and blanked; dates and times are blanked; remaining numbers are labelled by the nearest preceding keyword (amount: مبلغ/واریز/برداشت/…; balance: مانده/موجودی). `تومان` after a number multiplies by 10.
5. **Type**: sign on the amount (`+1,000` / `1,000-`) wins; otherwise the earliest deposit vs withdrawal keyword. `انتقال` alone is ambiguous → review.
6. **Result**: `Parsed` (bank, type, amount all known) · `NeedsReview` (looks like a transaction but bank/type/amount missing; partial values pre-fill the form) · `Ignored`.

Unknown senders need both a type keyword and an amount to reach review, to keep shop/OTP noise out.

### Adding a bank format

1. Get a masked sample (in-app *ارسال نمونه* or pasted by hand).
2. Add a test to `core/src/test/.../SmsParserTest.kt` under "Real samples".
3. Run `./gradlew :core:test`, adjust keywords/patterns until green. Keep all existing tests green.

The current tests use **synthetic** samples covering common shapes; they are not copied from real bank SMS. Sender keywords in `Banks` are unverified guesses.

## Dedupe

`Dedupe.hash = SHA-256(normalized sender | trimmed body | timestamp in minutes)`, unique in both `transactions` and `review_items`. The receiver uses the PDU timestamp; the inbox scan uses `date_sent` (falls back to `date`). Because those can differ, `Repo` also treats an identical sender+body within ±10 minutes as a duplicate. Hidden transactions keep their hash, so a re-scan never brings them back.

## Data model (Room, `payment-recorder.db`, version 1)

| Table | Key fields |
|---|---|
| `accounts` | id (UUID), bankId (`mellat` or `sender:98700…`), accountKey (last digits or ""), displayName, scope (UNSET/PERSONAL/BUSINESS) · unique(bankId, accountKey) |
| `transactions` | id, accountId → accounts, type, amountRial, balanceRial?, occurredAt, smsSender, smsBody, dedupeHash (unique), categoryId?, payerName, note, hidden, source (SMS/MANUAL) |
| `categories` | id, name |
| `category_rules` | id, categoryId, bodyContains?, accountId?, type?, priority |
| `review_items` | id, sender (normalized), body, receivedAt, dedupeHash (unique), reason, status (PENDING/RESOLVED/DISMISSED), resolvedTxId? |
| `senders` | sender (normalized, PK), trust (TRUSTED/IGNORED), bankId? |

All synced entities carry `createdAt`, `updatedAt`, `deviceId` for v2. Room schema JSON is exported to `android/app/schemas/` (CI uploads it as an artifact; commit it whenever the schema changes, and write a `Migration` for every version bump — never use destructive migration, users can't recover lost SMS-derived notes).

Known sync gaps to close in v2: category-rule deletion is a hard delete (needs tombstones), and amounts are immutable so conflicts only arise on user fields (last-writer-wins on `updatedAt` is enough).

## Money & dates

Rial `Long` everywhere; `Money.formatToman` divides by 10 for display (keeps a ٫5 fraction if the rial amount isn't a multiple of 10). Jalali conversion is a port of jalaali-js (`JalaliDate`), tested against known dates and a 10-year round-trip. Month boundaries are computed in `Asia/Tehran`.

## Security & privacy

- No `INTERNET`/`ACCESS_NETWORK_STATE` permission (explicitly removed in the manifest so no library can add it).
- `allowBackup=false` + data-extraction rules exclude everything: no Google cloud backup or device transfer.
- App lock: `BiometricPrompt` with `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`; enabling requires a successful unlock; re-locks after 30 s in background; `FLAG_SECURE` while enabled.
- Full SMS text (with balances) is stored unencrypted in the app's private storage. Backups are unencrypted — the UI says so. SQLCipher is on the roadmap.
- CSV export neutralizes formula injection (`=`, `+`, `-`, `@`).

## Signing

Every CI run's debug APK is signed with that runner's throwaway debug key, so a newer debug APK **can't be installed over an older one** (Android refuses; uninstalling deletes the data — back up first).

For in-place updates, set up a release key once:

```bash
keytool -genkeypair -v -keystore release.jks -alias recorder -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks   # → secret SIGNING_KEYSTORE_BASE64
```

Add repository secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. CI then produces a signed `app-release.apk`; always install that one. Keep `release.jks` and its passwords somewhere safe — losing it means users must uninstall to update.

## CI

`.github/workflows/android.yml` on every push/PR: `:core:test`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease`; uploads APKs (artifact `payment-recorder-apk-<run>`), the Room schema, and test reports on failure. `versionCode` = CI run number.

## Building locally

```bash
./gradlew :core:test          # parser tests, no Android SDK needed
./gradlew :app:assembleDebug  # needs Android SDK (Android Studio)
```

Or open the repo root in Android Studio (JDK 17).

## Roadmap (technical)

- v2: `backend/` (Ktor + PostgreSQL), auth, sync endpoint (push/pull by `updatedAt`), per-account sync flag (personal accounts never synced), `web/` dashboard.
- Parser: real samples per bank; auto-detect internal transfers.
- SQLCipher, .xlsx export, charts.
