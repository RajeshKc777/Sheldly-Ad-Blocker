# DNS filtering in depth

The reference for how `core` handles a query. Aimed at contributors; the plain
story is in [HOW_IT_WORKS.md](HOW_IT_WORKS.md).

---

## 1. What a DNS query looks like on the wire

A DNS query is a small UDP packet on port 53. The part Shieldly cares about:

```
┌────────────┬────────────┬───────────────────┬───────────┐
│ IP header  │ UDP header │ DNS message       │           │
│ (20 bytes) │ (8 bytes)  │ id, flags, counts │ questions │
└────────────┴────────────┴───────────────────┴───────────┘
```

- The **IP header** carries source/destination addresses.
- The **UDP header** carries ports (53 for DNS).
- The **DNS message** starts with a 16-bit **transaction ID** and a **question**
  (name, type, class).

`core/net/Ipv4Udp.kt` parses and rebuilds those two headers; `core/dns/DnsMessage.kt`
parses and rebuilds the DNS message. Both are pure byte manipulation, covered by
unit tests with fixed byte arrays (no network involved).

## 2. The pipeline (`DnsPipeline.handle`)

```
parse query            DnsMessage.parseQuery(payload)
   │                    null → drop (client retries; fail-open)
   ▼
evaluate(name)         DomainFilter.evaluate
   │                    allow rule → allowed (skip blocklists)
   │                    deny rule or list rule → blocked
   │                    else → normal
   ▼
blocked? ──yes──► NXDOMAIN reply built from the original query
   no
   ▼
cache.get(key)         key = (name, type, qclass)
   hit ─────────────► patch transaction ID, rewrite TTLs, reply
   miss
   ▼
resolver.query(payload)   protected UDP socket to upstream:53
   │
   ├─ null ──────► SERVFAIL reply (fail-open)
   └─ answer ────► patch ID, min TTL → cache, reply
```

Every branch returns an `Outcome` that feeds exactly one counter — which is why
the Stats screen's numbers are trustworthy.

## 3. Domain matching rules (`filtering/`)

| Input | Meaning | Matched |
|---|---|---|
| `example.com` | plain domain | `example.com` and **all subdomains** |
| `||example.com^` | Adblock syntax | same as above |
| `0.0.0.0 example.com` | hosts syntax | same as above |
| `@@example.com` | exception | allow rule |
| `1.2.3.4` | bare IP | rejected (invalid host name) |
| `##selector` / `$modifier` | cosmetic / modifier | **rejected as unsupported** — counted, not silently ignored |

**Precedence:** allow rules beat deny rules, deny rules beat the bundled list.
Normalisation lowercases, strips a trailing dot, punycodes non-ASCII names, and
rejects invalid hosts before matching.

## 4. The cache (`DnsCache`)

- Key: question name + type + class. Value: the raw upstream message.
- TTL handling: stores with the **minimum answer TTL**; on a hit it rewrites the
  answer TTLs for elapsed time so clients keep honest expiry data.
- Size-bounded; entries expire by wall clock. All in-memory, never written to disk.

## 5. Talking upstream (`UdpUpstreamResolver`)

- Plain UDP/53 to the resolver you selected (Cloudflare `1.1.1.1`, Google `8.8.8.8`,
  Quad9 `9.9.9.9`, AdGuard `94.140.14.14` by default in the UI).
- The socket is `protect()`ed via `SocketProtector` so the query does **not** loop
  back into the tunnel.
- Response is truncated (TC=1) if it would exceed the MTU — a real behaviour some
  resolvers rely on; oversized UDP answers are silently lost otherwise.
- Timeout → `SERVFAIL`, not silence.

## 6. What DNS filtering cannot do

| Situation | Why DNS cannot help |
|---|---|
| Ads and content on one domain (`cdn.news.com`) | Matching happens per *name*; both lookups are identical |
| App hard-codes its DNS (e.g. `8.8.8.8` inside the app) | The query never goes to the system resolver → never enters the tunnel |
| **DNS-over-HTTPS (DoH)** inside a browser | The lookup itself is HTTPS to `1.1.1.1`/`dns.google` → bypasses |
| **Android Private DNS (DoT)** | Same — the system resolver is bypassed |
| IPv6 | This tunnel routes one IPv4 address only; IPv6 datagrams seen in the TUN are **counted, then dropped** (they are not expected to exist there) |
| QUIC/HTTP-3 traffic | Not DNS; never reaches the process at all |

These are inherent to the technique, documented in [LIMITATIONS.md](LIMITATIONS.md),
and not on the roadmap to "fix" by intercepting encrypted DNS.
