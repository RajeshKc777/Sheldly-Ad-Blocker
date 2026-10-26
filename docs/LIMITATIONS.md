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

Implemented: accessibility service, pure-Kotlin detector, a direct press of
Facebook's own `Hide Ad` button, session counter, disclosure, and label/control
rules kept in a data file rather than in the detector. Changing that file still
requires a rebuild; it is not a runtime setting.

There is **no overlay or cover**: a post that exposes no `Hide Ad` button is left
alone rather than replaced by a visible block. The screen only ever shows what
Facebook itself renders.

Fragile by nature:

- **The ad is still downloaded.** Pressing `Hide Ad` makes Facebook remove the
  post from the feed; nothing is filtered at the network level. The label must
  therefore be "ads hidden on screen", never "ads blocked", and any bandwidth or
  privacy benefit is zero.
- **Detection depends on Facebook's label text.** The tested build exposes
  `Ad•Shared with: Public`. Other builds, accounts, or A/B buckets may use
  `Sponsored`, a different separator, or no text node at all. When that happens
  the feature silently stops hiding instead of guessing.
- **Facebook changes its layout often.** View classes, hierarchy depth, and the
  position of the label change between releases. The detector survives label
  and depth changes because it walks for text, but not the removal of the label
  node. If Facebook renames the `Hide Ad` control, the rules file needs the new
  name; until then the ad stays visible.
- **Facebook Lite is not supported in practice.** Its feed exposed **zero text
  and zero content descriptions** across every dump measured on the test device
  (53–63 nodes, twice on different screens). Text-based detection cannot work
  there. The package is still listed in the accessibility scope, so a future
  Lite version that exposes text would start working without a manifest change.
- **The first frames of a post are drawn before Android reports it.** The
  service reacts on the accessibility callback (fast path) and on a debounced
  full scan, so an ad Facebook has just composited can be visible for a moment
  before it disappears. No accessibility-based hider can act before the view is
  drawn; that would need root-level view hooks or a modified client. The fast
  path exists to make that moment as short as the platform allows: it presses
  Facebook's button from the node that just changed, without waiting for the
  debounce.
- **A preloaded ad can be pressed before it scrolls in.** Facebook binds the next
  post before it is visible, and the service does not require the button to be
  on screen, so such an ad can be removed while it is still below the fold — the
  user never sees it. If the platform rejects that press, the post stays visible
  when it does scroll in; nothing is drawn over it.
- **Pressing `Hide Ad` is an action inside Facebook, not a local hide.** It is
  Facebook's own control, so pressing it removes the post *and* records a
  preference signal on the account. That trade-off is why the feature is opt-in
  and stated in the disclosure. Each button rectangle is pressed at most once;
  if the post survives, it is not pressed again and stays visible until it
  scrolls away.
- **A wrong label match cannot hide a normal post.** Pressing needs a clickable
  `Hide Ad` control inside the matched post. Facebook renders that control only
  on sponsored posts ("Hide post" is a different label and is never matched), so
  a false label match finds no button and the post is left untouched.
- **The counter is approximate.** It counts distinct (label, rectangle) pairs in
  one service session. The same ad reappearing at a different position counts
  again; a re-scan of the same position does not.
- **No persistence.** Counters reset when the service is destroyed.
- **Play Store policy risk.** AccessibilityService use must be disclosed and is
  reviewed; an ad-hider for another app's UI is a high-risk category. This build
  is not published anywhere.

Deliberately not done:

- **Overlay covers.** An opaque block hides the ad but leaves a visible
  rectangle, and the requirement is that the screen look untouched. Covers were
  implemented in an earlier build and removed.
- **Scrolling, liking, opening links, or pressing anything other than `Hide Ad`.**
  The service has one action in its vocabulary. Anything else would change the
  user's position or make decisions inside another app.
- **Screenshot-based or OCR detection.** Requires screen capture, which is a far
  larger permission and would process post content.

## Explicitly not implemented (all phases)

- HTTPS interception, certificate installation, or certificate-pinning bypass.
- Browser with content filtering (Phase 8 of the brief).
- Per-app VPN rules; always-on VPN.
- Any cloud service, account, analytics, or crash reporting.
- Any write path to a third-party service.
