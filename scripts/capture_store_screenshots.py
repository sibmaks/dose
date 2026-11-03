#!/usr/bin/env python3
"""Capture the real app with fictional data on a dedicated emulator.

Never point this at a personal device: seeding replaces Dose's emulator data.
The default serial is reserved for the temporary store-capture AVD.
"""

import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import time
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1]
ADB = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Library/Android/sdk"))) / "platform-tools/adb"
PACKAGE = "io.github.sibmaks.app.dose"
OUTPUT = ROOT / "store-assets/rustore/ru"
SERIAL = "emulator-5580"


def adb(*args, data=None, check=True):
    return subprocess.run([str(ADB), "-s", SERIAL, *map(str, args)], input=data,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=check).stdout


def shell(*args, **kwargs):
    return adb("shell", *args, **kwargs)


def require_capture_emulator():
    if SERIAL != "emulator-5580" or "DoseStore" not in adb("emu", "avd", "name").decode():
        raise RuntimeError("Preference writes require the dedicated DoseStore AVD on emulator-5580")


def nodes():
    shell("uiautomator", "dump", "/sdcard/dose-store.xml")
    return list(ET.fromstring(shell("cat", "/sdcard/dose-store.xml")).iter("node"))


def describe():
    for node in nodes():
        a = node.attrib
        if a.get("text") or a.get("content-desc"):
            print(a.get("text"), "|", a.get("content-desc"), a.get("bounds"), flush=True)


