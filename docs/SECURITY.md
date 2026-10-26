# Security and privacy

Last updated: 2026-10-26

## Threat model

Shieldly (package `com.sentinel.adblock`) is an on-device filter with **no backend, no account, and no
telemetry**. The realistic risks are therefore local:

1. the app itself mishandling what it can see;
2. a compromised build or a malicious update;
3. the user granting a capability (VPN or accessibility) without understanding
   its scope.

Everything below is written to make those three checkable.

## Data inventory

| Data | Where it lives | Lifetime | Leaves the device |
|---|---|---|---|
| DNS counters (queries, blocked, cache hits, failures) | memory (`ProtectionState`) | until protection stops | no |
| Recent blocked domains (last 50) | memory only | until protection stops | no |
| Ads hidden on screen (count) | memory (`CosmeticStatistics`) | until service stops | no |
| User allow/deny rules | `SharedPreferences` | until uninstalled | no |
| Upstream resolver choice | `SharedPreferences` | until uninstalled | no |
| Screen text from Facebook | never persisted, never logged | — | no |

The app has `INTERNET` only because the VPN forwards DNS packets. The cosmetic
service performs no network I/O at all.

## VPN service

- The tunnel routes exactly one address (`10.111.222.1/32`) and only answers
  DNS. No other packet enters the process, so the app cannot read browsing
  traffic, TLS payloads, or other apps' connections.
- Upstream sockets are created with `protect()`, so forwarded DNS does not loop
  back into the tunnel.
- No packet contents are logged.
- `allowBypass` is not used. The app's own traffic is excluded with
  `addDisallowedApplication(packageName)` so the filter never sees itself.
- Always-on VPN is explicitly declared unsupported (`SUPPORTS_ALWAYS_ON=false`)
  rather than shipped half-working.

## Accessibility service (experimental)

This is the largest capability in the app and it is **off by default**.

### Scope

`app/src/main/res/xml/accessibility_service_config.xml` limits the service to:

- `com.facebook.katana`
- `com.facebook.lite`

The system does not deliver events from any other package. The service also
re-checks `event.packageName` and `rootInActiveWindow.packageName` before acting,
so a widened manifest cannot silently widen behaviour without a code change.

### Capabilities requested

- `canRetrieveWindowContent="true"` — required; the ad label is text.
- `typeWindowStateChanged`, `typeWindowContentChanged`, `typeViewScrolled` — the
  minimum needed to know when the feed changed or moved.

Not requested: `canPerformGestures`, `canRequestFilterKeyEvents`,
`canTakeScreenshot`, `canRequestTouchExplorationMode`, `flagRetrieveInteractiveWindows`,
or any text-selection capability. The app cannot tap, scroll, type, or capture
the screen.

### What it does with what it sees

- Converts the tree to `UiNode` values **in memory**, for the duration of one
  scan.
- Reads text only to compare the first segment against a small label set from
  `assets/cosmetic/facebook_rules.txt`.
- Discards the tree immediately after the scan.
- Stores only a counter and a dedupe key made of the matched label plus the
  rectangle (`CosmeticStatistics`). Neither contains post text, author names, or
  URLs.
- Logs only a configuration warning when the rules file is unusable. No node
  text is ever written to logcat.

### Overlay window

Covers use `TYPE_ACCESSIBILITY_OVERLAY`, the only window type an
AccessibilityService may add without the separate "draw over other apps"
permission. They are opaque, non-focusable, and deliberately **not**
touch-transparent, so a tap on a hidden post does not open the ad.

Because they are global windows, they are removed in three situations:

- the service is destroyed or interrupted;
- a 500 ms watchdog sees a foreground package other than Facebook — it reads
  only `rootInActiveWindow.packageName`, never that window's content;
- the watchdog cannot read a root at all for 3 s while Facebook was the last
  known package (a package-scoped device).

The watchdog exists because an earlier build left a cover floating over the
package installer. That bug is documented in LIMITATIONS.md and TESTING.md.

### Residual risk

- A user who enables the service grants read access to everything Facebook
  renders. Android offers no narrower grant. The disclosure screen says this in
  plain language before sending the user to settings.
- A future malicious build could exfiltrate that text. Nothing in the current
  code does, and the app has no network code outside the DNS forwarder, but a
  reviewer should verify that on every release.
- Play Store policy treats accessibility-based UI modification as high risk.
  This build is therefore distributed only as a debug-signed APK from
  [GitHub Releases](https://github.com/RajeshKc777/Sheldly-Ad-Blocker/releases/latest).
  Re-verify this entire checklist before every release.

## Deliberate refusals

These are not "not yet implemented"; they are out of scope by design:

- No silent certificate installation, no custom CA, no HTTPS interception.
- No certificate-pinning bypass of any kind.
- No attempt to defeat another app's security controls.
- No rooted-device requirements or root helpers.
- No write path to any third-party service or data store.

## Reviewer checklist

1. `AndroidManifest.xml` — only `INTERNET`, `FOREGROUND_SERVICE`,
   `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, plus the
   accessibility binding permission on the service.
2. `accessibility_service_config.xml` — package list is exactly the two Facebook
   packages; no gesture or screenshot capability.
3. `FacebookAdHiderService` — no `Log.*` of node text; no network calls.
4. `CosmeticStatistics` — keys contain a label and a rectangle only.
5. `AdBlockVpnService` — routes one address; `protect()` on upstream sockets.
6. `gradle/libs.versions.toml` — no analytics, crash-reporting, or ad SDKs.

## Reporting a vulnerability

Use [GitHub Security Advisories → "Report a vulnerability"](https://github.com/RajeshKc777/Sheldly-Ad-Blocker/security/advisories/new)
rather than a public issue. Include the Android version, the affected component
(VPN service, cosmetic service, UI), and steps or a patch. Please do not attach
keystores, passwords, or personal data to the report.

There is no paid bounty and no guaranteed response time — this is a small
open-source project — but reports are read and fixes are prioritised by severity.
