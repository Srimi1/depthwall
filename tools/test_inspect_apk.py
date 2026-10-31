import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from inspect_apk import summarize_manifest

MANIFEST = '''N: android=http://schemas.android.com/apk/res/android (line=2)
  E: manifest (line=2)
    A: package="com.miui.miwallpaper.earth" (Raw: "com.miui.miwallpaper.earth")
    E: queries (line=4)
      E: intent (line=5)
        E: action (line=6)
          A: http://schemas.android.com/apk/res/android:name(0x01010003)="android.intent.action.VIEW" (Raw: "android.intent.action.VIEW")
    E: application (line=8)
      A: http://schemas.android.com/apk/res/android:directBootAware(0x01010505)=(type 0x12)0x0
      E: service (line=10)
        A: http://schemas.android.com/apk/res/android:name(0x01010003)="com.miui.miwallpaper.earth.superwallpaper.EarthSuperWallpaper" (Raw: "com.miui.miwallpaper.earth.superwallpaper.EarthSuperWallpaper")
        A: http://schemas.android.com/apk/res/android:process(0x01010011)=":earthSuperWallpaper" (Raw: ":earthSuperWallpaper")
        A: http://schemas.android.com/apk/res/android:exported(0x01010010)=(type 0x12)0xffffffff
        E: intent-filter (line=14)
          E: action (line=15)
            A: http://schemas.android.com/apk/res/android:name(0x01010003)="android.service.wallpaper.WallpaperService" (Raw: "android.service.wallpaper.WallpaperService")
      E: receiver (line=18)
        A: http://schemas.android.com/apk/res/android:name(0x01010003)="com.example.Boot" (Raw: "com.example.Boot")
        A: http://schemas.android.com/apk/res/android:directBootAware(0x01010505)=(type 0x12)0xffffffff
        E: intent-filter (line=20)
          E: action (line=21)
            A: http://schemas.android.com/apk/res/android:name(0x01010003)="android.intent.action.BOOT_COMPLETED" (Raw: "android.intent.action.BOOT_COMPLETED")
'''


class ManifestSummaryTest(unittest.TestCase):
    def test_records_reboot_relevant_attributes_and_ignores_query_actions(self):
        components = summarize_manifest(MANIFEST)
        application, service, receiver = components
        self.assertEqual(application['element'], 'application')
        self.assertIs(application['directBootAware'], False)
        self.assertEqual(application['actions'], [])
        self.assertEqual(service['process'], ':earthSuperWallpaper')
        self.assertIs(service['exported'], True)
        self.assertNotIn('directBootAware', service)
        self.assertEqual(service['actions'], ['android.service.wallpaper.WallpaperService'])
        self.assertIs(receiver['directBootAware'], True)
        self.assertEqual(receiver['actions'], ['android.intent.action.BOOT_COMPLETED'])


if __name__ == '__main__':
    unittest.main()
