# Contributing

Thanks for considering a contribution. The rules below are short because the
codebase has exactly three non-negotiable principles.

---

## The three principles

1. **Keep `:core` Android-free.** Everything in `core/src/main` must compile as a
   plain Kotlin JVM module with no `android.*` imports. That is what makes the
   DNS hot path unit-testable without an emulator. If a change seems to need
   Android APIs in `core`, it belongs in `:app` behind an interface.
2. **Never claim a capability the code does not have.** Documentation that
   overclaims is treated as a bug, same as broken code. If your PR adds a
   feature, update the status tables in the README and the relevant doc — and if
   something stays unimplemented, say so plainly.
3. **No third-party network dependency in the DNS hot path.** The tunnel,
   parser, cache, and resolver use `java.net`/`java.nio` only. Fewer moving
   parts, fewer supply-chain inputs, easier audits.

## Good first issues

- Extending the **starter blocklist** (see [BLOCKLISTS.md](BLOCKLISTS.md) — send
  rules, not the whole list, and explain each addition).
- **Tests** for `:core` edge cases (malformed DNS messages, weird domains,
  rule-syntax corners).
- **Docs** improvements — a beginner's confusion is always a doc bug; tell us.

## Workflow

```bash
git clone https://github.com/RajeshKc777/Sheldly-Ad-Blocker.git
cd Sheldly-Ad-Blocker
./gradlew test assembleDebug     # must stay green
```

- One feature or fix per pull request; keep diffs reviewable.
- Run `./gradlew test` before pushing — CI runs the same thing.
- Update the docs that your change touches (the README status tables are part
  of the review).
- Follow the existing code style: 4-space indent, explanatory KDoc on public
  classes, comments that explain *why*.

## What will not be merged

- HTTPS/TLS inspection of any kind, or any feature that requires installing a
  user certificate.
- Telemetry, analytics, or any backend call the user did not explicitly make.
- A dependency on a third-party filtering engine for the hot path.
- Features that make the accessibility hider broader in scope (more apps,
  more capabilities) without a written, honest disclosure to match.

## Commit messages

Present tense, imperative, with the *why* in the body when it is not obvious:

```
Fix stale overlay after leaving Facebook

The watchdog cleared covers only when rootInActiveWindow was null;
MIUI keeps a stale root during app transitions, so covers survived
into the installer UI. Compare package name instead.
```
