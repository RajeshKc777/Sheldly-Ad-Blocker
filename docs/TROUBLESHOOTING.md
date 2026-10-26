# Troubleshooting

Every symptom below is something the app can actually show. If yours is not here,
[open an issue](https://github.com/RajeshKc777/Sheldly-Ad-Blocker/issues).

---

## Protection will not start

| Symptom | Cause | Fix |
|---|---|---|
| Banner "Protection is not running: VPN permission was denied or revoked" | You tapped **Cancel** on the consent dialog, or another VPN app took the slot | Toggle protection off and on again; accept the dialog |
| Same banner mentioning "another VPN app" | Android allows **one** VPN at a time; installing/enabling a different VPN revokes this tunnel | Close the other VPN, restart protection |
| Banner "No blocklist rules are loaded" | The bundled asset is missing/corrupted in a broken build | Reinstall from a fresh APK |

## Internet seems broken

| Symptom | Cause | Fix |
|---|---|---|
| Most sites fail, some work | The chosen **upstream resolver** is unreachable on your network (hotspots and some ISP routers block outside DNS) | **Settings → Upstream DNS resolver** → pick another provider → toggle protection off/on |
| Everything fails immediately | A firewall app / DPI box interfering with UDP/53 | Try a different resolver; if it persists, turn protection off (instant recovery) |
| One specific app fails | Over-blocking | **Rules** tab → *Check a domain* with the app's domain → add an **allow** rule → restart protection |

**Emergency escape hatch:** flip protection **off** on the Dashboard (or
uninstall). The tunnel closes at once and the phone uses its normal DNS — no
reboot needed.

## Counters look wrong

| Symptom | Cause | Fix |
|---|---|---|
| "DNS queries" stays 0 | The phone's **Private DNS** (Settings → Network → Private DNS) sends lookups over DoT, bypassing the tunnel | Set Private DNS to **Off**/Opportunistic, or accept those lookups are unfiltered |
| "Blocked" stays 0 on a device full of ads | The bundled list is tiny (49 rules) by design | Expected; real coverage grows when list updating is wired in |
| A browser shows ads anyway | Browsers use **DNS-over-HTTPS** by default; their lookups bypass system DNS | Expected (see [LIMITATIONS.md](LIMITATIONS.md)) |
| "IPv6 DNS packets seen" grows | IPv6 traffic on a dual-stack network | Expected — counted, not filtered |

## The Facebook sponsored-post hider

| Symptom | Cause | Fix |
|---|---|---|
| Toggle does nothing / "Restricted setting" | Android 13+ blocks accessibility for sideloaded apps until allowed | Settings → Apps → Shieldly → ⋮ → **Allow restricted settings** |
| Hider silently stops working | Facebook changed its label or layout | Nothing to fix at runtime; this is the documented fragility |
| An ad stays on screen for a moment before it vanishes | Android draws the post before the accessibility callback reaches Shieldly; the fast path presses `Hide Ad` as soon as the changed node is visible | Expected and bounded; no accessibility app can act before the first frame |
| An ad is never removed | That build shows no `Hide Ad` button, or the control was renamed | Add the new control name as `hide_control=` in `assets/cosmetic/facebook_rules.txt` and rebuild |
| The same advertiser appears again in a later session | Facebook can serve the same advertiser again; hiding a post is not a block | Nothing to fix — the service hides what is on screen |
| Ads in Reels/Stories/Marketplace still show | Out of scope by design (labelled feed posts only) | See [LIMITATIONS.md](LIMITATIONS.md) |
| MIUI kills the hider | Battery optimisation | Autostart **on**, battery saver **No restrictions** for Shieldly |

## Installation problems

| Symptom | Cause | Fix |
|---|---|---|
| "App not installed" | Corrupted download or architecture-less release | Re-download the APK |
| Play Protect warns | Normal for APKs outside Play | Choose **Install anyway** (verify the release came from this repo's Releases page) |
| Update fails over an existing install | Package mismatch | Uninstall the old app first (counters reset; that is by design) |

## For developers

| Symptom | Fix |
|---|---|
| `gradlew` freezes on a synced folder (OneDrive) | `./gradlew --stop`, delete `app/build`/`core/build`, or move the checkout out of the synced folder — see [BUILDING.md](BUILDING.md) |
| Emulator blocks `adb install` (MIUI physical device) | MIUI blocks it by design; install the APK by hand or enable "USB install" in developer options |
| `settings put` fails over adb | `WRITE_SECURE_SETTINGS` is not granted to shell on MIUI; enable the service in system settings by hand |

## Reporting a bug

Include: device + Android version, the exact wording of any banner, the Dashboard
counters, and (if it is a build issue) the full Gradle output. Never paste
keystores, passwords, or personal data into an issue.
