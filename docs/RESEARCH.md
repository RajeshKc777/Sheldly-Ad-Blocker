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
| Accessibility overlay cover | Yes for Katana | Covers are in screen space, must be re-drawn after scroll, and leave a visible block | **Rejected (removed 2026-10-26)** |
| `ACTION_SCROLL_FORWARD` to skip the post | Probably | Moves the user's reading position; can trigger more feed loading; jarring on fast scroll | Rejected |
| Auto-click Facebook's "Hide Ad" | Yes — it is a direct one-click button on the tested build | Performs an action inside another app and reports a preference to Facebook; brittle | **Chosen (2026-10-26)** |
| Screenshot + OCR | Would work on Lite | Needs screen capture, processes post content, slow, battery-heavy | Rejected |
| Modify Facebook's views | No | Not possible across processes | Rejected |

## Chosen method

> **Update (2026-10-26).** The tested build exposes a direct, one-click
> `Hide Ad` button in a sponsored post's header — it sits next to "More
> options", is `clickable=true`, and opens no menu. The decision therefore
> changed: the service presses that button, and nothing is drawn over the feed.
> The press is a real action inside Facebook, so it is opt-in, disclosed, and
> limited to one press per post; the label and control names stay in the
> replaceable rules file. Covers were removed because they leave a visible
> block, and the requirement is that the screen look untouched.

- **Detection**: walk the accessibility tree, match the first segment of a
  short header label against a replaceable label list, then walk up to the
  nearest ancestor that is still one post, stopping at `RecyclerView`.
- **Hiding**: press the `Hide Ad` button inside that post (Facebook removes the
  post and reflows the feed). A post without that button is left alone — no
  overlay, no blank block.
- **Why**: the press leaves no trace and needs no scroll movement. The cost is a
  brief first draw that no accessibility app can pre-empt, and a preference
  signal sent to Facebook by its own control.

## Consequences accepted

- Facebook Lite is effectively unsupported until it exposes text.
- A label change silently disables hiding rather than guessing.
- The counter is per session and approximate.
- Play Store review risk is real; this build is not distributed.

## Open questions for a later phase

- Does Facebook expose the label and the `Hide Ad` control identically on
  tablets and in other locales? Only one device and one locale were tested.
- Could the first drawn frame be avoided entirely without root? No known
  accessibility mechanism does that today.
- Is there a stable view-id resource name for the header that would make
  detection cheaper than a full tree walk? Not exposed in the dump.
