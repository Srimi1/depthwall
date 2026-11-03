"""Runs tools/integrity_report.sh against a fake adb and su to check quoting and that it stays read-only."""
import os
import re
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "integrity_report.sh"

# adb -s SERIAL shell CMD: run CMD the way the phone's shell would (one string, parsed again by sh).
FAKE_ADB = """#!/bin/sh
shift 2
[ "$1" = shell ] || exit 1
shift
exec sh -c "$*"
"""
# su -c CMD: record the exact command root would run, without running it.
FAKE_SU = """#!/bin/sh
printf '%s\\n' "$2" >> "$FAKE_LOG/su.log"
"""
FAKE_PIDOF = """#!/bin/sh
[ "$1" = zygote64 ] && echo 4242
exit 0
"""
# Anything that would change the phone. The report must never contain these.
MUTATING = re.compile(r"settings put|setprop|resetprop|pm (grant|revoke|install|uninstall|disable|enable|clear)|"
                      r"cmd overlay (enable|disable|fabricate)|--denylist (add|rm)|\brm\b|\btouch\b|\bmv\b|reboot|>\s*/")


def executable(path: Path, text: str) -> None:
    path.write_text(text)
    path.chmod(path.stat().st_mode | stat.S_IEXEC)


class IntegrityReportTest(unittest.TestCase):
    def run_report(self):
        work = Path(self.enterContext(tempfile.TemporaryDirectory()))
        tools, bin_dir, out = work / "sdk/platform-tools", work / "bin", work / "out"
        tools.mkdir(parents=True)
        bin_dir.mkdir()
        executable(tools / "adb", FAKE_ADB)
        executable(bin_dir / "su", FAKE_SU)
        executable(bin_dir / "pidof", FAKE_PIDOF)
        env = dict(os.environ, ANDROID_HOME=str(work / "sdk"), FAKE_LOG=str(work),
                   PATH=f"{bin_dir}{os.pathsep}{os.environ['PATH']}")
        result = subprocess.run(["sh", str(SCRIPT), "SERIAL", str(out)], env=env, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        return out, (work / "su.log").read_text().splitlines()

    def test_root_commands_reach_su_intact(self):
        _, su = self.run_report()
        # The device shell must not expand $m; su's own shell does.
        self.assertIn("for m in /data/adb/modules/*; do echo $m; ls $m | grep -E disable\\|remove\\|skip_mount; done", su)
        self.assertIn("grep -iE 'depthwall|/data/adb|magisk' /proc/4242/maps | sort -u", su)
        self.assertIn("ls -l /proc/4242/fd | grep -iE 'depthwall|/data/adb|magisk'", su)
        self.assertIn("magisk --denylist status; magisk --denylist ls", su)

    def test_writes_every_report_file(self):
        out, _ = self.run_report()
        for name in ("boot-and-build.txt", "environment.txt", "packages.txt", "overlays.txt", "root.txt", "app-mappings.txt"):
            self.assertTrue((out / name).is_file(), name)
        self.assertIn("=== zygote64 (pid 4242)", (out / "app-mappings.txt").read_text())
        self.assertIn("=== org.digiyatra.org (pid not running)", (out / "app-mappings.txt").read_text())

    def test_never_changes_the_phone_or_reads_the_keybox(self):
        text = SCRIPT.read_text()
        commands = [line for line in text.splitlines() if not line.lstrip().startswith("#")]
        offending = [line for line in commands if MUTATING.search(line)]
        self.assertEqual(offending, [])
        self.assertNotRegex(text, r"cat [^\n]*keybox")


if __name__ == "__main__":
    unittest.main()
