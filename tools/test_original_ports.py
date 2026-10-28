#!/usr/bin/env python3
"""Collect repeatable original-port evidence on an explicitly selected emulator.

Install DepthWall's debug APK and androidTest APK first. The opt-in helper sets a
neutral static wallpaper on BOTH destinations and does not preserve the previous
wallpapers. Cold mode additionally force-stops the original ports and picker as a
diagnostic fixture. Warm mode resets only before the first scene and preserves
port processes/wallpaper state between subsequent scene switches. Neither mode
modifies/re-signs the original APKs or claims the fixture is an app-side fix.

Examples (run outside an iCloud output directory):
  python3 tools/test_original_ports.py --serial emulator-5554 --output /tmp/ports-cold
  python3 tools/test_original_ports.py --serial emulator-5554 --output /tmp/ports-warm \
      --mode warm --pack pack-1

Fixed elapsed samples, exact home-component checks and raw diagnostics are
recorded. Bright pixels never count as a rendering pass: visually inspect all
preview/home captures and correlate crashes before assigning a result.
"""

import argparse
import hashlib
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path


PICKER = "com.android.wallpaper.livepicker"
SETUP_CLASS = "dev.srimi.depthwall.PortHarnessSetupTest#resetToNeutralWallpaper"
RUNNER = "dev.srimi.depthwall.test/androidx.test.runner.AndroidJUnitRunner"


def write_json(path, value):
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n")
    temporary.replace(path)


def canonical_component(value):
    if not value or "/" not in value:
        return value
    package, activity = value.split("/", 1)
    return package + "/" + (package + activity if activity.startswith(".") else activity)


def home_component(wallpaper_dump):
    """Read only the active system wallpaper block, never a global substring."""
    marker = "System wallpaper state:"
    if marker not in wallpaper_dump:
        return None
    block = wallpaper_dump.split(marker, 1)[1]
    block = re.split(r"(?:Lock|Fallback) wallpaper state:", block, maxsplit=1)[0]
    matches = re.findall(r"mWallpaperComponent=ComponentInfo\{([^}]+)\}", block)
    return canonical_component(matches[0]) if len(matches) == 1 else None


