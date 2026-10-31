# StandBy

DepthWall can become a desk or bedside display when the phone is plugged in and
held horizontally. StandBy is off by default. It is a regular, visible Android
screen / charging screensaver, separate from the ROM's Always-On Display (AOD).

## Set up

1. Open **Preferences → StandBy → Designs & charging setup**.
2. Choose Digital, Flip, Analogue, Calendar or Photo, then enable StandBy.
3. Connect a charger and turn the phone sideways while DepthWall is open.
   StandBy opens immediately after a short orientation debounce. Both landscape
   directions work, including with rotation lock on. Turning upright, laying the
   phone flat, unplugging, Back or Exit ends the session. A fully charged phone
   still counts as plugged in.
4. Swipe left/right for the next/previous design. Tap to reveal controls. Back or
   Exit pauses automatic entry until the app observes an unplug/replug or a turn
   out of landscape and back. The previous DepthWall screen is preserved.

**Preview full screen** works even when StandBy is disabled, unplugged or the
phone is upright. Back returns to StandBy settings. Changes save immediately.

### Outside the app

Use **Set up charging screensaver**, select **DepthWall StandBy** in Android's
screensaver settings, and select **While charging** (wording varies by ROM).
Enable StandBy inside DepthWall too. Leave the charging phone horizontally on
its stand and let the normal screen timeout occur.

Android controls when it starts a screensaver. Pressing Power can skip it;
turning a sleeping phone sideways alone does not guarantee it will start.
Dreams finish if StandBy is disabled, power is disconnected or the phone leaves
landscape. Android's public DreamService API does not expose a preview flag, so
the system's screensaver preview follows these same checks. Use DepthWall's
in-app preview to try designs without a charger or stand.

The screensaver is an interactive `DreamService` protected by
`BIND_DREAM_SERVICE`; it does not launch an activity from the background, request
an overlay permission, change secure system settings or bypass authentication.
Back / Exit returns control to Android and its lock screen.

## Designs and night mode

- **Digital:** large time, local date and battery percentage.
- **Flip:** split clock tiles with a short animation only when the minute changes.
- **Analogue:** minute and hour hands, plus the date. No continuously ticking second hand.
- **Calendar:** time and the current month. The week starts according to the locale;
  this is a date grid, not access to personal calendars.
- **Photo:** an offline static wallpaper behind the clock. Choose bundled art,
  downloaded wallpapers or import a photo. Online images are never fetched by
  StandBy. Missing/corrupt images fall back to the bundled Blue Hour illustration.

Clock accents follow DepthWall's Papaya, Space blue or AMOLED monochrome palette.
Time uses the device's time zone, locale and 12/24-hour setting. The clock updates
on system minute/time changes; analogue hands do not run a frame loop.

Night mode has **Auto / On / Off**. Auto uses local time, **22:00 inclusive to
07:00 exclusive**, and follows time-zone changes. Night mode uses dim red, hides
photos, and reduces window brightness from 20% to 3%. Faces shift by at most 4 dp
in each direction every minute to reduce static OLED exposure; this is not a
guarantee against burn-in. Display overrides apply only to the visible StandBy
window, never system brightness. Lifecycle cleanup removes sensors, receivers,
pending orientation callbacks and brightness/keep-screen-on overrides.

StandBy settings use their own DataStore, so saving a previously opened wallpaper
preference dialog cannot reset a design chosen by a gesture. Wallpaper scene
state, rotation schedules and the Room database schema are unchanged.

## Validation

Build/unit/lint checks use the repository's debug and preview variants. New
tests cover entry/dismissal policy, physical orientation, night boundaries and
time zones, preference isolation, offline fallback and preview design switching.
Use the instrumentation tests `StandbyPreferencesTest` and `StandbyUiTest` for
storage/UI checks.

Physical-device validation remains required on the OnePlus 7T Pro / exact
crDroid build. Check both landscape directions with rotation lock on, full
battery, unplug/upright/flat exit, screensaver selection and timeout, Back/Exit
and keyguard authentication, night-time readability and wake recovery. Record
emulator results separately from phone results. No new AOD support is claimed.
