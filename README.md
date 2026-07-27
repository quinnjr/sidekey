# SideKey

Restores the side-key long-press power menu on Samsung Galaxy phones running One UI 8+,
where the AI assistant took it over.

Long-pressing the side key opens Bixby or Gemini instead of Power off / Restart, and the
setting in **Settings → Advanced features → Side button** no longer changes it. SideKey
fixes that and keeps it fixed across reboots.

Verified on **SM-S928U1 (Galaxy S24 Ultra), Android 16, One UI 8.5**.

---

## Why the obvious approaches don't work

Everything below was measured on a real device, not inferred. If you were about to suggest
one of these, the answer is already here.

**You cannot intercept the key.** `PhoneWindowManager` consumes the long-press in the
policy layer, before any app sees it:

```
KEYCODE_POWER(26) down
  → PhoneWindowManager.handleKeyGesture for power key
  → powerLongPress: mLongPressOnPowerBehavior=101 behavior=101
  → BixbyService.startBixbyService(keyPressType=1, longPress=true)   [inside system_server]
  → PhoneWindowManagerExt: "consume powerLongPress"
```

No `AccessibilityService`, no `VoiceInteractionService`, no key listener will ever receive it.

**You cannot become the assistant.** During testing the device's assistant was Google
(`secure.assistant = com.google.android.googlequicksearchbox/...GsaVoiceInteractionService`),
and the long-press still launched **Bixby**. `BixbyService` is called directly inside
`system_server`; `VoiceInteractionManagerService` is never consulted. Registering your app
as the default digital assistant changes nothing.

**Samsung ignores its own setting.** One UI stores two keys, and they disagree:

| Key | Store | Value | Read by |
| --- | --- | --- | --- |
| `function_key_config_longpress_selected_item` | Global | `long_press_power_off` | One UI Settings UI |
| `power_button_long_press` | Global | `101` | `PhoneWindowManager` |

AOSP defines `power_button_long_press` values 0–5. `101` is a Samsung extension meaning
"launch Bixby/AI". The Settings screen writes the first key; the framework reads the second.

## What actually works

Setting the value the framework reads:

```bash
adb shell settings put global power_button_long_press 1
```

That takes effect on the very next long-press, no reboot:

```
powerLongPress: mLongPressOnPowerBehavior=1 behavior=1
  → showGlobalActionsInternal()
  → [SystemUI][SamsungGlobalActions]SamsungGlobalActionsPresenter onStart()
  → mCurrentFocus = Window{... Phone options}
```

You get the genuine One UI power dialog, with Samsung's own side-key, lockdown, and
fingerprint handling intact.

**But it does not survive a reboot.** `system_server` rewrites the value back to `101`
during boot:

```
PUT_ret(/global/power_button_long_press)  callingPackage:android
```

That is what this app is for.

## What SideKey does

Holds `WRITE_SECURE_SETTINGS` and re-asserts your chosen value whenever the system
overwrites it.

It does **not** write on boot and hope. `BOOT_COMPLETED` arrives *before* Samsung's rewrite,
so an immediate write is simply clobbered. Instead a `ContentObserver` watches the setting
and corrects it the moment it changes, then stops once the value has held.

Measured end to end:

```
19:44:45.839  PUT_ret(...) callingPackage:android                 ← Samsung writes 101
19:44:53.902  LOCKED_BOOT_COMPLETED: starting PinService
19:44:53.916  re-asserted observed=101 desired=1 -> Ok            ← corrected 8.1s later
19:49:21.112  BOOT_COMPLETED: starting PinService for desired=1   ← after unlock, idempotent
```

`LOCKED_BOOT_COMPLETED` matters: plain `BOOT_COMPLETED` is only delivered once you unlock,
which in one test was **three minutes** after boot — three minutes of the side key still
opening Bixby. SideKey is direct-boot aware to close that window.

## Requirements

**Developer options must be enabled.** There is no way around this, for any app.
`WRITE_SECURE_SETTINGS` is `signature|privileged|development` — the `development` flag is
the only reason a normal app can hold it at all, and the only way to set that flag is a
shell that already has it. An app that promises this without developer options is lying.

