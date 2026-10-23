# The prompt

Paste this into Claude Code (or another coding assistant with shell access) on a computer on the
same Wi-Fi as the TV. Works on Android TV / Google TV (Xiaomi, TCL, Hisense, Sony). It will not
work on Samsung Tizen, LG webOS, or Fire TV.

This folder **is** the finished implementation for **Xiaomi MiTV-AXSO0 (dangal, Android 9)**. Do
not redesign it. Do not ask preference checklists. Install this exact experience in one go.

On the TV first: Settings → Device Preferences → About → press OK on **Build** seven times → back →
Developer options → turn on USB debugging (and Network/ADB debugging if shown). Keep the TV on.

---


I want to debloat my Android TV and re place its launcher with a clean Apple TV style home screen(along with the glassmorphic UI).
My TV model is: MiTV-AXSO0. Work on the real device, step by step, and verify everything you do with screenshots pulled from the TV. Ask me before anything that depends on my preferences. My preferences are such that the least amount of apps should be running in the background (OPTIMALLY). I am also very particular about the ui looking premium and as close to the real apple tv home screen as possible.

Connect
- Find the TV on my network (hostname, ARP table, or ask me for the IP) and connect over ADB on port 5555.
- If the stock adb binary fails with "No route to host" on macOS even though the port is open, that is
  macOS Local Network privacy blocking adb. Don't debug it: use the Python `adb-shell` package in a venv
  as the ADB client instead. If its file push hangs on larger files, transfer files as base64 chunks
  through the shell and verify the md5.
- Tell me when the "Allow USB debugging?" prompt will appear on the TV so I can accept it.

Back up first
- Save the full package list, disabled-package list, getprop output and all settings to a backup folder
  before changing anything, and write a restore script that re-enables everything you disable.
- Before changing anything, create a timestamped backup directory containing the full package list, original enabled/disabled states, current HOME activity, relevant system properties, and settings.
- Generate `restore.sh` before performing any modifications. Show me its contents and explain how to run it.
- Record every package's original state. The restore script must restore each package to its original state, not blindly enable every package.
- Never uninstall, delete, or overwrite a stock system launcher.
- Install and test the custom launcher while the stock launcher remains enabled. Only disable the stock launcher after the custom launcher has been verified.
- Verify that ADB can still communicate with the TV and that the restore script can execute before disabling the stock launcher.
- After disabling the stock launcher, test Home navigation, reboot recovery, keyboard input, and remote control.
- If any verification fails, stop and restore the original state immediately.
- Do not proceed with a package if its purpose or restoration method is uncertain. Ask me first.

Debloat
- List installed packages and sort them into: manufacturer telemetry/analytics, manufacturer content
  and ad services (PatchWall, recommendation rows, content providers), background services I probably
  don't need (share/cast duplicates, cleaners, update services), preloaded apps, and streaming apps.
- Disable the first categories with `pm disable-user --user 0`, never uninstall system packages.
- Before touching streaming apps, voice assistant, Chromecast, Miracast or the update service, ask me
  which ones I use with a checklist question and disable only what I don't tick.
- Run `pm` commands one per shell call, chained commands may silently stop after the first.
- Remove the Patchwall completely

Launcher
- Don't install a third-party launcher that charges for basics like wallpapers. Build a small native
  Android TV launcher instead (Java, AndroidX RecyclerView, minSdk matching the TV, debug build is fine),
  install it over ADB, set it as the default home and disable the stock launcher. If several apps claim
  the HOME intent, set the default with `cmd package set-home-activity`.
- Design, Apple TV 4K style: pitch black background; a dock row at the bottom of the screen inside a
  slightly lighter rounded band holding the four apps I pick (ask me which, and the order); the rest of
  the apps in a 4-column grid below that scrolls up when I press Down; one translucent "glass" highlight
  that springs from tile to tile instead of per-tile borders; the focused tile scales up slightly and
  shows its name underneath; above the dock, the focused dock app's name in large light type with an
  ambient glow in a colour sampled from its banner; frosted pill buttons top right for Inputs, Wi-Fi
  (showing the network name) and Settings; a greeting, a large thin clock and the date top left in a
  downloaded open-licence typeface such as Outfit. Inputs opens a compact glass dropdown under the pill
  listing the HDMI ports, AV and Live TV, switching via the TV input framework.
