# Limitations

Last updated: 2026-10-26

This file exists so the app never has to overclaim. It separates "implemented
and verified", "implemented but fragile", and "not implemented".

## DNS filtering (Phase 6)

Implemented and unit-tested:

- local `VpnService` tunnel that routes **one address**, `10.111.222.1/32`, and
  answers DNS on it;
- label-aligned domain matching, user allow/deny rules, `@@` exceptions;
- UDP forwarding to a configurable upstream resolver, with TTL-aware caching;
- NXDOMAIN for blocked names, SERVFAIL on upstream failure, TC=1 for oversized
  replies.

Limits that are inherent to the approach:

- **Only DNS is filtered.** Anything that hard-codes an IP address, or resolves
  through a resolver the app contacts directly, is invisible.
- **DNS-over-HTTPS / DNS-over-TLS bypasses the filter.** Chrome, Firefox, and
  many apps use DoH by default. Private DNS (DoT) in system settings also
  bypasses it. The app does not and will not try to break those.
- **IPv6 DNS is not routed.** Only an IPv4 address is inside the tunnel. IPv6
  lookups take the normal path; the count is shown on the Stats screen as
  "not filtered".
- **No HTTP path filtering.** Two ads on the same host cannot be separated;
  blocking the host blocks the host.
- **CNAME cloaking defeats it.** A tracker on `metrics.example.com` that
  CNAMEs to a blocked domain is not detected, because only the queried name is
  inspected.
- **Blocking a domain can break an app** that uses it for both ads and
  functionality. That is why allow rules exist and why they win.
- **The bundled list is a starter list, not EasyList.** It is small by design
  and documented as such.

Not implemented: per-app rules, scheduled list downloads, Room-backed
statistics, always-on VPN.

## Facebook sponsored-post hider (Phase 8b, experimental)

Implemented: accessibility service, pure-Kotlin detector, overlay covers,
session counter, disclosure, label rules kept in a data file rather than in the
detector. Changing that file still requires a rebuild; it is not a runtime
setting.

Fragile by nature:

- **The ad is still downloaded and rendered.** The cover is drawn over it. The
  label must therefore be "ads hidden on screen", never "ads blocked", and any
  bandwidth or privacy benefit is zero.
- **Detection depends on Facebook's label text.** The tested build exposes
  `Ad•Shared with: Public`. Other builds, accounts, or A/B buckets may use
  `Sponsored`, a different separator, or no text node at all. When that happens
  the feature silently stops hiding instead of guessing.
- **Facebook changes its layout often.** View classes, hierarchy depth, and the
  position of the label change between releases. The detector survives label
  and depth changes because it walks for text, but not the removal of the label
  node.
- **Facebook Lite is not supported in practice.** Its feed exposed **zero text
  and zero content descriptions** across 63 nodes (measured on the test device).
  Text-based detection cannot work there. The package is still listed in the
  accessibility scope, so a future Lite version that exposes text would start
  working without a manifest change.
- **Covers move with the feed, but cannot be instant.** A cover is a window in
  screen coordinates, so the service offsets every cover by the scroll delta as
  the list moves and re-confirms positions after the feed settles. An ad that
  scrolls into view is still visible until the next scan covers it (at most about
  a second during a fast fling). The accessibility tree only exposes a post after
  Android has laid it out; no overlay can cover it before that.
- **A cover hides, it does not remove, and it swallows taps.** Tapping where the
  ad was does nothing instead of opening it. Covers are global windows, so a stale
  cover would sit over another app; a 500 ms watchdog removes every cover as soon
  as the foreground package is no longer Facebook, with a 3 s fallback for the
  case where node access is package-scoped. Before that watchdog existed, a cover
  was observed blocking the package installer; that is the bug it fixes.
- **The cover colour is a user choice, not a guess.** Facebook has its own
  light/dark switch, so a cover derived only from the system theme could sit as
  a dark block in a light feed. Settings → *Cover colour* picks Match system,
  Light, or Dark; the setting recolours covers already on screen.
- **A translucent navigation bar can reveal the bottom strip of a covered post.**
  On MIUI the accessibility tree reports the app window as ending above the real
  display edge, so a cover placed on the reported rectangle stopped 66 px short
  and Facebook content showed dimmed under the bar. Covers for posts that touch
  the reported bottom edge are now extended to the real display height.
- **False positives are possible.** A normal post whose header begins with a
  configured label and is short enough would be covered. The height and
  segmentation guards make this unlikely, not impossible.
- **The counter is approximate.** It counts distinct (label, rectangle) pairs in
  one service session. The same ad reappearing at a different position counts
  again; a re-scan of the same position does not.
- **No persistence.** Counters reset when the service is destroyed.
- **Play Store policy risk.** AccessibilityService use must be disclosed and is
  reviewed; an ad-hider for another app's UI is a high-risk category. This build
  is not published anywhere.

Deliberately not done:

- **Auto-tapping Facebook's "Hide Ad" button.** It would be more effective, but
  it sends a signal to Facebook about the user's advertising preferences and
  performs an action inside another app. Covering is the least invasive method
  that works.
- **Screenshot-based or OCR detection.** Requires screen capture, which is a far
  larger permission and would process post content.

## Explicitly not implemented (all phases)

- HTTPS interception, certificate installation, or certificate-pinning bypass.
- Browser with content filtering (Phase 8 of the brief).
- Per-app VPN rules; always-on VPN.
- Any cloud service, account, analytics, or crash reporting.
- Any write path to a third-party service.
