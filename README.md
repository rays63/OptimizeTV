# TV setup

Debloat, de-ad and re-skin two Android TV devices over ADB, with a shared custom home screen.

| Device | Folder | Address |
|---|---|---|
| TCL Google TV (Smart_TV_Pro, Android 12) | [`google-tv/`](google-tv) | `192.168.1.255:5555` |
| NetTV box (NETTV-1000, Android 10, Allwinner, 32-bit ARM, no Google Play Services) | [`nettv/`](nettv) | `192.168.1.255:<port>` |
| Home screen app used by both | [`launcher/`](launcher) | — |

Everything is reversible: each `optimize.ps1` has a matching `undo.ps1`.

## Requirements

- Android SDK platform-tools (`adb`) on `PATH`
- Network debugging turned on in each TV's developer options
- For building the home screen: Android SDK build-tools 36.1.0, platform `android-36.1`, and Android Studio's bundled JDK (paths are in [`launcher/build.sh`](launcher/build.sh))
- For the NetTV boot animation: Python 3 with Pillow, and Windows 10/11 (the video is encoded with Windows' built-in Media Composition API, so no ffmpeg is needed)

Run any `.ps1` with:

```powershell
powershell -ExecutionPolicy Bypass -File <path\to\script.ps1>
```

## Home screen (`launcher/`)

A tvOS-style launcher written in plain Java, built without Gradle:

- Bing photo of the day as the background (cached for offline use; **Next photo** steps back through the last 8 days)
- Big clock with the **Nepali (Bikram Sambat) date** and the English date beside it; numbers can use **Devanagari digits** (११:०६)
- **Weather chip** (icon, temperature, condition, today's high and low); OK opens a **forecast panel** with today's details, morning to night, and the next 3 days (Open-Meteo)
- **Favourites** row using each app's TV banner, ending in an **All apps** tile; hold **OK** on a tile, then **◀ ▶** to move it, then **OK** to finish
- **Home, Apps and Settings tabs** at the top of every screen
- **Apps** tab: every app as banner tiles; hold **OK** or press **Menu** for Open, Hide/Show on home, App info and Uninstall
- **Settings** with a live preview of Home:

  | Category | Options |
  |---|---|
  | Background | Daily photo or drawn scene, photo location |
  | Weather | City, temperature (°C/°F), wind speed (km/h, m/s, mph), rainfall (mm, inches), show on home |
  | Date | Calendar (both, Bikram Sambat, Gregorian), Devanagari digits |
  | Home screen | Your name, app order, hidden apps |
  | Icons & tiles | App names: when selected, always or off |
  | Remote buttons | What the YouTube, Prime Video and TCL Channel buttons open: any app, the recent-apps switcher, or the TV's default |
  | TV settings | Opens Android's own settings |

- Dark chips stay readable on any photo, everything sits inside the 5% TV-safe area, and shared styling lives in [`Ui.java`](launcher/src/tv/local/ytredirect/Ui.java). No blur (it needs Android 12, and the NetTV box runs 10), so it stays smooth on both devices
- Layout modelled on [Summit](https://github.com/exendahal/summit_android_tv_launcher) (MIT); the Bikram Sambat table comes from it. See [`launcher/NOTICES.md`](launcher/NOTICES.md)
- Hides other home-screen apps from the row (on the NetTV box, the stock launcher has to stay enabled in the background)
- **Remote helper** (an accessibility service, `RedirectService`):
  - YouTube button → SmartTube (TCL key `4065`, NetTV key `F11`), changeable in Settings › Remote buttons
  - TCL Prime button → Spotify, changeable
  - TCL Channel button → recent-apps switcher, shown over the current app, that can force-stop apps; changeable
  - Home → this launcher instead of Google TV's (on the TCL, `optimize.ps1` also disables Google TV's launcher so this one is the real home app, and Home switches the helper back on after a reboot)
  - Apps left in the background for 5 minutes are killed (playing media is left alone)

Build:

```bash
cd launcher && ./build.sh      # outputs launcher.apk
```

It's signed with `launcher/debug.keystore`. Updates only install over the existing app if they're signed with the same key, so keep a backup of that file (it's git-ignored).

## Google TV (`google-tv/`)

| File | Purpose |
|---|---|
| [`optimize.ps1`](google-tv/optimize.ps1) | Disables everything in `packages-disabled.txt` (TCL extras, Google feedback/games/screensaver, Prime Video, Play Movies…) and sets animations to 0.5× |
| [`undo.ps1`](google-tv/undo.ps1) | Re-enables those apps, restores stock YouTube, uninstalls the home screen, resets animation speed |
| `packages-disabled.txt` | The package list used by both scripts |
| `screenshots/` | Reference screenshots of the home screen |

## NetTV box (`nettv/`)

| File | Purpose |
|---|---|
| [`optimize.ps1`](nettv/optimize.ps1) | Installs the home screen and apps, uninstalls stock YouTube, disables junk, sets the home screen, enables the remote helper, speeds up animations |
| [`undo.ps1`](nettv/undo.ps1) | Reverses all of the above and makes the NetTV launcher the home screen again |
| `packages-disabled.txt` | Ad screensaver, NetTV weather, factory test apps, print services, etc. |
| `apps/` | APKs installed by `optimize.ps1` (git-ignored, see below) |
| `bootlogo/` | Google TV-style boot screen (see below) |
| `bootlogo-backup/` | The box's original logo, boot video and full bootloader partition image (git-ignored) |
| `screenshots/` | Before/after screenshots |

### Things to know about this box

- **The ADB port isn't 5555** and can change after a reboot. Find it with `adb mdns services`, then pass it in: `optimize.ps1 -s 192.168.1.77:<port>`.
- **The NetTV launcher must stay enabled.** If it's disabled, Live TV fails with error 1417. It stays installed but isn't the home screen, so its ads never show.
- **No Play Store.** The box has no Google Play Services, and adding them needs a modified system image. [Aurora Store](https://f-droid.org/packages/com.aurora.store/) (from F-Droid) downloads apps from Google Play anonymously instead.
- **No Netflix.** The Netflix TV app refuses to run here with error -13, because the box isn't Netflix-certified.
- **Sideloading:** LocalSend and the built-in file manager are allowed to install APKs, so you can send an APK from your phone with LocalSend and open it on the box.
- The firmware is a `userdebug` build, so `adb root` works. The boot-screen scripts rely on that.

### Boot screen (`nettv/bootlogo/`)

The boot has two stages. Both are replaced with the same Google TV wordmark over a glow in Google's colours:

1. **Power-on logo:** `bootlogo.bmp` on the `bootloader` partition (FAT16), a 1280×720 32-bit BMP, overwritten in place.
2. **Boot video:** the box's player prefers `/data/local/boot.mp4` over `/system/media/boot.mp4`, so no system files are changed.

| File | Purpose |
|---|---|
| [`render.py`](nettv/bootlogo/render.py) | Draws one 6-second loop of frames (`frames/`) plus `gtv-bootlogo.bmp` |
| [`make-video.ps1`](nettv/bootlogo/make-video.ps1) | Encodes the frames into `gtv-boot.mp4` (4 s, 720p, H.264) |
| [`apply.ps1`](nettv/bootlogo/apply.ps1) | Switches ADB to root and installs the logo and video on the box |
| [`restore.ps1`](nettv/bootlogo/restore.ps1) | Puts the original NetTV logo and video back from `bootlogo-backup/` |
| `black-boot.mp4`, `black-bootlogo.bmp` | Plain black alternative |

To change the design:

```powershell
python -I nettv\bootlogo\render.py nettv\bootlogo\frames
copy nettv\bootlogo\frames\bootlogo.bmp nettv\bootlogo\gtv-bootlogo.bmp
powershell -ExecutionPolicy Bypass -File nettv\bootlogo\make-video.ps1
powershell -ExecutionPolicy Bypass -File nettv\bootlogo\apply.ps1
```

**Keep the video short.** This box plays the whole video before it lets Android show the home screen. The video starts about 16.6 s into boot and Android is ready at about 20 s, so a 4-second video adds no delay. A 30-second one pushed boot from about 26 s to 50 s.

## Not in git

See [`.gitignore`](.gitignore). These are large, third-party, or private:

- `nettv/apps/`: SmartTube, LocalSend and Stremio were copied from the Google TV with `adb pull` (`adb shell pm path <package>`, then `adb pull` each APK). Aurora Store 4.8.4 comes from F-Droid.
- `nettv/bootlogo-backup/`: the box's original firmware files. **Keep a copy somewhere safe**, because they're the only way to restore the NetTV boot logo.
- `launcher/build/`, `*.apk`, `*.idsig`: build output.
- `launcher/debug.keystore`: the signing key.
- `nettv/bootlogo/frames/`: generated by `render.py`.
