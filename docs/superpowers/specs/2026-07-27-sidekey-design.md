# SideKey — restore the Samsung side-key long-press power menu

**Date:** 2026-07-27
**Status:** Approved design
**Target:** Samsung Galaxy, One UI 8.x / Android 16 (verified on SM-S928U1)

## Problem

On One UI 8.5, long-pressing the side key launches Bixby/the AI assistant instead of the
power menu. Samsung's own Settings screen still stores the user's preference as
"power off menu", but the framework ignores it and forces the AI branch on every boot.
The setting cannot be corrected from the phone's UI.

## Evidence

All findings below were measured on the target device, not inferred.

**Device:** `SM-S928U1` (Galaxy S24 Ultra), Android 16, One UI 8.5,
build `BP4A.251205.006.S928U1UES6DZF2`.

### The key never reaches userspace

```
KEYCODE_POWER(26) down
  → PhoneWindowManager.handleKeyGesture for power key
  → SingleKeyGesture long-press timer (~500 ms)
  → powerLongPress: mLongPressOnPowerBehavior=101 behavior=101
  → BixbyService.startBixbyService(keyPressType=1, longPress=true)   [inside system_server]
  → PhoneWindowManagerExt: "consume powerLongPress"
  → BCL@CoreSvc (BixbyKeyLService) processLongPress
  → START u0 {act=...bixby.onboarding.action.START_PROVISION
              cmp=com.samsung.android.bixby.agent/...ProvisioningActivity}
              from uid 10065 (BAL_ALLOW_PERMISSION)
```

`consume powerLongPress` means the event is swallowed in the policy layer. No app,
`AccessibilityService`, or `VoiceInteractionService` can observe or intercept it.

### The assistant framework is not involved

`secure.assistant` and `secure.voice_interaction_service` were both set to
`com.google.android.googlequicksearchbox/...GsaVoiceInteractionService`, and
`cmd role get-role-holders android.app.role.ASSISTANT` returned Google. The long-press
launched **Bixby** regardless. `BixbyService` is invoked directly inside `system_server`;
`VoiceInteractionManagerService` is never consulted.

### The two settings are desynced

| Key | Store | Value | Read by |
| --- | --- | --- | --- |
| `function_key_config_longpress_selected_item` | Global | `long_press_power_off` | One UI Settings UI |
| `power_button_long_press` | Global | `101` | `PhoneWindowManager` |

AOSP defines `power_button_long_press` values 0–5 (`0` nothing, `1` global actions,
`2` shut off, `3` shut off no confirm, `4` voice assist, `5` assistant). `101` is a
Samsung extension meaning "launch Bixby/AI".

### The override works

```
adb shell settings put global power_button_long_press 1
```

produced, on the very next long-press, with no reboot:

```
powerLongPress: mLongPressOnPowerBehavior=1 behavior=1
  → showGlobalActionsInternal()
  → [SystemUI][SamsungGlobalActions]SamsungGlobalActionsPresenter onStart()
  → mCurrentFocus = Window{2195ba5 u0 Phone options}
```

Zero `startBixbyService` lines. Samsung's `PhoneWindowManagerExt` gates the `101` branch
behind reading this value, so `1` routes into Samsung's own `SamsungGlobalActions`
presenter — the genuine One UI power dialog, with its `SideKeyStrategy`,
`LockdownModeStrategy`, and `FingerprintInDisplayStrategy` intact.

### It survives Settings visits but not reboot

Opening `com.android.settings/.Settings$FunctionKeyLongPressSettingsActivity` and backing
out left the value at `1`. A reboot reset it to `101`.

### The boot race

```
18:52:22.5xx   BOOT_COMPLETED dispatched
18:52:26.273   PUT_ret(/global/power_button_long_press)  callingPackage:android
```

`system_server` (uid 1000) rewrites the value roughly **3.7 seconds after
`BOOT_COMPLETED`**. `function_key_config_longpress_selected_item` was untouched across the
reboot, confirming Samsung boots by ignoring its own stored preference.

A boot receiver that writes immediately will be overwritten.

### Permission is grantable

```
Permission [android.permission.WRITE_SECURE_SETTINGS]
  prot=signature|privileged|development|installer|role
```

The `development` flag makes `pm grant` work for a normal third-party app. Grants persist
across reboots and are revoked only on uninstall.

## Approaches rejected

**Become the default assistant** (`VoiceInteractionService` + `AccessibilityService`,
`performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)`). Dead: the assist framework is never
consulted, and the key event is consumed in the policy layer.

**Intercept the key.** Dead: `consume powerLongPress` happens before dispatch.

**Block the Bixby activity launch.** Dead: the launch comes from Bixby's own uid with
`BAL_ALLOW_PERMISSION`; there is no clean interception point, and blocking it would leave
the long-press doing nothing rather than showing the power menu.

**Write `function_key_config_longpress_selected_item` instead.** Useless alone: it is
already the desired value and the framework ignores it.

## The fix

Hold `WRITE_SECURE_SETTINGS` and keep `power_button_long_press` pinned to the user's
chosen value, re-asserting it after Samsung's boot-time rewrite.

## Setup tiers

| Tier | Requires | Outcome |
| --- | --- | --- |
| A. On-device | Wireless debugging + Shizuku, **no PC** | App uses Shizuku once to `pm grant` itself, then Shizuku is disposable |
| B. PC once | `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` | Same end state, one command |
| C. Root | Magisk | Same; could also patch the framework, but out of scope |
| — | No developer options | **Impossible.** `WRITE_SECURE_SETTINGS` is unreachable, for any app |

