#!/usr/bin/env python3
"""Exercise Persian RTL UI on a compact Android emulator and save screenshots."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path


PACKAGE = "com.salvia.salviabrowxer"
ACTIVITY = f"{PACKAGE}/.MainActivity"
DISPLAY_WIDTH_PX = 720
DISPLAY_HEIGHT_PX = 1280
BOUNDS_RE = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def adb(*args: str, binary_output: bool = False) -> bytes | str:
    result = subprocess.run(
        ["adb", *args],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return result.stdout if binary_output else result.stdout.decode("utf-8", errors="replace")


def node_labels(node: ET.Element) -> tuple[str, str]:
    return node.attrib.get("text", ""), node.attrib.get("content-desc", "")


def node_bounds(node: ET.Element) -> tuple[int, int, int, int] | None:
    match = BOUNDS_RE.fullmatch(node.attrib.get("bounds", ""))
    return tuple(map(int, match.groups())) if match else None


def dump_hierarchy(output_dir: Path, screen: str) -> ET.Element:
    remote_path = "/sdcard/salvia-ui-hierarchy.xml"
    adb("shell", "uiautomator", "dump", remote_path)
    local_path = output_dir / f"{screen}-hierarchy.xml"
    subprocess.run(
        ["adb", "pull", remote_path, str(local_path)],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return ET.parse(local_path).getroot()


def matching_nodes(root: ET.Element, label: str) -> list[ET.Element]:
    return [
        node
        for node in root.iter("node")
        if any(label in value for value in node_labels(node))
        and node_bounds(node) is not None
        and node.attrib.get("visible-to-user", "true") != "false"
    ]


def require_label(root: ET.Element, label: str, screen: str) -> list[ET.Element]:
    found = matching_nodes(root, label)
    if not found:
        raise AssertionError(f"{screen}: expected visible Persian label {label!r}")
    return found


def verify_visible_bounds(root: ET.Element, screen: str) -> None:
    outside: list[str] = []
    for node in root.iter("node"):
        if node.attrib.get("visible-to-user", "true") == "false":
            continue
        bounds = node_bounds(node)
        if bounds is None:
            continue
        left, top, right, bottom = bounds
        if left < 0 or top < 0 or right > DISPLAY_WIDTH_PX or bottom > DISPLAY_HEIGHT_PX:
            text, description = node_labels(node)
            outside.append(f"{text or description or '<unlabelled>'}: {bounds}")
    if outside:
        raise AssertionError(
            f"{screen}: visible accessibility nodes exceed the {DISPLAY_WIDTH_PX}x"
            f"{DISPLAY_HEIGHT_PX}px display:\n" + "\n".join(outside[:20])
        )


def tap_label(root: ET.Element, label: str, screen: str) -> None:
    candidates = require_label(root, label, screen)
    candidates.sort(key=lambda node: node.attrib.get("clickable") != "true")
    bounds = node_bounds(candidates[0])
    assert bounds is not None
    left, top, right, bottom = bounds
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
    time.sleep(2)


def wait_for_label(output_dir: Path, screen: str, label: str, timeout: float = 25) -> ET.Element:
    deadline = time.monotonic() + timeout
    last_error: AssertionError | None = None
    while time.monotonic() < deadline:
        root = dump_hierarchy(output_dir, screen)
        try:
            require_label(root, label, screen)
            return root
        except AssertionError as error:
            last_error = error
            time.sleep(1)
    raise last_error or AssertionError(f"{screen}: timed out waiting for {label!r}")


def save_screenshot(output_dir: Path, screen: str) -> None:
    screenshot = adb("exec-out", "screencap", "-p", binary_output=True)
    (output_dir / f"{screen}.png").write_bytes(screenshot)


def verify_screen(root: ET.Element, screen: str, expected_labels: tuple[str, ...]) -> None:
    for label in expected_labels:
        require_label(root, label, screen)
    verify_visible_bounds(root, screen)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, default=Path("ui-audit"))
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)

    try:
        # 720x1280 at 320 dpi gives a real compact 360x640 dp viewport.
        adb("shell", "wm", "size", f"{DISPLAY_WIDTH_PX}x{DISPLAY_HEIGHT_PX}")
        adb("shell", "wm", "density", "320")
        adb(
            "shell",
            "cmd",
            "locale",
            "set-app-locales",
            PACKAGE,
            "--user",
            "0",
            "--locales",
            "fa-IR",
        )
        adb("shell", "am", "force-stop", PACKAGE)
        adb("shell", "am", "start", "-W", "-n", ACTIVITY)

        browser = wait_for_label(args.output_dir, "browser-fa-compact", "نوار نشانی و جست‌وجو")
        verify_screen(
            browser,
            "browser-fa-compact",
            ("نوار نشانی و جست‌وجو", "بازگشت", "جلو", "تنظیمات"),
        )
        back = node_bounds(require_label(browser, "بازگشت", "browser-fa-compact")[0])
        forward = node_bounds(require_label(browser, "جلو", "browser-fa-compact")[0])
        assert back is not None and forward is not None
        if (back[0] + back[2]) <= (forward[0] + forward[2]):
            raise AssertionError("Browser back/forward controls are not in RTL order.")
        save_screenshot(args.output_dir, "browser-fa-compact")

        tap_label(browser, "خانه", "browser-fa-compact")
        home = wait_for_label(args.output_dir, "home-fa-compact", "وب‌گردی")
        verify_screen(home, "home-fa-compact", ("وب‌گردی", "چسباندن لینک", "مرورگر"))
        save_screenshot(args.output_dir, "home-fa-compact")

        tap_label(home, "مرورگر", "home-fa-compact")
        browser = wait_for_label(args.output_dir, "browser-return-fa-compact", "نوار نشانی و جست‌وجو")
        tap_label(browser, "تنظیمات", "browser-return-fa-compact")
        settings = wait_for_label(args.output_dir, "settings-fa-compact", "تنظیمات")
        verify_screen(settings, "settings-fa-compact", ("تنظیمات", "زبان"))
        save_screenshot(args.output_dir, "settings-fa-compact")

        tap_label(settings, "بازگشت", "settings-fa-compact")
        browser = wait_for_label(args.output_dir, "browser-again-fa-compact", "نوار نشانی و جست‌وجو")
        tap_label(browser, "فعال", "browser-again-fa-compact")
        downloads = wait_for_label(args.output_dir, "downloads-fa-compact", "دانلودها")
        verify_screen(downloads, "downloads-fa-compact", ("دانلودها", "فعال", "صف"))
        save_screenshot(args.output_dir, "downloads-fa-compact")

        print(
            "Persian compact UI audit passed: 360x640 dp, RTL navigation order, "
            "visible UI bounds, and browser/home/settings/downloads screens."
        )
        return 0
    except (AssertionError, subprocess.CalledProcessError, ET.ParseError) as error:
        print(f"Persian compact UI audit failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
