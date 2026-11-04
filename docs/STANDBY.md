# StandBy

StandBy turns the phone into a bedside or desk clock while it charges, in the spirit of iPhone StandBy. It is Android's screen saver (`DreamService`), so crDroid starts and stops it: nothing of StandBy runs on battery.

## Setup

StandBy starts by itself (**Start by itself on its side**, on by default): put the phone **on its side on the charger** and it starts when the screen times out. Upright, flat or upside down, the phone just sleeps as usual. Standing it upright while StandBy shows closes StandBy after 3 seconds, and unplugging ends it. The power button still just locks the phone, as on any Android phone.

How it works: StandBy is Android's screen saver. crDroid 17 starts the chosen screen saver when the screen times out while charging, if "When to start" includes "While charging", which is off by default (`PowerManagerService.shouldNapAtBedTimeLocked`). When the screen saver starts, StandBy keeps the screen black for up to 4 seconds while it checks the accelerometer, then either shows itself (landscape) or ends so the phone sleeps.

Android lets an app change the screen saver only with `WRITE_SECURE_SETTINGS`:

- **With the AOD module**, the module grants it and DepthWall makes DepthWall StandBy the screen saver while charging the first time it runs. It does this once; if you later pick another screen saver in Android's settings, that choice stands. The switch in **StandBy settings** turns it back on or off, and turning it off puts back the screen saver DepthWall replaced.
- **Without the module**, grant it once from a computer: `adb shell pm grant dev.srimi.depthwall android.permission.WRITE_SECURE_SETTINGS`, then open DepthWall. Or set it by hand: **StandBy settings → Screen saver**, choose **DepthWall StandBy** and set **When to start** to **While charging**. The on-its-side rule applies either way.

With **Start by itself** off, a screen saver you choose by hand shows StandBy in any orientation, including the portrait nightstand clock, as before. **Home → StandBy** (or **Settings → StandBy settings**) picks the face, night mode, planet and world-clock cities.

**Save & try** and the **StandBy** Quick Settings tile open the same view as a normal activity in any orientation. It keeps the screen on only while the phone is charging. The screen saver's own **Preview** follows the on-its-side rule.

## Using it

| Gesture | Landscape | Portrait |
| --- | --- | --- |
| Swipe sideways | Clock → planet → widgets | Next / previous face |
| Swipe up or down | Next / previous face; on the widgets page, the right half cycles widgets | Next / previous face |
| Tap | Shows the page dots | — |
| Long-press | Leaves StandBy | Leaves StandBy |

The last face you swipe to is remembered. Widgets: month calendar, battery with charging power, next alarm, and a two-city world clock. No widget needs a permission.

StandBy reads orientation from the accelerometer, so it works with auto-rotate off: the window stays portrait and the content turns itself. With auto-rotate on, the window rotates and the content stays upright.

## Clock faces

All faces are shared with the wallpaper clock layer (`ClockFaces`):

| Face | Look |
| --- | --- |
| Digital | Light numerals with the date |
| Minimal | Thin numerals, spaced date |
| Analogue | Ticks, numerals, papaya seconds hand |
| Bold | Hours in ivory, minutes in papaya, as tall as the panel allows |
| Float | Rounded warm numerals that drift a little each minute |
| Solar | The sun on a 06:00–18:00 arc, a crescent moon at night |
| World | Up to four cities with day/night dials and offsets |
| Flip | Split-flap cards; the flap falls in the first 600 ms of each minute |
| Word | "IT IS HALF PAST TEN", leftover minutes as dots |
| Dot matrix | 5 × 7 LED digits |
| Gauge | A McLaren-style tachometer: the needle reads the hour, papaya redline from 10 to 12 |

The planet page draws a DepthWall scene (Earth by default) with a corner clock. Solar uses a stylised day because DepthWall has no location permission.

## AMOLED care (OnePlus 7T Pro)

- True black (`#000000`) behind everything; no full-screen colour fields.
- At most one redraw a second (one a minute for faces without seconds); 16 ms frames only during a flip.
- Everything shifts by up to 8 px (scaled to a 1440 px short edge) around a small circle each minute.
- After 30 minutes without a touch (configurable: never, 10 or 30), content dims to 40 %.
- **Night mode:** Auto switches when the light sensor reads under 3 lux for 10 seconds and leaves above 8 lux, so a passing light does not flicker it. Everything turns deep red (`#B3180F`) and the screen saver drops to its dim brightness. The planet page steps aside at night.
- The planet page renders at 30 fps and 720 px on its short edge, below the wallpaper's quality profile.

Measured frame rate, temperature and power on the phone are not yet recorded.