You do **not** need a PC, and you do **not** need root.

## Setup

Pick whichever tier you can manage. All three end in the same place: the app holds the
permission permanently, and nothing else is ever needed.

### Tier A — no PC

1. Enable **Developer options** → **Wireless debugging**
2. Install [Shizuku](https://shizuku.rikka.app/) and start it via Wireless debugging
   (on-device pairing, no computer)
3. Open SideKey → **Grant permission via Shizuku**
4. Shizuku can be uninstalled afterwards

Shizuku is used exactly once, to grant the permission. It does not need to keep running —
which matters, because Shizuku itself dies on every reboot unless you are rooted.

> ⚠️ This tier is implemented but has not yet been exercised against a real Shizuku binder.
> If it fails for you, please [open an issue](https://github.com/quinnjr/sidekey/issues) —
> that is genuinely useful.

### Tier B — one command from a computer

```bash
adb install sidekey.apk
adb shell pm grant io.github.quinnjr.sidekey android.permission.WRITE_SECURE_SETTINGS
```

The grant persists across reboots and app updates. It is revoked only if you uninstall.

### Tier C — rooted

Run the Tier B `pm grant` from a root shell.

## Using it

Open the app and choose what the long-press should do:

| Option | `power_button_long_press` |
| --- | --- |
| Power menu | `1` |
| Assistant | `5` |
| Nothing | `0` |
| Samsung AI (restore default) | `101` |
| Raw value | anything, for devices not yet catalogued |

Press **Fix now**. The status card shows the observed value against the one you asked for,
and names the exact reason if a write fails.

## Device reports

`101` is confirmed to mean "launch Bixby" on exactly one model and one One UI version. Other
Galaxy devices may use a different value, and the app cannot guess it.

**Report my device** builds a prefilled GitHub issue. It collects firmware metadata and the
four relevant settings — and nothing else:

- ❌ no serial, IMEI, `ANDROID_ID`, or advertising ID
- ❌ no accounts, installed apps, or location
- ❌ no free-text field
- ❌ no backend, analytics, or telemetry of any kind

The app shows you the exact payload before anything happens, then hands off to GitHub's own
compose screen, which you submit yourself. Two explicit taps; nothing is ever sent in the
background. Note that the payload includes your CSC sales code, which identifies your
carrier or region, and a GitHub issue is public.

## Building

Requires JDK 17 and Android SDK platform `android-37.0`.

```bash
./gradlew :app:testDebugUnitTest    # 26 JVM tests, no device needed
./gradlew :app:assembleRelease
```

The decision logic sits behind a `GlobalSettingsPort` interface, so `Behavior`,
`KeyBehaviorRepo`, `PinPolicy`, `IssueUrlBuilder`, and `Bootstrapper` are all tested on the
JVM with no emulator and no Robolectric.

Toolchain constraints that are load-bearing, not incidental:

- **AGP 9.x is required.** Current AndroidX needs `compileSdk 37` and AGP 9.1+, and AGP 8.x
  crashes on Gradle 9.6 (`InternalProblems`, removed in 9.6.0).
- **Do not add `org.jetbrains.kotlin.android`.** AGP 9 has built-in Kotlin and fails hard if
  the plugin is applied.

## Limitations

- Tested on one device. Other models may use a different Samsung extension value.
- Uninstalling revokes the permission; reinstalling means redoing setup.
- Will not be on Google Play — `WRITE_SECURE_SETTINGS` apps are not welcome there.
- Between the system's boot write and SideKey's correction there is a window of a few
  seconds where the side key still opens the assistant.

## Design notes

Full engineering write-up, including the raw logs behind every claim above:

- [`docs/superpowers/specs/2026-07-27-sidekey-design.md`](docs/superpowers/specs/2026-07-27-sidekey-design.md)
- [`docs/superpowers/plans/2026-07-27-sidekey.md`](docs/superpowers/plans/2026-07-27-sidekey.md)

## License

MIT
