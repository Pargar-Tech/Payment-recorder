# PRD — تراکنش‌های من (Payment Recorder)

Status: v1 (MVP) in development · Owner: Pargar Tech · Source: original Persian spec "SmsTracker 1.0" + product/technical interview (Oct 2026)

## 1. Problem

People with accounts at several Iranian banks get transaction SMS from each bank in a different format. There is no single place to see all deposits and withdrawals, and for a business, no easy way to tell which incoming payment was from whom or to hand the month's activity to an accountant.

## 2. Goal

An Android app that reads bank SMS automatically, extracts each transaction, and shows every bank's activity in one place — for both **personal** and **business** accounts — without the data ever leaving the phone (v1).

## 3. Users

| User | Need |
|---|---|
| Owner (personal + business on one phone) | See all money in/out per Jalali month, tell personal from business, note who paid, export for the accountant |
| v2: Staff with their own phones | Their business accounts appear in a shared view; their personal accounts stay private |
| v2: Accountant | Read-only web dashboard + export |

## 4. Scope

### v1 — offline, one phone (this release)

| # | Feature | Notes |
|---|---|---|
| 1 | Live capture of incoming bank SMS | Background `BroadcastReceiver`; notification per recorded transaction |
| 2 | One-time inbox scan | User picks range on first run: 1 / 3 / 12 months / all. Re-scan available in Settings; duplicates never recorded twice |
| 3 | Persian SMS parser for all banks | Persian/Arabic digits, deposit/withdrawal, amount, balance, account last digits, bank. Generic rules + per-bank tuning as real samples arrive |
| 4 | Review list | Bank-looking SMS the parser can't read are kept, not dropped. User records by hand, dismisses, marks sender as a bank ("trust") or ignores the sender. Notification when an item needs review |
| 5 | Accounts | Auto-created from bank + last digits of account/card. User renames and tags each **personal** or **business** |
| 6 | Home | Current Jalali month: total deposits, withdrawals, net; list of transactions. Previous/next month. Filters: personal/business, account, category |
| 7 | Transaction detail | Amount is read-only (as the bank sent it). Editable: payer name, note, category, hidden |
| 8 | Categories + rules | Manual categories; simple rules "SMS contains X / account Y / deposit or withdrawal → category" applied to new transactions |
| 9 | Export | CSV (opens in Excel with Persian text) of the current month/filter |
| 10 | Backup / restore | Manual DB file via system file picker (save to Telegram/Drive yourself). Restore replaces all data |
| 11 | App lock | Fingerprint/face or phone PIN; re-locks after 30 s in background; hides content in recent apps |
| 12 | "Share sample" | Shares a digit-masked copy of an unreadable SMS so the parser can learn that bank's format |

Display rules: amounts **stored in rial**, **shown in toman**. Dates in Jalali (Asia/Tehran). UI Persian only, RTL. Transfers between the user's own accounts appear as two normal transactions (no special linking in v1).

### Out of scope for v1

Sync, multiple users, web dashboard, charts, auto-detected internal transfers, .xlsx (CSV only), encrypted DB, iPhone, Google Play.

### v2 — sync (planned)

- Several phones, combined view in the app and a **web dashboard**.
- Backend: Kotlin (Ktor) + PostgreSQL, reusing the `core` module. Hosting decided later (consider in-country hosting; Firebase/Google Cloud are not options).
- Roles: **Owner** sees everything · **Staff** sync only business accounts — personal accounts never leave the phone · **Accountant** read + export only.
- v1 data model already carries UUIDs, `updatedAt`, `deviceId` so no migration of existing data is needed.

### Later

Charts, auto-linking internal transfers, auto-suggested categories, .xlsx export, SQLCipher.

## 5. Distribution & constraints

- **Sideloaded APK only.** Google Play restricts SMS permissions to default SMS apps; this app is not one.
- Android 8+ (minSdk 26). On Android 13+ the user must enable *Allow restricted settings* for sideloaded apps before SMS permission can be granted.
- Some vendors (Xiaomi, Huawei, …) block background SMS delivery unless battery optimization is disabled for the app.
- No `INTERNET` permission in v1 — verified in the manifest (`tools:node="remove"`).

## 6. Success criteria (v1)

1. ≥ 95% of transaction SMS from the banks the owner actually uses are recorded correctly without review (measured on real samples in the test suite).
2. 0 lost bank SMS: everything bank-like is either recorded or in the review list.
3. 0 duplicates after re-scans and live capture of the same SMS.
4. Owner can produce the month's CSV for the accountant in under a minute.

## 7. Open questions

- Real masked SMS samples per bank — the owner doesn't have all banks; collected over time via the in-app "share sample" button.
- v2 hosting location and auth method.
- Whether staff phones need their own app lock policy enforced by the owner.
