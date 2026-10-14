#!/usr/bin/env python3
"""Seed the Android emulator with the synthetic bank SMS in sample-sms.tsv and check the app's result.

  seed_sms.py history   write the dated samples straight into the emulator's SMS inbox (needs `adb root`)
  seed_sms.py live      send the `live` samples through the emulator modem (the app's receiver gets them)
  seed_sms.py verify    compare the app's database with each sample's expected outcome

Emulator only: `history` edits the telephony database directly so messages can be backdated.
See docs/TESTING.md.
"""
import csv
import os
import shutil
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timedelta, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
SAMPLES = os.path.join(HERE, "sample-sms.tsv")
PACKAGE = "ir.pargartech.paymentrecorder"
APP_DB = f"/data/data/{PACKAGE}/databases/payment-recorder.db"
SMS_DB = "/data/data/com.android.providers.telephony/databases/mmssms.db"
ADB = shutil.which("adb") or os.path.expanduser("~/Android/Sdk/platform-tools/adb")


def adb(*args, input=None):
    return subprocess.run([ADB, *args], check=True, capture_output=True, text=True, input=input).stdout


def samples():
    with open(SAMPLES, encoding="utf-8") as f:
        rows = [line for line in f if not line.startswith("#")]
    for row in csv.DictReader(rows, delimiter="\t"):
        row["body"] = row["body"].replace("\\n", "\n")
        yield row


def timestamp_ms(when):
    if when.isdigit():
        t = datetime.now(timezone.utc) - timedelta(days=int(when))
    else:
        t = datetime.fromisoformat(when)
    return int(t.timestamp() * 1000)


def sql_str(s):
    return "'" + s.replace("'", "''") + "'"


def device_sql(db, sql):
    """Runs SQL on the device as root; returns rows split on the unit separator."""
    with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False, encoding="utf-8") as f:
        f.write(sql)
        local = f.name
    try:
        adb("push", local, "/data/local/tmp/seed.sql")
    finally:
        os.unlink(local)
    out = adb("shell", f"sqlite3 -separator $'\\x1f' -newline $'\\x1e' {db} < /data/local/tmp/seed.sql")
    return [r.split("\x1f") for r in out.split("\x1e") if r]


def history():
    if adb("shell", "id -u").strip() != "0":
        sys.exit("adb is not root; run `adb root` first (emulator Google APIs images allow it).")
    values = []
    for s in samples():
        if s["when"] == "live":
            continue
        ts = timestamp_ms(s["when"])
        values.append(f"({sql_str(s['sender'])}, {sql_str(s['body'])}, {ts}, {ts}, 1, 1, 1)")
    device_sql(SMS_DB, "INSERT INTO sms (address, body, date, date_sent, type, read, seen) VALUES\n"
               + ",\n".join(values) + ";\n")
    print(f"wrote {len(values)} SMS into the inbox")


def live():
    sent = 0
    for s in samples():
        if s["when"] != "live":
            continue
        # The emulator console turns \n into a line break; ASCII-only senders are fine as-is.
        adb("emu", "sms", "send", s["sender"], s["body"].replace("\n", "\\n"))
        sent += 1
        time.sleep(2)
    print(f"sent {sent} live SMS")


def verify():
    # Checkpoint first so rows still in the WAL are visible to the reader.
    txs = device_sql(APP_DB, "PRAGMA wal_checkpoint(FULL);\n"
                     "SELECT smsBody, type, amountRial FROM transactions;\n")[1:]
    reviews = device_sql(APP_DB, "SELECT body, status FROM review_items;\n")
    sent_live = {r[0] for r in device_sql(SMS_DB, "SELECT body FROM sms;\n")}

    by_body = {}
    for body, kind, amount in txs:
        by_body.setdefault(body, []).append((kind, int(amount)))
    review_bodies = {body for body, _ in reviews}

    ok = failed = skipped = 0
    for s in samples():
        body = s["body"]
        if s["when"] == "live" and body not in sent_live:
            skipped += 1
            continue
        got = by_body.get(body, [])
        expect = s["expect"]
        if expect in ("dep", "wd"):
            want = ("DEPOSIT" if expect == "dep" else "WITHDRAWAL", int(s["amount"]))
            good = got == [want]
            actual = got or ("review" if body in review_bodies else "nothing")
        elif expect == "review":
            # A review item may since have been recorded by hand; it still went through review.
            good = body in review_bodies
            actual = got or "nothing"
        else:
            good = not got and body not in review_bodies
            actual = got or ("review" if body in review_bodies else "ignored")
        ok += good
        failed += not good
        if not good:
            print(f"FAIL {s['when']:>25} {s['sender']:<12} expected {expect} {s['amount']}, got {actual}\n"
                  f"     {body[:80]!r}")
    print(f"{ok} as expected, {failed} wrong, {skipped} live not sent yet")
    print(f"{len(txs)} transactions, {len(reviews)} review items in the app")
    return failed == 0


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "history":
        history()
    elif cmd == "live":
        live()
    elif cmd == "verify":
        sys.exit(0 if verify() else 1)
    else:
        sys.exit(__doc__)
