#!/usr/bin/env python3
"""Exercise a running English Dose debug build without saving the medication draft.

Requires Python 3 and adb. Start on a main tab with the keyboard closed. XML and
screenshots are written to --output (a temporary directory by default).
"""

import argparse
import pathlib
import re
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()
    output = args.output or pathlib.Path(tempfile.mkdtemp(prefix="dose-editor-ime-"))
    output.mkdir(parents=True, exist_ok=True)
    command = [args.adb] + (["-s", args.serial] if args.serial else [])

    def adb(*parts):
        return subprocess.check_output(command + list(parts), timeout=30)

    def hierarchy():
        adb("shell", "uiautomator", "dump", "/sdcard/dose-editor-ime.xml")
        raw = adb("shell", "cat", "/sdcard/dose-editor-ime.xml")
        return ET.fromstring(raw), raw

    def bounds(node):
        return tuple(map(int, re.findall(r"\d+", node.get("bounds", ""))))

    def find(root, attribute, value):
        return next((n for n in root.iter("node") if n.get(attribute) == value), None)

    def tap(node):
        assert node is not None, "Expected control is missing"
        left, top, right, bottom = bounds(node)
        assert right > left and bottom > top, "Expected control is not visible"
        adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))

    def tap_text(value):
        tap(find(hierarchy()[0], "text", value))

    def keyboard_shown():
        return "mInputShown=true" in adb("shell", "dumpsys", "input_method").decode()

    def card_bounds(root):
        # The smallest common ancestor of the close action and footer is the card,
        # independently of whether the underlying Dialog window fills the display.
        candidates = []
        for node in root.iter("node"):
            if (find(node, "content-desc", "Close medication editor") is not None
                    and any(find(node, "text", label) is not None for label in ("Continue", "Save"))):
                left, top, right, bottom = bounds(node)
                candidates.append(((right - left) * (bottom - top), bounds(node)))
        assert candidates, "Medication editor card is missing"
        return min(candidates)[1]

    def card_height(root):
        _, top, _, bottom = card_bounds(root)
        return bottom - top

    def swipe(forward):
        left, top, right, bottom = card_bounds(hierarchy()[0])
        start, end = (0.8, 0.2) if forward else (0.2, 0.8)
        y = str((top + bottom) // 2)
        adb("shell", "input", "swipe", str(int(left + (right - left) * start)), y,
            str(int(left + (right - left) * end)), y, "400")

    def assert_step(number):
        assert any(n.get("text", "").startswith(f"Step {number} of ")
                   for n in hierarchy()[0].iter("node")), f"Expected step {number}"

    def snapshot(label):
        root, raw = hierarchy()
        (output / (label + ".xml")).write_bytes(raw)
        (output / (label + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))
        height = card_height(root)
        print(f"{label}: card height {height}px", flush=True)
        return root, height

    def restored(label, baseline):
        # Wait for the real IME hide/layout, rather than assuming an animation duration.
        for _ in range(6):
            root, _ = hierarchy()
            if not keyboard_shown() and abs(card_height(root) - baseline) <= 4:
                snapshot(label)
                return
            time.sleep(0.2)
        snapshot(label + "-failed")
        raise AssertionError(f"{label}: height {card_height(root)}px did not recover to {baseline}px")

    def focus_field(value=None):
        tap(find(hierarchy()[0], "class", "android.widget.EditText"))
        if value is not None:
            adb("shell", "input", "text", value)

    root, _ = hierarchy()
    assert find(root, "content-desc", "Close medication editor") is None, "Close any existing draft first"
    assert not keyboard_shown(), "Close the keyboard before starting"
    tap(find(root, "content-desc", "Add medication"))
    try:
        hierarchy()
        # Android can restore IME focus when opening/recreating the dialog. Measure the
        # keyboard-hidden baseline first, including when checking enlarged system text.
        if keyboard_shown():
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
        _, baseline = snapshot("01-before-keyboard")
        assert not keyboard_shown(), "Initial keyboard dismissal did not finish"
        swipe(False)
        assert_step(1)
        swipe(True)
        assert_step(1)
        assert any(n.get("text") == "Enter a medication name."
                   for n in hierarchy()[0].iter("node")), "Swipe bypassed name validation"
        focus_field("HeightProbe")
        root, reduced = snapshot("02-name-keyboard")
        assert keyboard_shown() and reduced < baseline * 0.9, "Keyboard did not resize the editor"
        footer = find(root, "text", "Continue")
        assert footer is not None and bounds(footer)[3] > bounds(footer)[1], "Footer is hidden"
        tap(footer)
        restored("03-form-restored", baseline)
        swipe(True)
        restored("04-amount-restored", baseline)
        assert_step(3)
        focus_field("1")
        assert keyboard_shown(), "Amount keyboard did not open"
        tap_text("Continue")
        restored("05-schedule-restored", baseline)
        swipe(False)
        assert_step(3)
        focus_field()
        assert keyboard_shown(), "Keyboard did not reopen"
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        restored("06-back-dismiss-restored", baseline)
        tap_text("Continue")
        restored("07-schedule-restored-again", baseline)
        tap_text("Continue")
        restored("08-details-restored", baseline)
        assert find(hierarchy()[0], "text", "Save") is not None, "Final step was not reached"
        swipe(True)
        assert_step(5)
        for number in (4, 3, 2, 1):
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            assert_step(number)
            restored(f"09-hardware-back-step-{number}", baseline)
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        assert find(hierarchy()[0], "content-desc", "Close medication editor") is None
        print(f"PASS: IME recovery, footer, validated swipes and Back. Evidence: {output}", flush=True)
    finally:
        # This check never presses Save or changes an existing medication.
        root, _ = hierarchy()
        close = find(root, "content-desc", "Close medication editor")
        if close is not None:
            tap(close)


if __name__ == "__main__":
    main()
