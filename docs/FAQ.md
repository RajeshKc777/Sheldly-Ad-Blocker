# FAQ

Short answers; each links to the detailed explanation.

---

## Is this a VPN? Do you route my traffic through a server?

No server exists. "VPN" here is only Android's mechanism for letting an app
process device traffic on a virtual network interface. Shieldly takes **only
your DNS lookups** through that doorway (one route, `10.111.222.1/32`) and
answers or forwards them. Everything else your phone sends goes directly to the
internet, untouched. → [HOW_IT_WORKS.md](HOW_IT_WORKS.md)

## What does Shieldly see about my browsing?

Only the **domain names** of DNS lookups that pass through the tunnel while
protection is on — and those are processed in memory, counted, and discarded.
No browsing history, no content, no metadata is stored or transmitted. The
app's only network conversation is with the DNS resolver you chose. →
[SECURITY.md](SECURITY.md)

## Does it block ads in YouTube / Instagram / games?

Mostly no. Those services serve ads from the same domains as their content or
from per-app CDNs — DNS cannot separate them. What DNS blocking *does* stop is
any app talking to a known ad/tracker **domain** in the bundled list. →
[LIMITATIONS.md](LIMITATIONS.md)

## What about HTTPS? Can the filter see inside encrypted traffic?

No, and it never will: Shieldly installs no certificates and performs no TLS
inspection. HTTPS hides content from the network; the DNS *name* is the only
part visible, and that is the exact lever Shieldly uses. →
[README: HTTP, HTTPS and TLS](../README.md#-http-https-and-tls-explained)

## Why is the blocklist so small?

49 rules, on purpose: the starter list proves the pipeline with near-zero
false positives. An HTTPS-only updater with validation already exists and is
unit-tested; wiring it into the UI is the top roadmap item. →
[BLOCKLISTS.md](BLOCKLISTS.md)

## Will it slow my phone or drain the battery?

The tunnel only receives DNS-sized packets (a few hundred bytes each) and does
one hash lookup per query. Your other traffic does not pass through the
process. Formal cross-device battery measurements have not been published —
that is stated rather than claimed. → [README: Performance](../README.md#-performance-and-battery)

## Is it on the Play Store?

No. The accessibility-based Facebook hider would face a high-risk review, and
distribution is currently via GitHub Releases APKs. Play policy considerations
are documented honestly in [LIMITATIONS.md](LIMITATIONS.md).

## Why does Android call the app "unsafe" when I install it?

Because it is installed outside the Play Store. Sideloaded APKs get the
"unknown apps" warning and, on Android 13+, the accessibility toggle needs
**Allow restricted settings** once. Only install APKs from this repo's official
Releases page. → [GETTING_STARTED.md](GETTING_STARTED.md)

## Facebook hider: is my screen content logged?

No. The service reads the accessibility tree to find posts Facebook itself
labels as ads, then presses Facebook's own `Hide Ad` button so Facebook removes
the post. Nothing is drawn over the feed. If that button is missing, the post is
left alone. Screen text is never logged, stored, or transmitted; the only
persisted number is "how many posts were hidden". It is off by default and
scoped to the two Facebook apps. → [LIMITATIONS.md](LIMITATIONS.md)

## If something breaks, how do I get the internet back instantly?

Turn protection **off** on the Dashboard, or uninstall the app. The tunnel
closes at once and Android's normal resolver returns. No reboot is needed. →
[TROUBLESHOOTING.md](TROUBLESHOOTING.md)

## Can I use it together with another VPN app?

No — Android allows one VPN at a time, and enabling another app's VPN revokes
Shieldly's tunnel (you will see the "protection stopped" banner). →
[TROUBLESHOOTING.md](TROUBLESHOOTING.md)

## Why is the app called Shieldly but the package `com.sentinel.adblock`?

The app was developed under the working name Sentinel; the user-facing name was
changed to Shieldly shortly before release. Package identifiers were kept so
updates install in place. Internal log tags still say `sentinel` — that is
cosmetic, not a different product.

## A banking app says "VPN detected" and refuses to run. What now?

Android reports the tunnel to every app, and most banking, wallet and government apps treat "a VPN is active" as a sign of interception. Shieldly is a VPN by design, so they see one.

Open **Settings → Apps that bypass the filter** and tick that app. It is then handed to `VpnService.Builder.addDisallowedApplication()`, so its traffic leaves through your normal connection and it never observes a VPN network.

The honest trade-off: a bypassed app is **not filtered at all**. Its DNS goes to the normal resolver, so its ads and trackers reach it. Bypass is a compatibility switch, not a privacy mode. Turning protection off would also satisfy the bank, but then nothing is blocked for any app.
