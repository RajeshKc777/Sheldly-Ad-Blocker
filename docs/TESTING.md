# Testing

Last updated: 2026-10-26

## Automated tests

```
.\gradlew.bat test
```

Requires `JAVA_HOME` to point at a JDK 17+ (Android Studio's JBR is fine).

| Module | Suite | Tests |
|---|---|---|
| `:core` | `RuleParserTest` | 12 |
| `:core` | `DomainMatcherTest` | 10 |
| `:core` | `DomainsTest` | 8 |
| `:core` | `BlocklistUpdaterTest` | 9 |
| `:core` | `FilterEngineTest` | 7 |
| `:core` | `DnsMessageTest` | 10 |
| `:core` | `DnsRecordWalkerTest` | 6 |
| `:core` | `DnsCacheTest` | 5 |
| `:core` | `DnsPipelineTest` | 10 |
| `:core` | `Ipv4UdpTest` | 9 |
| `:core` | `SponsoredDetectorTest` | 18 |
| `:app` | `ProtectionStateTest` | 10 |
| `:app` | `CosmeticStatisticsTest` | 4 |

118 unique tests. `:app` tests run twice (debug and release), so the XML reports
132 executions. Last run: **0 failures**.

Build:

```
.\gradlew.bat assembleDebug
```

Produces `app/build/outputs/apk/debug/app-debug.apk` (~11 MB).

## Continuous integration

`.github/workflows/android-build.yml` runs the exact same suite
(`./gradlew test assembleDebug`) on every push and pull request, uploads the
APK as the **Shieldly-debug-apk** artifact, and publishes it to a GitHub
Release on a `v*.*.*` tag. If CI is red, the suite is failing on a clean
machine — treat that as the ground truth rather than your local cache.

> OneDrive occasionally holds files under `*/build/` open, which makes Gradle
> fail with `Unable to delete directory ...`. `.\gradlew.bat --stop` followed by
> deleting `core/build` and `app/build` clears it. It is a file-lock problem, not
> a build error.

## Phase 8b — Facebook sponsored-post hider

### What was inspected (2026-10-26)

Device: **Redmi Note 8 Pro** (`begonia`), Android 11 / API 30, MIUI, serial
`89s4sgnfrwif99y9`. Emulator: API 36 `sdk_gphone64_x86_64`.

```
adb -s 89s4sgnfrwif99y9 shell uiautomator dump /sdcard/fb.xml
adb -s 89s4sgnfrwif99y9 pull /sdcard/fb.xml
```

Facebook (`com.facebook.katana`) and Facebook Lite (`com.facebook.lite`) are
both installed. Findings:

| Finding | Evidence |
|---|---|
| The ad label is **`Ad`**, not `Sponsored` | Header node text `Ad•Shared with: Public` (`\u2022` bullet). Zero occurrences of the string `Sponsored` in the dump. |
| The label is exposed as **both `text` and `content-desc`** | Same node had identical values in both attributes. |
| The label is a **combined string**, not a standalone word | `Ad•Shared with: Public` — exact-match on `Ad` would fail without splitting on the bullet. |
| A post is **one direct child of the RecyclerView** | RecyclerView had 3 children; the ad post was child 2 at `[0,424][1080,2005]`. |
| Facebook's own **"Hide Ad"** button exists | Node with `content-desc="Hide Ad"` in the post header. Deliberately not used (see LIMITATIONS.md). |
| **Facebook Lite exposes no text at all** | 63 nodes, all `com.facebook.lite`, **0 text, 0 content-desc** after a fully loaded feed. Text-based detection cannot work there. |

The dump encoding is UTF-8 but the shell reader decodes it as Latin-1, so the
bullet shows as `â€¢`. That is a shell artifact, not a Facebook one; the detector
normalises on the real bytes.

### What was verified automatically

- `:core` detector tests cover: normal post (no match), `Ad•…` (match), plain
  `Sponsored`, label split across two child views, full-width look-alike,
  zero-width characters inside the word, Devanagari label, a caption starting
  with "Ad", the word "Advertisement", an implausibly tall label node, two ads,
  duplicate labels in one post, empty tree, zero screen height, unusable rules,
  and container fallback without a RecyclerView.
- The service binds correctly (emulator): `dumpsys accessibility` reports
  `Bound services: Service[label=Shieldly sponsored-post hider … eventTypes=
  [TYPE_WINDOW_STATE_CHANGED, TYPE_WINDOW_CONTENT_CHANGED,
  TYPE_VIEW_SCROLLED]]` and `Crashed services:{}`.
- Enabling it does not crash the process; the rules asset parses (no
  `SentinelCosmetic` warning in logcat).

### What was verified on a physical device (2026-10-26)

The APK has to be installed by hand: MIUI blocks `adb install`
(`INSTALL_FAILED_USER_RESTRICTED`) and `settings put`
(`WRITE_SECURE_SETTINGS` denied to shell). Once installed and enabled, these
results were measured over adb:

| Check | Evidence |
|---|---|
| The ad label is found on a live feed | `Ad•Shared with: Public` at `[165,353][270,392]` inside a post spanning `[0,257][1080,2134]` |
| The whole post is covered | Overlay window `mAttrs={(0,257)(1080x1877) … ty=2032}`, i.e. exactly the post rectangle |
| The counter increments | Shieldly dashboard showed **"Ads hidden on screen 3"** after three distinct posts |
| Covers are removed when leaving Facebook | After `am start` of Shieldly and a 2 s wait, no `ty=2032` window with our geometry remained |
| The cover no longer reads as a badge | Cover is a plain `View`, background `#FFFFFF` in light mode and `#242526` in dark mode |
| A stale cover could block another app | Observed once on the **previous** build: a cover at `(0,633)(1080x1079)` sat over the package installer's INSTALL/CANCEL buttons. Fixed by the watchdog; re-tested after the fix. |

Live scroll tracking has not been measured frame by frame; it is covered by the
manual steps below.

## Manual procedure — Phase 8b on a physical device

1. Install `Shieldly-debug.apk` (pushed to `/sdcard/Download/`), allowing
   "install unknown apps" for the file manager.
2. Open Shieldly → **Settings → Facebook sponsored posts** → toggle on.
3. Read the disclosure, tap **Open Accessibility settings**.
4. Enable **Shieldly sponsored-post hider**. Accept the system warning.
5. Open Facebook, scroll until a post shows the `Ad` label.
   Expected: the post is replaced by a blank block matching the feed background.
6. Return to Shieldly → **Dashboard**. Expected: "Ads hidden on screen"
   increments once per distinct post.
7. Scroll the feed slowly while a covered post is on screen. Expected: the cover
   moves with the post and the ad is not revealed. During a fast fling a
   newly-entering ad can be visible until the next scan (about a second).
8. Open another app (or the home screen) while a cover is showing. Expected:
   the cover disappears within ~0.5 s and never sits over another app.
9. Turn the service off in Accessibility settings. Expected: covers disappear
   immediately (service destroyed) and the counter resets on the next enable.

Verification without looking at the screen:

```
adb shell dumpsys window windows | findstr /i "AccessibilityOverlay sentinel"
adb shell dumpsys accessibility | findstr /i "sentinel"
```

Overlay windows appear as `TYPE_ACCESSIBILITY_OVERLAY` owned by
`com.sentinel.adblock`.

## Manual procedure — DNS layer

Unchanged from Phase 6:

1. Enable protection, accept the VPN consent dialog.
2. `adb shell ping -c 1 doubleclick.net` → expected `Unknown host`.
3. `adb shell ping -c 1 example.com` → expected a reply.
4. Dashboard counters increase; "Recent blocked lookups" lists the domain.

## Known gaps in test coverage

- No instrumented (`androidTest`) tests. The overlay controller and the
  AccessibilityNodeInfo → UiNode adapter are only exercised by hand.
- No test for Facebook Lite, because its tree exposes no text to assert on.
- Battery impact of repeated tree walks is measured by hand, not automated.