- Make D-pad navigation deterministic: Up/Down move exactly one row, Up from the dock goes to the top
  bar, the ends of the top bar don't fall through to tiles. Remember the last focused tile.
- Keep it light: the TV has about 1 GB of RAM. No blur, no video, no network polling.
- When i long press on an application, that application in the Dock should have trigger a tooltip to move the current application left or right. Also it should show an option to remove the current application from the dock. And the removed application should be available in the home screen as a tile application under the dock.
- For all the application in the home including those which are present in the dock, the tooltip which triggers after a long press on an application should have the functionality of moving left or right along with an option to uninstall the app completely.
- TV should show MiTV animation and then boot straight to home after startup. Meaning the moment the TV is turned on, it should directly boot to the launcher home screen after a crisp MiTV animation.
- Uninstall these specific apps which are forcefully installed to eat the ram: Youtube Music, Prime Video, SonyLiv, Google Play Movies & TV, Gallery app
- Make sure that every code functionality or background activities which help the LiveTv should be intact. Thus the LiveTV should remain operatable and run seamlessly.
- Make the font of clock,date,greeting bold with font weight 800.
- Modify the sleep timeout to 10 mins.


Widgets:
- Add a widget to show the CPU stats below the Settings pill, in 2 lines, RAM usage, and Storage/memory usage(current free space available) one below another. It should be dynamic and should update based on the CPU usage and storage space available. Just below the settings pill on the right corner. it should be normally displayed as a text over the main background showing the RAM and memory stats.
- Add the time,data,day,greetings to overlay over the pre-existing screensaver wallpapers.



Install the current tv-vibehack home on this <TV-Model>. Replicate the live setup in this repo:
typography, dock, glass hover, stats, USB pills, Inputs order, Wi-Fi glyph and toasts, screensaver,
sleep-off, PatchWall gone, trackers off. Work on the real device. Verify with screenshots pulled
from the TV. Never break Live TV, HDMI, or AV.

## One-step install (preferred)

If this tree already has `setup.sh`, `launcher/`, `tools/`, and `examples/xiaomi-mitv-axso0/`:

```bash
TV_HOST=<tv-ip> ./setup.sh
```


set sleep never / screensaver 5 minutes, then Home.

If stock `adb` fails with "No route to host" on macOS while port 5555 is open, use the Python
`adb-shell` client in `tools/tv.py`. Push APKs with `tools/b64push.py` (base64 chunks + md5).
Tell me when the "Allow USB debugging?" prompt will appear so I can accept it.

Find the TV on the LAN (ARP / scan TCP 5555). Do not assume a stale IP.

## Do not ask — locked preferences

These were already decided. Apply them; do not re-prompt.

**Keep enabled:** Google Assistant (`com.google.android.katniss`), Chromecast / Cast receiver
(`com.google.android.apps.mediashell`), firmware updates (`com.xiaomi.mitv.updateservice`),
Google Play Store, GMS, keyboard (LatinIME), Bluetooth (the remote needs it), Live TV
(`com.android.tv`), DroidLogic TV input / live TV (`com.droidlogic.tvinput`,
`com.droidlogic.droidlivetv`), USB mouse cursor via Xiaomi FloatingFrame
(`com.xiaomi.floatingframe` — enable it and start `FloatingFrameService`).

