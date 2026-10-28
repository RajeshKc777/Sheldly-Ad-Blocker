# How Shieldly works

The full story of one tap on the Dashboard and one DNS query, written for
someone who has never worked with networking.

---

## 0. Three words you will meet

| Term | Plain meaning |
|---|---|
| **DNS** | The internet's phone book. Apps ask "what is the IP address of `example.com`?" before connecting. |
| **VPN (here)** | A doorway on the device through which the phone's network traffic passes. Android shows it as "a VPN"; it does **not** mean a faraway server. |
| **TUN interface** | The virtual network card a local VPN creates. |

## 1. What happens when you enable protection

1. You flip the switch. Shieldly asks Android for **VPN consent** (once per install).
2. Android creates a **TUN interface** and hands it to Shieldly. Shieldly configures it with:
   - one internal address (`10.111.222.2/24`),
   - **one route: `10.111.222.1/32`** — a single IP,
   - and tells Android *"this IP (10.111.222.1) is your DNS server"*.
3. A foreground service starts (that is the quiet notification) and a reader thread begins listening on the TUN.

**What "local VPN" means and does not mean.** Android's VPN mechanism lets an app
*process device traffic*; that is all it means here. There is no remote server, no
encryption of your traffic, and no change to which websites you can reach. Shieldly
sees only what passes through its single-route doorway (DNS), not the rest of your
traffic.

## 2. What happens when any app does a DNS lookup

1. The app asks Android for the address of a domain, e.g. `ads.example.com`.
2. Android sends that query to the configured resolver — which is now `10.111.222.1`, inside Shieldly's doorway. **Only because of that /32 route does this one packet enter Shieldly.**
3. The reader thread picks it up, checks it is a DNS packet, and hands it to the worker pool.
4. The **pipeline** parses the question and evaluates the domain name:
   - **Allow rule** (yours) → treated as allowed, even if a blocklist would match.
   - **Block rule** (yours or the bundled list) → answered `NXDOMAIN` — "this name does not exist". The app is told there is no address, so it cannot connect.
   - **No match** → normal resolution.
5. Normal resolution = **cache** first (keyed by name/type/class, TTL-aware), else **forward** to the upstream resolver you chose (Cloudflare / Google / Quad9 / AdGuard) over a socket protected from re-entering the tunnel.
6. The answer is wrapped back into an IP/UDP packet and written to the TUN; Android delivers it to the app.

## 3. Blocking, allowing, failing

| Outcome | What the app sees | What you see |
|---|---|---|
| **Blocked** | "No such address" (`NXDOMAIN`) — like typing a domain that does not exist | +1 in *Blocked*, the domain in *Recent blocked lookups* |
| **Allowed** | The real answer (possibly from cache) | +1 in *Allowed* (Stats) |
| **Upstream unreachable** | `SERVFAIL` — a normal, quick DNS failure, not a hang | *Upstream failures* increments |

Fail-open rule: **an outage must not hang your phone.** Fail-closed claim: if the
rule engine somehow has zero usable rules, Shieldly refuses to start and says so,
instead of silently filtering nothing.

## 4. What happens when DNS resolution fails

- Upstream timeout/network error → `SERVFAIL` reply → the app behaves exactly as
  it would with a broken DNS provider: it may retry or show an error.
- Shieldly never fakes a successful answer with an empty payload — that would
  silently break apps.

## 5. How rule updates work

- Your allow/block rules are applied on every rebuild; the engine swaps in a new
  **snapshot** atomically, so a query in flight never sees a half-built rule set.
- Downloaded lists: an HTTPS-only downloader + validator exists and is unit-tested
  (`core/…/BlocklistUpdater.kt`), but the app does not call it yet — the bundled
  list is the only source today. See [BLOCKLISTS.md](BLOCKLISTS.md).

## 6. What happens when protection stops

Toggled off, revoked by another VPN, service killed, or the app uninstalled — in
every case the TUN descriptor is closed and Android's normal resolver setup
returns instantly. Counters reset (they were memory-only anyway). The notification
is removed.

## 7. The four kinds of "ad blocking" (and which one this is)

| Kind | Inspects | Can Shieldly do it? |
|---|---|---|
| **Domain filtering** | The name an app looks up | ✅ This is the entire product |
| Request filtering | Full URLs inside a decrypted proxy stream | ❌ |
| TLS inspection | All HTTPS content via a trusted certificate | ❌ (refused — see [SECURITY.md](SECURITY.md)) |
| Cosmetic filtering | Page/UI structure (e.g. an ad *labelled* in the DOM) | ❌ in general; the experimental **Facebook hider** is a scoped cousin — it hides posts Facebook labels as ads by pressing Facebook's own `Hide Ad` button, in the Facebook feed only |

DNS filtering therefore cannot remove an ad that lives on the same domain as the
content you asked for, and cannot inspect HTTPS URLs. That is the honest boundary
of the technique, not a missing feature to be promised later.

## 8. Apps that are allowed to skip the tunnel

A local VPN is still a VPN as far as Android is concerned, and some apps refuse to run while one is active — banks and payment wallets mostly. Shieldly therefore supports split tunnelling: packages you list under **Settings → Apps that bypass the filter** are added with `Builder.addDisallowedApplication(packageName)` before `establish()`.

For those apps nothing changes: their sockets use the real network, the system resolver answers their lookups, and `NetworkCapabilities` never reports `TRANSPORT_VPN` to them. They also skip the filter entirely, which is the price of compatibility.

Changing the list while protection is running sends `ACTION_RESTART` to the service, which closes the current descriptor and establishes a new one so the change applies immediately.
