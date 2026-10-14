# تراکنش‌های من — Payment Recorder

Android app that reads Iranian bank SMS, records every deposit and withdrawal automatically, and shows all banks, personal and business, in one place. Version 1 is fully offline: the app has no internet permission.

- **Product spec:** [docs/PRD.md](docs/PRD.md)
- **Technical design:** [docs/TECH.md](docs/TECH.md)

## Get the APK

Every push builds the app in GitHub Actions → **Actions → Android → latest run → Artifacts → `payment-recorder-apk-…`**.

Install on the phone (Android 8+):

1. Copy the APK to the phone and open it; allow installing from this source.
2. Android 13+: **Settings › Apps › تراکنش‌های من › ⋮ › Allow restricted settings**.
3. Open the app, grant SMS permission, choose how far back to scan.
4. Xiaomi/Huawei/etc.: disable battery optimization for the app so new SMS are caught.

See [TECH.md › Signing](docs/TECH.md#signing) before relying on in-place updates.

## Develop

```bash
./gradlew :core:test           # parser + calendar tests (plain JVM)
./gradlew :app:assembleDebug   # Android build (needs Android SDK)
```

Parser not reading your bank? Use **بررسی › ارسال نمونه** in the app to share a masked sample, then add it as a test. See [TECH.md › Adding a bank format](docs/TECH.md#adding-a-bank-format).