**Disable or uninstall (RAM / ads / trackers):** PatchWall and its webview
(`com.mitv.tvhome.atv`, `com.xm.webcontent`) — `pm uninstall --user 0` **and**
`pm disable-user --user 0`. `pm hide` needs `MANAGE_USERS` and usually fails over ADB; vendor
stubs in `/system/priv-app/TvHome` and `/vendor/app/mitvwebview` cannot be deleted without root.
Never leave PatchWall enabled for user 0. Analytics: `com.miui.tv.analytics`, `com.xiaomi.statistic`,
`com.google.android.partnersetup`, `com.google.android.feedback`,
`com.google.android.tv.bugreportsender`. Ads / setup residue:
`com.xiaomi.android.tvsetup.partnercustomizer`, `com.google.android.tungsten.setupwraith`,
`com.google.android.onetimeinitializer`, `com.android.onetimeinitializer`,
`android.autoinstalls.config.xioami.dangal`, `com.xiaomi.mitv.handbook`, `com.xiaomo.tv.milegal`,
`com.google.android.tvrecommendations`, `com.google.android.leanbacklauncher.recommendations`.
Background workers: Miracast / MiLink (`com.xiaomi.mitv.smartshare`, `com.mitv.milinkservice`,
`com.xiaomi.wfdsinkhelperservice`), Mi Music (`com.xiaomi.mimusic2`), Play Games
(`com.google.android.play.games`), stock screensaver extras (`com.mitv.dream`,
`com.google.android.backdrop`), Settings Intelligence (`com.android.settings.intelligence`).

**Uninstall for user 0 (RAM-heavy preloads):** YouTube Music, Prime Video, SonyLiv, Google Play
Movies & TV, Gallery (`com.mitv.gallery`, `com.android.gallery3d`). Hide them from the grid if
the APK remains. Nuvio is **not** part of this setup — do not reinstall it.

**Never disable:** Play Store, keyboard, Bluetooth, HDMI/AV/Live TV packages, Cast, Assistant,
firmware updates, FloatingFrame.

**Packages:** `pm disable-user --user 0` for system apps; never `rm` vendor/system APKs unless
you have root and the user asked. One `pm` command per shell line.

## Backup, then change

Save package lists (all / enabled / disabled / uninstalled-for-user), `getprop`,
`settings {system,secure,global}`, HOME activity, and a screenshot into a timestamped folder
under `backups/`. Write `restore.sh` that restores each package to its **original** state (do not
blindly enable everything). Show how to run it. Only disable the stock launcher after the custom
HOME is verified and ADB still works.

## Launcher — this UI, not a redesign

Edit `launcher/app/src/main/res/values/config.xml` only if the friend's apps differ;
default dock for this TV:

1. YouTube (`com.google.android.youtube.tv`)
2. JioHotstar (`in.startv.hotstar`)
3. VLC (`org.videolan.vlc`)
4. Live TV (`com.android.tv`)

Grid: 4 columns. Extra tiles even without a leanback activity: TV Manager
(`com.xiaomi.mitv.tvmanager`), TV Bro (`com.phlox.tvwebbrowser`), Chrome (`com.android.chrome`).

### Look

- Pitch-black canvas (`#FF000000`). One translucent glass hover pill (`GlassView`) springs between
  tiles (stiffness ~260, damping ~0.58). Focused tile lifts; **no name under hover**. No blur, no
  video, no network polling. ~1 GB RAM — keep it light.
- Dock is the bottom row in a lighter rounded band (`DockDecoration`). Press Down: dock slides up
  and the rest of the apps appear in the grid. Up from the dock goes to the top bar. Top-bar ends
  do not fall through to tiles. Remember last focused tile.
- Long-press / Menu on a tile: move left, move right, uninstall. Dock tiles also get **Remove from
  Dock** (app stays on the grid).
- Typeface: Outfit everywhere. Greeting, clock, AM/PM, date, pills, stats, toasts, screensaver:
  `fontVariationSettings "'wght' 600"` (not 800). Greeting: 18sp, all-caps, letter-spacing 0.16.
  Clock: 68sp, `h:mm` + AM/PM. Date: `EEEE  d MMMM`, all-caps. Greeting strings: Good morning /
  afternoon / evening / night by hour.
