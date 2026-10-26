# Research notes

Last updated: 2026-10-26

This file currently covers **Phase 8b (Facebook sponsored-post hider)**. The
DNS/VPN research that informed Phase 6 lives with the implementation notes in
`REQUIREMENT.md` and the code comments.

## Question

Can an Android app hide posts that Facebook labels as advertising, without
root, without decrypting traffic, and without breaking the app for the user?

## Evidence collected

Device: Redmi Note 8 Pro, Android 11 (API 30), MIUI. Facebook and Facebook Lite
installed from the Play Store.

`uiautomator dump` of the Facebook feed produced a readable tree:

```
class=android.view.ViewGroup
  text='Ad•Shared with: Public'
  desc='Ad•Shared with: Public'
  bounds=[165,520][270,559]
```

Key facts:

1. The label is **`Ad`**, not `Sponsored`. The literal string "Sponsored" did
   not occur anywhere in the dump.
2. The label is **one combined string** with the audience line: splitting on the
   bullet is required to isolate the word.
3. The label is exposed as **both `text` and `content-desc`**, on a small
   `ViewGroup`, not a `TextView`.
4. The post is **one direct child of the RecyclerView**; the ad occupied
   `[0,424][1080,2005]`, bounded by the header at the top and the next item
   below.
5. Facebook exposes its own **"Hide Ad"** action (`content-desc="Hide Ad"`) in
   the post header.
6. **Facebook Lite exposes no text**: 63 nodes, 0 `text`, 0 `content-desc` on a
   loaded feed.

## Options considered

| Method | Works? | Cost / risk | Verdict |
|---|---|---|---|
| Accessibility overlay cover | Yes for Katana | Covers are in screen space, so they must be re-drawn after scroll; ad stays loaded | **Chosen** |
| `ACTION_SCROLL_FORWARD` to skip the post | Probably | Moves the user's reading position; can trigger more feed loading; jarring on fast scroll | Rejected |
| Auto-click Facebook's "Hide Ad" | Probably | Performs an action inside another app and reports a preference to Facebook; brittle | Rejected |
| Screenshot + OCR | Would work on Lite | Needs screen capture, processes post content, slow, battery-heavy | Rejected |
| Modify Facebook's views | No | Not possible across processes | Rejected |

## Chosen method

- **Detection**: walk the accessibility tree, match the first segment of a
  short header label against a replaceable label list, then walk up to the
  nearest ancestor that is still one post, stopping at `RecyclerView`.
- **Hiding**: draw an opaque `TYPE_ACCESSIBILITY_OVERLAY` window over that
  rectangle. Non-focusable, touch-absorbing, removed when the service stops.
- **Why**: it is local, reversible, sends nothing to Facebook, and does not
  move the user's scroll position. The visible cost is that the covered post
  becomes a blank block, and that covers must be cleared while the feed scrolls.

## Consequences accepted

- Facebook Lite is effectively unsupported until it exposes text.
- A label change silently disables hiding rather than guessing.
- The counter is per session and approximate.
- Play Store review risk is real; this build is not distributed.

## Open questions for a later phase

- Does Facebook expose the label identically on tablets and in other locales?
  Only one device and one locale were tested.
- Would `TYPE_ACCESSIBILITY_OVERLAY` covers survive Facebook's own `FLAG_SECURE`
  on future versions? Not applicable today.
- Is there a stable view-id resource name for the header that would make
  detection cheaper than a full tree walk? Not exposed in the dump.
