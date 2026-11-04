"""Runs tools/integrity_report.sh against a fake adb, su and /proc to check quoting, detection and that it stays read-only."""
import os
import re
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "integrity_report.sh"

# adb -s SERIAL get-state | shell CMD. Shell runs CMD the way the phone does: one string, parsed again by sh.
FAKE_ADB = """#!/bin/sh
shift 2
case "$1" in
  get-state) [ "${FAKE_STATE:-device}" = device ] && echo device && exit 0
             echo "error: device '$FAKE_SERIAL' not found" >&2; exit 1 ;;
  shell) shift; exec sh -c "$*" ;;
esac
exit 1
"""
# su -c CMD: record the exact command root would run and answer from fixtures instead of the real /proc.
FAKE_SU = """#!/bin/sh
printf '%s\\n' "$2" >> "$FAKE_LOG/su.log"
case "$2" in
  'id -u') echo "${FAKE_UID:-0}" ;;
  'cat /proc/'*) f="$FAKE_PROC/$(echo "$2" | cut -d/ -f3)-$(echo "$2" | cut -d/ -f4)"; [ -f "$f" ] && cat "$f" ;;
esac
exit 0
"""
FAKE_PS = """#!/bin/sh
printf 'PID NAME\\n4242 zygote64\\n4243 zygote\\n5001 org.digiyatra.org\\n5002 org.digiyatra.org:rasp\\n5003 org.digiyatra.orgx\\n'
"""
DATA_MOUNTINFO = "29 1 253:5 / /data rw,nosuid,nodev,noatime shared:13 - f2fs /dev/block/dm-5 rw\n"
# fd:05 is /data (253:5); fd:01 is the real product partition.
ZYGOTE_MAPS = """7a00000000-7a00001000 r--p 00000000 fd:05 1234                       /product/overlay/OtherModuleOverlay.apk
7a00002000-7a00003000 r--p 00000000 fd:01 99                         /product/overlay/RomFrameworkOverlay.apk
7a00004000-7a00005000 r--p 00000000 fd:05 77                         /data/resource-cache/product@overlay@RomFrameworkOverlay.apk@idmap
7a00006000-7a00007000 r--p 00000000 fd:05 88                         /system/fonts/Module Font.ttf
7a00008000-7a00009000 rw-p 00000000 00:00 0                          [anon:dalvik-main space]
"""
APP_MAPS = "7b00000000-7b00001000 r--p 00000000 fd:01 99                         /product/overlay/RomFrameworkOverlay.apk\n"
# Anything that would change the phone. The report must never contain these.
MUTATING = re.compile(r"settings put|setprop|resetprop|pm (grant|revoke|install|uninstall|disable|enable|clear)|"
                      r"cmd overlay (enable|disable|fabricate)|--denylist (add|rm)|\brm\b|\btouch\b|\bmv\b|reboot|>\s*/")


def executable(path: Path, text: str) -> None:
    path.write_text(text)
    path.chmod(path.stat().st_mode | stat.S_IEXEC)


class IntegrityReportTest(unittest.TestCase):
    def run_report(self, *packages, **env_overrides):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        work = Path(directory.name)
        tools, bin_dir, proc, out = work / "sdk/platform-tools", work / "bin", work / "proc", work / "out"
        for folder in (tools, bin_dir, proc):
            folder.mkdir(parents=True)
        executable(tools / "adb", FAKE_ADB)
        executable(bin_dir / "su", FAKE_SU)
        executable(bin_dir / "ps", FAKE_PS)
        for pid in ("4242", "4243", "5001", "5002"):
            (proc / f"{pid}-mountinfo").write_text(DATA_MOUNTINFO)
        (proc / "4242-maps").write_text(ZYGOTE_MAPS)
        (proc / "5001-maps").write_text(APP_MAPS)
        (proc / "5002-maps").write_text(APP_MAPS)
        env = dict(os.environ, ANDROID_HOME=str(work / "sdk"), FAKE_LOG=str(work), FAKE_PROC=str(proc),
                   FAKE_SERIAL="SERIAL", PATH=f"{bin_dir}{os.pathsep}{os.environ['PATH']}", **env_overrides)
        result = subprocess.run(["sh", str(SCRIPT), "SERIAL", str(out), *packages], env=env, capture_output=True, text=True)
        log = work / "su.log"
        return result, out, log.read_text().splitlines() if log.exists() else []

    def test_root_commands_reach_su_intact(self):
        result, _, su = self.run_report()
        self.assertEqual(result.returncode, 0, result.stderr)
        # The phone's shell must not expand $m; su's own shell does.
        self.assertIn("for m in /data/adb/modules/*; do echo $m; ls $m | grep -E disable\\|remove\\|skip_mount; done", su)
        self.assertIn("cat /proc/4242/maps", su)
        self.assertIn("ls -l /proc/4242/fd", su)
        self.assertIn("magisk --denylist status; magisk --denylist ls", su)

    def test_flags_system_paths_served_from_data_whatever_their_name(self):
        result, out, _ = self.run_report()
        self.assertEqual(result.returncode, 0, result.stderr)
        report = (out / "app-mappings.txt").read_text()
        zygote = report.split("=== zygote64 pid 4242")[1].split("===")[0]
        self.assertIn("(/data device 253:5)", report)
        self.assertIn("/product/overlay/OtherModuleOverlay.apk", zygote)
        self.assertIn("/system/fonts/Module Font.ttf", zygote)
        # The ROM's own overlay (product device) and real /data files are not module files.
        self.assertNotIn("RomFrameworkOverlay", zygote)

    def test_checks_every_process_of_each_package_and_extra_packages(self):
        result, out, _ = self.run_report("com.example.bank")
        self.assertEqual(result.returncode, 0, result.stderr)
        report = (out / "app-mappings.txt").read_text()
        self.assertIn("=== org.digiyatra.org pid 5001", report)
        self.assertIn("=== org.digiyatra.org pid 5002", report)  # its :rasp subprocess
        self.assertNotIn("pid 5003", report)  # a different package with the same prefix
        self.assertIn("=== com.example.bank: not running", report)
        self.assertIn("=== com.example.bank", (out / "packages.txt").read_text())

    def test_stops_when_adb_cannot_reach_the_phone(self):
        result, out, _ = self.run_report(FAKE_STATE="offline")
        self.assertEqual(result.returncode, 1)
        self.assertIn("adb cannot use SERIAL", result.stderr)
        self.assertFalse(out.exists())

    def test_says_so_when_root_is_unavailable(self):
        result, out, _ = self.run_report(FAKE_UID="2000")
        self.assertEqual(result.returncode, 3)
        self.assertIn("ROOT UNAVAILABLE", (out / "app-mappings.txt").read_text())
        self.assertIn("ROOT UNAVAILABLE", (out / "root.txt").read_text())
        self.assertTrue((out / "boot-and-build.txt").is_file())

    def test_never_changes_the_phone_or_reads_the_keybox(self):
        text = SCRIPT.read_text()
        commands = [line for line in text.splitlines() if not line.lstrip().startswith("#")]
        offending = [line for line in commands if MUTATING.search(line)]
        self.assertEqual(offending, [])
        self.assertNotRegex(text, r"cat [^\n]*keybox")


if __name__ == "__main__":
    unittest.main()