def tap(label, index=0, contains=False):
    matches = [n for n in nodes() if any(
        (label in n.get(key, "") if contains else label == n.get(key, ""))
        for key in ("text", "content-desc"))]
    if len(matches) <= index:
        raise RuntimeError(f"Cannot find {label!r}; found {len(matches)} matches")
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", matches[index].get("bounds")))
    shell("input", "tap", (x1 + x2) // 2, (y1 + y2) // 2)
    time.sleep(.6)


def back():
    shell("input", "keyevent", "KEYCODE_BACK")
    time.sleep(.5)


def launch():
    shell("am", "force-stop", PACKAGE)
    shell("am", "start", "-n", PACKAGE + "/.MainActivity")
    time.sleep(2)


def capture(name, device="phone"):
    target = OUTPUT / "raw" / device / (name + ".png")
    target.parent.mkdir(parents=True, exist_ok=True)
    time.sleep(.6)
    payload = adb("exec-out", "screencap", "-p")
    size = struct.unpack(">II", payload[16:24])
    expected = (1080, 1920) if device == "phone" else (1200, 1920)
    if size != expected:
        raise RuntimeError(f"Expected native {expected} capture, got {size}; restart the emulator with the matching -skin WxH")
    target.write_bytes(payload)
    print(target, flush=True)


def preferences(name, values):
    require_capture_emulator()
    entries = []
    for key, value in values.items():
        if isinstance(value, bool):
            entries.append(f'<boolean name="{key}" value="{str(value).lower()}" />')
        else:
            if not isinstance(value, str):
                value = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
            entries.append(f'<string name="{key}">{escape(value)}</string>')
    data = ('<?xml version="1.0" encoding="utf-8"?><map>' + "".join(entries) + "</map>").encode()
    shell("run-as", PACKAGE, "mkdir", "-p", "shared_prefs")
    shell("run-as", PACKAGE, "sh", "-c", f"'cat > shared_prefs/{name}.xml'", data=data)


def seed():
    require_capture_emulator()
    shell("am", "force-stop", PACKAGE)
    shell("settings", "put", "global", "window_animation_scale", "0")
    shell("settings", "put", "global", "transition_animation_scale", "0")
    shell("settings", "put", "global", "animator_duration_scale", "0")
    shell("settings", "put", "system", "font_scale", "1.0")
    shell("settings", "put", "system", "screen_off_timeout", "2147483647")
    shell("settings", "put", "global", "auto_time_zone", "0")
    shell("settings", "put", "global", "auto_time", "0")
    shell("setprop", "persist.sys.timezone", "Europe/Moscow")
    shell("settings", "put", "global", "sysui_demo_allowed", "1")
    shell("am", "broadcast", "-a", "com.android.systemui.demo", "--es", "command", "enter")
    shell("am", "broadcast", "-a", "com.android.systemui.demo", "--es", "command", "clock", "--es", "hhmm", "0941")
    shell("am", "broadcast", "-a", "com.android.systemui.demo", "--es", "command", "battery", "--es", "level", "100", "--es", "plugged", "false")
    shell("am", "broadcast", "-a", "com.android.systemui.demo", "--es", "command", "notifications", "--es", "visible", "false")
    today = dt.date.fromisoformat(shell("date", "+%Y-%m-%d").decode().strip())
    start = (today - dt.timedelta(days=45)).isoformat()
    def rule(times, **extra):
        return dict(kind="DAILY", times=times, dates=[], weekDays=[], monthDays=[],
                    createdOn=start, priority=0, **extra)
    medications = [
        dict(id="store-vitamin", name="Витамин D3", amount="1", quantity="capsule", form="CAPSULE",
             color="amber", note="Во время завтрака", active=True, schedules=[rule(["08:00"])]),
        dict(id="store-magnesium", name="Магний", amount="100", quantity="mg", form="PILL",
             color="violet", note="После еды. Запить водой.", active=True,
             schedules=[rule(["13:00"]), rule(["21:00"], amount="200")]),
        dict(id="store-omega", name="Омега-3", amount="1", quantity="capsule", form="CAPSULE",
             color="mint", note="Во время ужина", active=True, schedules=[rule(["18:00"])])]
    logs, events = [], []
    zone = dt.timezone(dt.timedelta(hours=3))
    for ago in range(35):
        day = today - dt.timedelta(days=ago)
        for med in medications:
            for schedule in med["schedules"]:
                for clock in schedule["times"]:
                    if ago == 0 and clock != "08:00":
                        continue
                    moment = dt.datetime.combine(day, dt.time.fromisoformat(clock), zone)
                    scheduled = int(moment.timestamp() * 1000)
                    delay = [0, 2, 3, 5, 4, 1, 6][(ago + int(clock[:2])) % 7]
                    amount = schedule.get("amount", med["amount"])
                    log = dict(date=day.isoformat(), medicationId=med["id"], medicationName=med["name"],
                               dose=f'{amount} ' + {"capsule": "капсула", "mg": "мг"}[med["quantity"]],
                               time=clock, status="taken",
                               recordedAt=scheduled + delay * 60000, scheduledAt=scheduled, onTime=False)
                    logs.append(log)
                    events.append(dict(date=day.isoformat(), medicationId=med["id"], time=clock,
                                       type="reminder", at=scheduled))
    preferences("dose_preferences_v1", dict(medications=medications, logs=logs, events=events,
                                           snoozes={}, daily_time_overrides={}))
    preferences("app_language", dict(offered=True, language="ru"))
    preferences("dose_alert_settings", dict(setup_offered=True, setup_reviewed=True, feed_enabled=True,
                                           overlay_enabled=True, feed_permission_requested=True))
    preferences("dose_appearance", dict(theme_mode="light"))
    shell("appops", "set", PACKAGE, "SYSTEM_ALERT_WINDOW", "allow")
    launch()


def popup(device):
    # Use the real opt-in popup service at a due time. Feed-only heads-up banners
    # are disabled for this frame so they do not obscure the application.
    require_capture_emulator()
    shell("am", "force-stop", PACKAGE)
    preferences("dose_alert_settings", dict(setup_offered=True, setup_reviewed=True,
                                           feed_enabled=False, overlay_enabled=True))
    shell("date", "110313002026.00")
    launch()
    day = "2026-11-03"
    at = int(dt.datetime.fromisoformat(day + "T13:00:00+03:00").timestamp() * 1000)
    shell("am", "start-foreground-service", "-n", PACKAGE + "/.reminders.ReminderOverlayService",
          "--es", "medicationId", "store-magnesium", "--es", "date", day,
          "--es", "time", "13:00", "--el", "scheduledAt", at)
    time.sleep(1.5)
    if "ty=2038" not in shell("dumpsys", "window", "windows").decode():
        raise RuntimeError("The native application overlay is not visible")
    capture("06-popup", device)
    shell("am", "force-stop", PACKAGE)


def capture_set(device):
    require_capture_emulator()
    adb("root")
    adb("wait-for-device")
    physical = "1080x1920" if device == "phone" else "1200x1920"
    if f"Physical size: {physical}" not in shell("wm", "size").decode():
        raise RuntimeError(f"Restart the dedicated emulator with -skin {physical} before capturing {device}")
    shell("am", "force-stop", PACKAGE)
    shell("settings", "put", "global", "auto_time", "0")
    shell("setprop", "persist.sys.timezone", "Europe/Moscow")
    shell("am", "broadcast", "-a", "android.intent.action.TIMEZONE_CHANGED",
          "--es", "time-zone", "Europe/Moscow")
    shell("date", "110309412026.00")
    shell("wm", "size", "1080x1920" if device == "phone" else "1200x1920")
    shell("wm", "density", "360" if device == "phone" else "320")
    seed()
    capture("01-today", device)
    tap("Лекарства")
    capture("02-medications", device)
    tap("Изменить Магний")
    tap("Продолжить")
    capture("05-forms", device)
    tap("Продолжить")
    capture("04-dosage", device)
    tap("Продолжить")
    capture("03-schedule", device)
    launch()
    tap("История")
    capture("08-report", device)
    tap("Витамин D3", index=1)
    capture("07-history", device)
    launch()
    tap("Тема:", contains=True)
    tap("Тёмная")
    capture("09-dark", device)
    tap("Тема:", contains=True)
    tap("Светлая")
    tap("Язык приложения:", contains=True)
    capture("10-languages", device)
    popup(device)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["seed", "describe", "capture", "tap", "back", "launch", "set", "popup"])
    parser.add_argument("value", nargs="?")
    parser.add_argument("--device", default="phone", choices=["phone", "tablet"])
    args = parser.parse_args()
    if args.action == "seed": seed()
    elif args.action == "describe": describe()
    elif args.action == "capture": capture(args.value, args.device)
    elif args.action == "tap": tap(args.value)
    elif args.action == "back": back()
    elif args.action == "launch": launch()
    elif args.action == "set": capture_set(args.device)
    elif args.action == "popup": popup(args.device)
