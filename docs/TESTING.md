# Testing on an emulator

How to run the app end to end on an Android emulator with synthetic bank SMS, and the
result of the last full pass. Companion to [TECH.md](TECH.md).

## Setup (Linux, no Android Studio)

| Piece | Version used | Where |
|---|---|---|
| JDK | Temurin 17 | `~/.jdks/jdk-17.0.20.1+1` (system Java 25 is too new for Gradle 8.11 / AGP 8.7) |
| Android SDK | cmdline-tools 19.0, platform-tools 37, platform 35, build-tools 34 + 35, emulator 37.2 | `~/Android/Sdk` |
| System image | `system-images;android-35;google_apis;x86_64` r09 | AVD `pr35` (Pixel 6, 2 GB) |

`local.properties` (gitignored) holds `sdk.dir=/home/<you>/Android/Sdk`.

```bash
export JAVA_HOME=~/.jdks/jdk-17.0.20.1+1 ANDROID_HOME=~/Android/Sdk
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
~/Android/Sdk/emulator/emulator -avd pr35 -no-snapshot -gpu swiftshader_indirect &
```

Use `-gpu swiftshader_indirect`: with `-gpu host` the emulator crashed during the backup-restore
test (gfxstream "null ctx" errors), unrelated to the app.

### When Google's Android servers are blocked

From some networks (and VPN exits) `dl.google.com/android/...` and Google Maven return **404**
while other Google downloads work. Workarounds that were used:

- **SDK packages:** downloaded from the Tencent mirror (`mirrors.cloud.tencent.com/AndroidSDK/`),
  each zip's SHA-1 checked against checksums published independently on GitHub (Arch AUR
  `android-sdk-*` packages and others), then installed with `sdkmanager` pointed at a local copy
  (`SDK_TEST_BASE_URL=http://127.0.0.1:8799/`). cmdline-tools **23** is a wrapper that downloads
  "Android CLI" from Google, so use **19.0** (`commandlinetools-linux-13114758`).
- **Gradle dependencies:** a user-level init script, kept outside the repo, that points
  `google()` at Aliyun's proxy of Google Maven:

  ```groovy
  // ~/Android/gradle-mirror.init.gradle — use: ./gradlew -I ~/Android/gradle-mirror.init.gradle ...
  def redirect = { RepositoryHandler repos ->
      repos.withType(MavenArtifactRepository).all { r ->
          def u = r.url.toString()
          if (u.contains('dl.google.com') || u.contains('maven.google.com')) {
              r.url = 'https://maven.aliyun.com/repository/google'
              // KSP isn't on Google Maven; Aliyun answers 502 (not 404) for its plugin marker.
              r.content { excludeGroupByRegex 'com\\.google\\.devtools\\.ksp.*' }
          }
      }
  }
  beforeSettings { s ->
      redirect(s.pluginManagement.repositories)
      redirect(s.dependencyResolutionManagement.repositories)
  }
  ```

  This trusts a third-party mirror for Google's artifacts. Prefer a network where Google works,
  and never use it for release builds. CI (GitHub Actions) isn't affected.

## Sample data

`tools/sample-sms.tsv` holds about 30 **synthetic** SMS: Mellat, Saman, Tejarat, Pasargad, Saderat,
Melli, Blu and Ayandeh shapes; Persian digits; toman; signs; negative balance; two SMS either side
of the Mehr/Shahrivar month boundary; plus noise (OTP, promo, personal, unknown sender). Each row
says when it arrives and what should happen to it.

```bash
adb root                              # Google APIs images allow it
python3 tools/seed_sms.py history     # backdated SMS straight into the inbox (before first launch)
# install, grant SMS permission, pick a scan range
python3 tools/seed_sms.py live        # SMS through the emulator modem → the app's receiver
python3 tools/seed_sms.py verify      # compares the app's database with each row's expectation
```

The emulator's provider reads the SMS store at `/data/data/com.android.providers.telephony/databases/mmssms.db`
(not the `user_de` copy).

## Last full pass — 2026-10-14, API 35 emulator

`verify`: **26 / 26** samples as expected (21 history + 5 live), after a 3-month scan, a full
re-scan and live delivery. 0 duplicates.

| PRD feature | Result |
|---|---|
| 1 Live capture + notification | Pass: receiver records in the background; "واریز/برداشت ثبت شد" and review notifications; review notification opens the Review tab |
| 2 Inbox scan + re-scan | Pass: range respected (150-day SMS skipped by the 3-month scan); re-scan adds only what's new. **Fixed:** re-scan from Settings showed no progress or result (B1) |
| 3 Parser | Pass on all samples, including Persian digits, toman, signs, card masks, month boundary |
| 4 Review list | Pass: manual record pre-fills type/amount; "trust" auto-records that sender's other pending items; share sample masks digits. **Fixed:** "ignore sender" was one tap with no confirmation and no undo (B2) |
| 5 Accounts | Pass: auto-created per bank + last digits; rename and personal/business saved |
| 6 Home + filters | Pass: month navigation, totals, scope / account / category filters |
| 7 Transaction detail | Pass: payer, note, category, hide saved. **Fixed:** hidden transactions could never be shown or un-hidden (B3) |
| 8 Categories + rules | Pass: "contains SNAPP → Food" and "account + deposit → Salary" applied to live SMS |
| 9 CSV export | Pass: UTF-8 BOM, Persian headers, toman amounts, negative balance kept |
| 10 Backup / restore | Pass: backup is a valid SQLite file; a CSV is rejected as a backup; restore replaces data and restarts |
| 11 App lock | Pass: enabling requires a PIN; re-locks after 30 s; FLAG_SECURE blanks screenshots. **Fixed:** opened unlocked after Android killed the app in the background (B4); **fixed:** locked out forever if the phone's screen lock is removed (B5) |
| 12 Share sample | Pass |
| No INTERNET permission | Pass (merged manifest) |

Also fixed: the Room schema export (`android/app/schemas/`) was never committed (B6).

Not covered on the emulator: Android 8 (minSdk 26), the "Allow restricted settings" path for a
sideloaded APK (adb installs bypass it), vendor battery-optimisation killing the receiver, real
bank SMS formats.