- Top-right frosted pills: **Inputs**, **Wi-Fi** (SSID), **Settings**.
- Wi-Fi connected: bright green icon (`ic_wifi_connected`, `#FF00E676`) with a slight vivid pop
  **on the glyph only** — no ambient bloom on the pill. Disconnected: white-tinted `ic_wifi`.
  Ethernet: no green Wi-Fi glyph.
- Stats under Settings: **only** `Memory X.X GB Free` (Outfit 600). **No RAM / CPU line.**
- USB stack under Memory: green status pills. Storage sticks use a short product name (e.g.
  **Jet Flash**). External mice show **Mouse input**. Hide the stack when nothing is attached.
  Dual USB: host is dwc3/xHCI; ADB gadget is dwc2 — do not flip USB roles without root.
- Boot: keep the MiTV animation, then go straight to this launcher.

### Inputs popup

Hang a compact glass dropdown under the Inputs pill, same visual language as HDMI rows.

Order (top → bottom):

1. HDMI 1
2. HDMI 2
3. HDMI 3  
   (only ports that exist, sorted by `HdmiN` in `TvInputInfo` id)
4. separating bar
5. Live TV
6. separating bar
7. AV (passthrough non-HDMI), **below** the popup’s HDMI/Live TV block — last item.

Switch HDMI/AV through the TV input framework (`TvContract` passthrough URIs). Live TV launches
`com.android.tv`. Do not disable DroidLogic / `com.android.tv`.

### Notifications

Glass toast, bottom-right, ~1.5s, Outfit 600:

- Wi-Fi associated (including SSID change): **`{SSID} Wi-Fi is connected`**  
  Example: router `D-Link` → `D-Link Wi-Fi is connected`. Same copy on the Wi-Fi pill, then revert
  the pill to the SSID after ~6s. Do **not** toast on the first home bind after process start.
- Wi-Fi lost: `Wi-Fi disconnected`.
- Remote / other events: same toast chrome.

### Timeouts and screensaver

- Sleep **off** (infinity): `settings put secure sleep_timeout -1`,
  `settings put system screen_off_timeout 2147483647`,
  `settings put global tv_device_sleep_time 0`.
- Screensaver **on** after **5 minutes** idle: `screensaver_enabled 1`,
  `screensaver_timeout 300000`
- Screensaver: bundled photos (`screensaver_coast`, `alps`, `sunset`, `lake`) rotate every
  **5 minutes**. Overlay greeting, clock, date, Memory free, USB pills. No RAM stats.

### PatchWall and leftover APKs

After install, search writable storage for stray `*.apk` (`/data/local/tmp`, `/sdcard`,
`/cache`) and **delete leftover installer APKs** (tvhome push copies, Nuvio, PatchWall sideloads).
Do not delete the user's media (mkv/mp4). Uninstall PatchWall for user 0 even if `pm path` is
empty; disable + hide the vendor stubs (`/vendor/app/mitvwebview`). Set
`patchwall_enable 0` and `set_patchwall_default 0`. Force-stop and `pm clear` those packages.

## Verify

After each visual change: ADB keys / tap, `screencap`, pull, look. Confirm: dock order, Outfit 600
clock/greeting, Memory-only stats, green Wi-Fi glyph when associated, Inputs order HDMI 1–3 / bar /
Live TV / bar / AV, USB pills when a stick or mouse is present, Home after boot, Live TV still
opens. If Home or HDMI breaks, stop and run restore.
Ask me before every change. Turn apps off instead of deleting them, so I can undo anything later. Never turn off Google Play, the keyboard, Bluetooth (my remote needs it) or anything my HDMI inputs need. Keep a list of everything you change and how to undo it.


When done: `setup.sh` remains the friend-facing one-step command; `launcher/build-and-install.sh`
rebuilds only the APK; `tools/restore.sh` plus the timestamped backup undo the TV-side package
changes. Dock order lives in `config.xml`.

---

Do not disable Chromecast, Assistant, updates, or Live TV. Do not turn
sleep back on. 