Tier A is the primary target. It sidesteps Shizuku's weakness — Shizuku must be restarted
after every reboot unless the device is rooted — by using it exactly once, to bootstrap a
permanent self-grant.

## Architecture

Kotlin, Compose, minSdk 30 (Wireless debugging requires Android 11+), targetSdk 36.
Single module for now; each class below is independently testable.

### `SettingsWriter`

Sealed interface over the write capability.

- `DirectWriter` — app holds `WRITE_SECURE_SETTINGS`; writes via `Settings.Global.putInt`.
- `ShizukuWriter` — bootstrap only; executes `pm grant` through Shizuku's shell binder.
- `UnavailableWriter` — no capability; every write returns a typed failure.

Resolution order: `DirectWriter` if the permission is held, else `ShizukuWriter` if Shizuku
is bound, else `UnavailableWriter`. Pure enough to test on the JVM with a fake
`ContentResolver`.

### `Bootstrapper`

Detects current state (permission held? Shizuku present and bound?), runs the self-grant
through Shizuku, and verifies by re-checking `checkSelfPermission`. Reports a
`BootstrapState` the UI renders directly.

### `KeyBehaviorRepo`

Owns both keys and keeps them in sync so One UI's own Settings screen reflects reality:

- writes `power_button_long_press` (the value the framework reads)
- writes `function_key_config_longpress_selected_item` to the matching string

Exposes the desired behavior as a `Behavior` type:

| Behavior | `power_button_long_press` | `function_key_config_longpress_selected_item` |
| --- | --- | --- |
| Power menu | `1` | `long_press_power_off` |
| Assistant | `5` | (leave) |
| Nothing | `0` | (leave) |
| Samsung AI (restore default) | `101` | (leave) |
| Raw | user-supplied int | (leave) |

`function_key_config_longpress_selected_item` is written only for the Power menu case,
where the correct string (`long_press_power_off`) is known from the device. Samsung's
string vocabulary for the other behaviors has not been observed, so those rows leave it
untouched rather than guess — the framework ignores it either way; syncing it only affects
what One UI's own Settings screen displays.

The raw escape hatch exists because `101` is specific to this device and One UI version.
The UI always displays the currently observed value alongside the chosen one.

### `BootReceiver` and `PinService`

`BootReceiver` does **not** write on receipt. It starts `PinService`, which:

1. Registers a `ContentObserver` on `Settings.Global.getUriFor("power_button_long_press")`.
2. On each change, if the value differs from the desired one, writes the desired one back.
3. Writes once immediately as well, in case the system's PUT already landed.
4. Stops after 60 seconds of stability.

This targets the measured ~3.7 s race precisely instead of guessing a delay, and also
catches rewrites triggered by OTA or by tapping an option on the Side button screen while
the service is resident.

If Samsung's `RestrictedReceiverFilter` drops our `BOOT_COMPLETED`, `PinService` becomes a
user-toggleable persistent foreground service instead. See Risks.

### `SetupActivity`

Compose. One screen, four sections:

1. **Status** — current raw value, desired value, whether they match, permission state.
2. **Setup wizard** — tier detection with the exact next action for the detected tier,
   including a copyable `adb` command for Tier B.
3. **Behavior picker** — the table above.
4. **Fix now** — writes immediately; useful for verifying without a reboot.

## Error handling

Every write returns a result type, never a silent failure. Failure modes surfaced to the
user by name:

- permission not held → show the setup wizard
- Shizuku not bound → link to Shizuku setup
- write succeeded but read-back differs → "the system overrode this", offer `PinService`
- `SecurityException` → surface verbatim; it means the grant was revoked

Silently doing nothing is the worst outcome, because the user has lost their power button
and will not know why.

## Testing

**JVM unit tests** — `SettingsWriter` resolution, `KeyBehaviorRepo` value mapping,
`PinService` re-assert policy (a pure state machine over observed values), `Bootstrapper`
state transitions. Fakes for `ContentResolver` and the Shizuku binder.

**Instrumented tests** — real write and read-back of `power_button_long_press` on a device
with the permission granted; `ContentObserver` fires on external change.

**Manual / scripted device test** — the reboot check, scripted with adb:

```bash
adb shell settings put global power_button_long_press 1
adb reboot && adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 3; done
sleep 30
adb shell settings get global power_button_long_press   # expect 1
```

## Risks

1. **`RestrictedReceiverFilter` may drop our `BOOT_COMPLETED`.** Untested — the boot log
   shows Samsung filtering it for dozens of packages. Mitigation: exempt from battery
   optimization and "Sleeping apps"; fall back to a persistent foreground service. This is
   the first thing to verify during implementation.
2. **`101` is device- and version-specific.** Other Samsung models or One UI versions may
   use a different value. Mitigated by the raw-int field and by always displaying the
   observed value.
3. **Samsung may rewrite at other times** (OTA, tapping the Side button settings screen).
   Covered only while `PinService` is resident.
4. **Play Store policy** on `WRITE_SECURE_SETTINGS` apps is unpredictable. Distribution via
   GitHub Releases / Obtainium / F-Droid.
5. **`pm grant` is revoked on uninstall.** Reinstalling requires redoing setup; documented
   in the app.

## Out of scope

- Double-press remapping (`function_key_config_doublepress_*`). The keys exist and the same
  mechanism would work, but it is not the reported problem.
- Root-specific framework patching.
- Any attempt to intercept the key event itself — proven impossible above.
- Custom power dialog UI. The system dialog is the goal.

## Open questions

None blocking. Risk 1 is an empirical question to answer in the first implementation step,
not a design fork — both branches are specified.
