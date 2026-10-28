# Blocklists

How rules are written, parsed, and kept honest.

---

## 1. Where lists come from

| Source | File | Status |
|---|---|---|
| Bundled starter list | `app/src/main/assets/blocklist/test-blocklist.txt` | ✅ the only active source (49 rules) |
| Your allow/block rules | `SharedPreferences` | ✅ applied on every rebuild |
| Downloaded lists | `core/…/BlocklistUpdater.kt` | 🟡 **library ready, tested, not wired into the app yet** |

The starter list is intentionally small. It is not EasyList; its job is to prove
the pipeline end-to-end with near-zero false positives. Importing a large public
list becomes possible the moment the updater is wired to the UI.

## 2. Rule syntax

| Line | Effect |
|---|---|
| `example.com` | block `example.com` + every subdomain |
| `||example.com^` | Adblock syntax, same effect |
| `0.0.0.0 example.com` | hosts-file syntax, same effect |
| `@@example.com` | **allow** (exception) |
| `! comment` / `# comment` | ignored |
| `##ad-banner` | cosmetic selector → **unsupported** (counted) |
| `/banner/ad.png` | path → unsupported |
| `/regex/` or `$script` | regex / modifier → unsupported |

Everything unsupported is **reported, not dropped silently**: the Settings
"Filter build" card shows accepted / duplicates / unsupported / invalid counts so
the rule number stays trustworthy.

## 3. Matching semantics

- A rule matches the domain **and all of its subdomains**: `example.com` covers
  `a.b.example.com`.
- Names are normalised first: lowercased, trailing dot stripped, non-ASCII
  punycoded, invalid hosts rejected.
- **Precedence:** your allow rules → your deny rules → bundled list. An allow rule
  always wins.

## 4. Adding rules in the app

1. **Rules** tab → *Add a rule*.
2. Type a domain (`example.com`, no paths).
3. Choose **Allow** (fix over-blocking) or **Block** (extend the list).
4. Not sure what would happen? Use *Check a domain*; it prints the exact rule
   and source that would match, using the same immutable snapshot the DNS path uses.

Rules survive restarts (they are the only data written to disk besides the
resolver choice).

## 5. How an update would install (and not break) your list

The updater separates **fetch → validate → build → swap**:

1. **Fetch:** HTTPS-only, redirect hops re-checked (no `https→http` downgrade),
   10 s connect / 15 s read timeouts, 8 MiB cap enforced twice (declared length
   and bytes read).
2. **Validate:** not empty, ≤ 800k lines, ≥ 1 usable rule, and a warning if >90%
   of lines were unsupported (a sign you grabbed a browser-only list).
3. **Build:** the parser produces a compiled snapshot; failures here abort.
4. **Swap:** `BlocklistRepository.current` is replaced atomically: a DNS query in
   flight finishes against the old snapshot; the next query sees the new one.

A failed download changes nothing: the previous list stays active. That is the
design contract of `BlocklistUpdater` and it is covered by
[`BlocklistUpdaterTest`](../core/src/test/kotlin/com/sentinel/core/blocklists/BlocklistUpdaterTest.kt).

## 6. What is *not* here yet

- No UI to paste a URL and update from the internet.
- No schedule, no "check daily", no diffing, no mirror fallback.
- No disk cache of downloaded lists (a restart would re-download).
- No automatic trust evaluation beyond the size/format limits above; treat
  third-party list sources as supply-chain inputs, the way you would treat
  third-party dependencies.