class Harness:
    def __init__(self, args):
        self.args = args
        self.base = [args.adb, "-s", args.serial]

    def run(self, *command, timeout=30, check=True):
        result = subprocess.run(self.base + list(command), capture_output=True, timeout=timeout)
        if check and result.returncode:
            raise RuntimeError("adb " + " ".join(command) + ": " +
                               result.stderr.decode(errors="replace").strip())
        return result

    def text(self, *command, **kwargs):
        return self.run(*command, **kwargs).stdout.decode(errors="replace")

    def verify_emulator(self):
        if not self.args.serial.startswith("emulator-"):
            raise RuntimeError("Only an explicit emulator-NNNN serial is accepted")
        if self.text("shell", "getprop", "ro.kernel.qemu").strip() != "1":
            raise RuntimeError("Selected device is not reporting an Android emulator")
        if self.text("shell", "getprop", "sys.boot_completed").strip() != "1":
            raise RuntimeError("Emulator has not completed boot")
        return {key: self.text("shell", "getprop", key).strip() for key in (
            "ro.build.fingerprint", "ro.build.version.sdk", "ro.product.cpu.abilist",
            "ro.product.model", "ro.hardware", "ro.kernel.qemu")}

    def neutral(self, folder):
        result = self.run("shell", "am", "instrument", "-w", "-r", "-e", "class",
                          SETUP_CLASS, "-e", "portHarness", "true", self.args.runner,
                          timeout=60, check=False)
        text = (result.stdout + result.stderr).decode(errors="replace")
        (folder / "neutral-setup.txt").write_text(text)
        if result.returncode or "OK (1 test)" not in text or "FAILURES!!!" in text:
            raise RuntimeError("Neutral fixture failed; install matching debug and androidTest APKs. "
                               "See neutral-setup.txt")
        state = self.text("shell", "dumpsys", "wallpaper")
        (folder / "neutral-wallpaper.txt").write_text(state)
        current = home_component(state)
        if current is None or current.startswith("com.miui.miwallpaper."):
            raise RuntimeError("Neutral system wallpaper could not be verified")

    def xml(self):
        self.run("shell", "uiautomator", "dump", "/sdcard/depthwall-port-harness.xml", timeout=12)
        return ET.fromstring(self.run("exec-out", "cat",
                                     "/sdcard/depthwall-port-harness.xml", timeout=12).stdout)

    def click(self, label, timeout=25):
        deadline = time.monotonic() + timeout
        last_error = None
        while time.monotonic() < deadline:
            try:
                root = self.xml()
                parents = {child: parent for parent in root.iter() for child in parent}
                for node in root.iter("node"):
                    if node.get("text") != label or node.get("enabled") != "true":
                        continue
                    target = node
                    while target.get("clickable") != "true" and target in parents:
                        target = parents[target]
                    if target.get("clickable") != "true":
                        # Android's chooser ListView handles row touches even when
                        # neither the TextView nor its ancestors expose clickable.
                        if node.get("resource-id") != "android:id/text1" or node.get("package") != PICKER:
                            continue
                        target = node
                    bounds = list(map(int, re.findall(r"\d+", target.get("bounds", ""))))
                    if len(bounds) != 4:
                        continue
                    self.run("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2),
                             str((bounds[1] + bounds[3]) // 2))
                    return
            except (RuntimeError, ET.ParseError, subprocess.TimeoutExpired) as error:
                last_error = str(error)
            time.sleep(.5)
        raise RuntimeError("Picker control did not become available: " + label +
                           ("; " + last_error if last_error else ""))

    def close_picker_normally(self):
        """Warm-mode cleanup uses Back, without killing package processes."""
        for _ in range(3):
            top = self.text("shell", "dumpsys", "activity", "activities")
            resumed = [line for line in top.splitlines() if "topResumedActivity=" in line]
            if not any(PICKER in line for line in resumed):
                break
            self.run("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(.7)

    def sample(self, folder, name, elapsed):
        path = folder / (name + ".png")
        data = self.run("exec-out", "screencap", "-p").stdout
        if not data.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("Screenshot did not return a PNG")
        path.write_bytes(data)
        record = {"file": path.name, "elapsed_seconds": round(elapsed, 2),
                  "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data)}
        try:
            from PIL import Image, ImageStat
            with Image.open(path) as image:
                width, height = image.size
                crop = image.convert("RGB").crop((int(width * .1), int(height * .14),
                                                   int(width * .9), int(height * .75)))
                crop.thumbnail((160, 300))
                stats = ImageStat.Stat(crop)
                pixels = list(crop.getdata())
                record["diagnostic_pixels_only"] = {
                    "mean_rgb": stats.mean, "variance_rgb": stats.var,
                    "bright_fraction": sum(max(pixel) > 16 for pixel in pixels) / len(pixels),
                    "note": "Not a rendering verdict; spinner/dialogs also produce bright pixels"}
        except ImportError:
            pass
        return record

    def capture_at(self, folder, phase, offsets, record):
        started = time.monotonic()
        samples = record.setdefault(phase + "_samples", [])
        for seconds in offsets:
            remaining = started + seconds - time.monotonic()
            while remaining > 0:
                time.sleep(min(remaining, .5))
                remaining = started + seconds - time.monotonic()
            samples.append(self.sample(folder, f"{phase}-{seconds}s", time.monotonic() - started))
            write_json(folder / "result.json", record)

    def diagnostics(self, folder, component):
        errors = []
        commands = {
            "logcat.txt": ("logcat", "-b", "all", "-d", "-v", "threadtime"),
            "crashes.txt": ("logcat", "-b", "crash", "-d", "-v", "threadtime"),
            "wallpaper.txt": ("shell", "dumpsys", "wallpaper"),
            "services.txt": ("shell", "dumpsys", "activity", "services", component.split("/")[0]),
            "engine.txt": ("shell", "dumpsys", "activity", "service", component),
            "activities.txt": ("shell", "dumpsys", "activity", "activities"),
        }
        for name, command in commands.items():
            try:
                result = self.run(*command, check=False)
                (folder / name).write_bytes(result.stdout + result.stderr)
            except Exception as error:
                errors.append(name + ": " + str(error))
        try:
            root = self.xml()
            ET.ElementTree(root).write(folder / "ui.xml", encoding="utf-8", xml_declaration=True)
        except Exception as error:
            errors.append("ui.xml: " + str(error))
        return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--mode", choices=("cold", "warm"), default="cold")
    parser.add_argument("--manifest", type=Path,
                        default=Path(__file__).resolve().parents[1] / "docs/port-inspection.json")
    parser.add_argument("--runner", default=RUNNER)
    parser.add_argument("--pack", action="append", help="Filter by inspection pack ID; may repeat")
    parser.add_argument("--component", action="append", help="Filter by exact service component; may repeat")
    parser.add_argument("--destination", choices=("home", "both"), default="home")
    parser.add_argument("--preview-seconds", type=int, nargs="+", default=[20, 40])
    parser.add_argument("--home-seconds", type=int, nargs="+", default=[5, 15])
    args = parser.parse_args()
    for offsets in (args.preview_seconds, args.home_seconds):
        if offsets != sorted(set(offsets)) or any(value < 1 or value > 60 for value in offsets):
            parser.error("Sample offsets must be increasing unique seconds between 1 and 60")
    args.output.mkdir(parents=True, exist_ok=True)
    if (args.output / "results.json").exists():
        parser.error("Output already contains results.json; choose a fresh directory")
    harness = Harness(args)
    environment = harness.verify_emulator()
    packs = [pack for pack in json.loads(args.manifest.read_text())
             if "linuxct" not in pack["version"].lower()]
    packages = sorted({pack["package"] for pack in packs})
    selected = [(pack, scene) for pack in packs for scene in pack["services"]
                if (not args.pack or pack["pack"] in args.pack)
                and (not args.component or scene["component"] in args.component)]
    if not selected:
        parser.error("No modern scenes matched the filters")
    report = {"environment": environment, "mode": args.mode, "serial": args.serial,
              "requested_scenes": len(selected), "destination": args.destination,
              "rendering_verdict": "Requires human review of preview/home images and crash evidence",
              "fixture": "Neutral static wallpaper on both destinations before every cold scene or first warm scene",
              "scenes": []}
    write_json(args.output / "results.json", report)
    harness.run("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    harness.run("shell", "wm", "dismiss-keyguard")
    failed = 0
    for index, (pack, scene) in enumerate(selected):
        scene_index = pack["services"].index(scene)
        folder = args.output / pack["pack"] / f"scene-{scene_index}"
        folder.mkdir(parents=True, exist_ok=True)
        record = {"pack": pack["pack"], "label": scene["label"], "component": scene["component"],
                  "version": pack["version"], "expected_apk_sha256": pack["sha256"],
                  "mode": args.mode, "artifact_dir": str(folder.relative_to(args.output)),
                  "rendering_verdict": "human_review_required", "apply_confirmed": False}
        interrupted = False
        try:
            if args.mode == "cold" or index == 0:
                harness.neutral(folder)
            if args.mode == "cold":
                for package in packages + [PICKER]:
                    harness.run("shell", "am", "force-stop", package)
            else:
                harness.close_picker_normally()
            harness.run("shell", "input", "keyevent", "KEYCODE_HOME")
            harness.run("logcat", "-b", "all", "-c")
            result = harness.run("shell", "am", "start", "-a",
                                 "android.service.wallpaper.CHANGE_LIVE_WALLPAPER", "--ecn",
                                 "android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT",
                                 scene["component"])
            start = (result.stdout + result.stderr).decode(errors="replace")
            (folder / "start.txt").write_text(start)
            if "Error:" in start or "Exception" in start:
                raise RuntimeError("Wallpaper preview launch failed; see start.txt")
            if "intent has been delivered to currently running" in start:
                record["launch_warning"] = "Existing activity reused; confirm selected scene from capture/service evidence"
            harness.capture_at(folder, "preview", args.preview_seconds, record)
            (folder / "preview-services.txt").write_text(harness.text(
                "shell", "dumpsys", "activity", "services", pack["package"]))
            harness.click("Set wallpaper", timeout=30)
            harness.click("Home screen" if args.destination == "home" else
                          "Home screen and lock screen", timeout=30)
            deadline = time.monotonic() + 25
            observed = None
            while time.monotonic() < deadline:
                state = harness.text("shell", "dumpsys", "wallpaper")
                observed = home_component(state)
                if observed == canonical_component(scene["component"]):
                    record["apply_confirmed"] = True
                    break
                time.sleep(.7)
            record["observed_home_component"] = observed
            harness.run("shell", "input", "keyevent", "KEYCODE_HOME")
            harness.capture_at(folder, "home", args.home_seconds, record)
        except KeyboardInterrupt:
            interrupted = True
            record["error"] = "Interrupted by operator"
        except Exception as error:
            record["error"] = str(error)
            failed += 1
        finally:
            record["diagnostic_capture_errors"] = harness.diagnostics(folder, scene["component"])
            write_json(folder / "result.json", record)
            report["scenes"].append(record)
            write_json(args.output / "results.json", report)
        print(f"{index + 1}/{len(selected)} {pack['pack']} {scene['label']}: "
              f"home binding={record['apply_confirmed']}; "
              f"{record.get('error', 'captures ready for visual review')}", flush=True)
        if interrupted:
            return 130
    print(f"Evidence saved to {args.output}; operational errors: {failed}. "
          "No rendering result is inferred from brightness or binding alone.")
    return 1 if failed else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print("Port harness stopped: " + str(error), file=sys.stderr)
        sys.exit(2)
