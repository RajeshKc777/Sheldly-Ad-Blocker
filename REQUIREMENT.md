
# REQUIREMENT.md - Android Ad Blocker (branded "Shieldly")

> Single source of truth for scope, constraints, and acceptance criteria.
> Copied verbatim from the project brief. Implementation status is tracked in
> the [README](README.md) status tables and `docs/LIMITATIONS.md`.

---

Act as a senior Android engineer, network security specialist, and systems architect with deep expertise in Kotlin, Android VpnService, DNS filtering, HTTP/HTTPS networking, browser content filtering, and production-grade privacy software.

Your task is to help me build a full-fledged Android ad-blocking application from scratch. This is my first project in the ad-blocking domain, so guide the development process carefully, explain important technical decisions, and implement the application incrementally.

Do not merely produce an architecture document. Inspect the development environment and repository, research current documentation, create the project where appropriate, implement working code, run tests, and document the next steps.

## PROJECT OBJECTIVE

Build a native Android application that blocks advertising, tracking, and unwanted network requests across Android applications and supported browsers, without requiring root access.

The application should eventually combine:

1. System-wide DNS filtering.
2. Local VPN-based network traffic processing.
3. HTTP request filtering where technically possible.
4. Optional, carefully scoped HTTPS inspection.
5. Browser-level network and cosmetic filtering.
6. Maintainable community blocklists.
7. Per-application filtering controls.
8. A privacy-focused dashboard and filtering statistics.

Do not promise complete advertisement removal from every third-party application. Clearly distinguish between implemented features, technically limited features, and future research.

## TECHNOLOGY STACK

Use the following stack unless current technical evidence justifies a change:

- Kotlin for Android development.
- Jetpack Compose for the user interface.
- Android VpnService for local VPN functionality.
- MVVM with clear separation between UI, application logic, and networking.
- Kotlin coroutines for asynchronous operations.
- Room for persistent settings and local statistics where appropriate.
- WorkManager for scheduled blocklist updates where appropriate.
- Gradle Kotlin DSL.
- JUnit and Android testing tools for verification.

Prefer native Android APIs and actively maintained libraries. Do not introduce Rust, C++, a backend, or a complex external dependency unless there is a clear engineering reason.

The application should initially operate locally without user registration, mandatory cloud services, or a proprietary remote VPN server.

## PHASE 0: RESEARCH AND TECHNICAL VALIDATION

Before implementing the application, research the current state of Android ad blocking and verify relevant APIs against official documentation.

Prioritize these sources:

Android VpnService:
https://developer.android.com/develop/connectivity/vpn

Android network security configuration:
https://developer.android.com/privacy-and-security/security-config

Android background execution:
https://developer.android.com/develop/background-work

Android networking documentation:
https://developer.android.com/develop/connectivity

AdGuard Android documentation:
https://adguard.com/en/adguard-android/overview.html

EasyList:
https://easylist.to/

uBlock Origin documentation:
https://github.com/gorhill/uBlock/wiki

Determine which techniques are practical on current Android versions.

Investigate:
- Android VPN limitations and lifecycle management.
- DNS packet processing and upstream resolution.
- IPv4 and IPv6 support.
- DNS-over-HTTPS and DNS-over-TLS.
- TLS metadata visibility without decryption.
- HTTP/3 and QUIC compatibility.
- HTTPS interception and certificate trust limitations.
- Certificate pinning and Android Network Security Configuration.
- Browser-level network filtering and cosmetic filtering.
- Per-application VPN routing.
- Android battery optimization and background execution.
- Distribution restrictions affecting applications that block third-party advertisements.

Do not assume a feature works simply because it works on desktop operating systems.

Create these documentation files:

docs/RESEARCH.md
docs/ARCHITECTURE.md
docs/IMPLEMENTATION_PLAN.md
docs/SECURITY.md
docs/TESTING.md
docs/LIMITATIONS.md

Document sources, decisions, known limitations, and unresolved questions.

## PHASE 1: DEFINE THE ARCHITECTURE

Design the application using independently testable modules.

Suggested structure:

```
app/
  MainActivity.kt
  ui/
    DashboardScreen.kt
    SettingsScreen.kt
    StatisticsScreen.kt
    AppRulesScreen.kt
  vpn/
    AdBlockVpnService.kt
    VpnController.kt
    PacketProcessor.kt
  dns/
    DnsPacketParser.kt
    DnsResolver.kt
    DnsCache.kt
  filtering/
    DomainFilter.kt
    DomainMatcher.kt
    FilterEngine.kt
    RuleParser.kt
    AllowlistManager.kt
  blocklists/
    BlocklistRepository.kt
    BlocklistUpdater.kt
    BlocklistValidator.kt
  storage/
    SettingsRepository.kt
    StatisticsRepository.kt
  monitoring/
    ProtectionMonitor.kt
    NetworkMonitor.kt
```

You may adjust this structure after evaluating the networking requirements.

Do not create every proposed file immediately. Establish clear module boundaries first, then implement the minimum functionality needed for each milestone.

The architecture must distinguish between:
- Android UI and lifecycle management.
- VPN packet transport.
- DNS processing.
- Domain and request filtering.
- Upstream network resolution.
- Blocklist management.
- Persistent configuration.
- Browser-specific functionality.

The filtering engine must be testable independently of Android UI components.

## PHASE 2: IMPLEMENT THE DOMAIN FILTERING ENGINE

Begin with the simplest independently testable component.

Create a domain filtering interface equivalent to:

```kotlin
interface DomainFilter {
    fun shouldBlock(domain: String): Boolean
}
```

Implement the following capabilities:

- Normalize domain names.
- Support exact-domain matching.
- Support explicitly defined subdomain matching.
- Prevent incorrect suffix matches.
- Handle malformed inputs safely.
- Support allowlist exceptions.
- Support domain-based block rules.
- Provide clear explanations for why a rule matched.
- Add unit tests for valid, invalid, blocked, allowed, and boundary cases.

Use a straightforward implementation first. Introduce a trie or other optimized matching structure only when justified.

Do not embed arbitrary advertising-domain lists directly in application source code.

## PHASE 3: IMPLEMENT BLOCKLIST MANAGEMENT

Build a blocklist subsystem that can:

- Load rules from local test files.
- Parse the selected rule format.
- Validate downloaded lists.
- Download updates securely over HTTPS.
- Apply validated updates atomically.
- Preserve the last working version when an update fails.
- Detect duplicate rules.
- Report invalid or unsupported rules.
- Support user-defined allowlists and denylists.
- Track list versions and update timestamps.

Start with a clearly defined subset of domain-based rules.

Do not claim full EasyList, AdGuard, or uBlock Origin syntax compatibility unless it has actually been implemented and tested.

Document the supported syntax and unsupported rule types.

Respect the licenses and attribution requirements of all imported filter lists.

## PHASE 4: IMPLEMENT THE ANDROID VPN SERVICE

Create a minimal, correctly functioning VpnService.

Requirements:

- Request VPN permission through Android's supported consent flow.
- Establish the VPN interface.
- Implement the necessary foreground-service behavior and notifications for applicable Android versions.
- Handle VPN startup and shutdown.
- Handle service revocation.
- Handle network transitions.
- Release file descriptors and other resources correctly.
- Expose reliable protection status to the UI.
- Provide clear error states.
- Avoid silently claiming protection is active when the filtering engine is not operating.

Research the exact Android version requirements before implementing permissions, foreground-service declarations, and notification behavior.

IMPORTANT NETWORKING REQUIREMENT:

Creating a VPN interface does not automatically provide a complete network proxy.

Any traffic routed into the VPN must either be processed appropriately or forwarded through a functioning network path. Do not silently discard ordinary TCP, UDP, or IPv6 traffic.

Design a packet-processing strategy that can be implemented and tested incrementally.

## PHASE 5: IMPLEMENT DNS PACKET PROCESSING

Build the DNS processing pipeline.

Requirements:

- Parse DNS packets safely.
- Validate packet lengths and supported record types.
- Identify queried domains.
- Apply domain filtering rules.
- Return valid DNS responses for blocked queries.
- Forward permitted queries to a configured upstream resolver.
- Validate upstream responses.
- Implement DNS caching with appropriate TTL handling.
- Handle timeouts and malformed responses.
- Avoid unbounded memory allocation.
- Support IPv4 and IPv6 networking.
- Include structured diagnostics without recording unnecessary browsing data.

Do not invent a custom DNS wire format.

Use a maintained DNS library if it materially improves correctness and security. Evaluate its maintenance status, license, Android compatibility, and API suitability before adopting it.

Test DNS processing independently before integrating it with the VPN service.

## PHASE 6: BUILD THE FIRST WORKING MVP

The first MVP must provide:

- A working Android application.
- A dashboard with a protection toggle.
- A functional local VPN service.
- DNS-based domain blocking.
- A small, clearly documented test blocklist.
- A configurable upstream DNS resolver.
- Basic allowlist management.
- Blocked-domain counters.
- Protection status.
- Graceful error handling.
- Automated unit tests.
- A repeatable debug APK build.

The MVP must not claim to block all advertisements.

Provide a test procedure that verifies domain filtering and confirms that ordinary internet access remains functional.

Do not move to advanced HTTP or HTTPS filtering until this milestone passes its tests.

## PHASE 7: ADVANCED NETWORK FILTERING

After the MVP is stable, evaluate the following features independently.

### A. HTTP request filtering

Implement URL-based filtering where requests are genuinely visible to the application.

Use an established filter-rule format where appropriate. Support exceptions and document unsupported syntax.

### B. HTTPS metadata filtering

Use only metadata legitimately available without decrypting encrypted traffic.

Explain which information is visible and which information remains encrypted.

Do not claim that destination metadata provides access to complete HTTPS URLs or page contents.

### C. Optional HTTPS inspection

Treat this as a separate, experimental module.

Before implementing it, document:

- The local proxy architecture.
- Certificate generation and secure storage.
- Explicit user consent and certificate installation.
- Application trust-store limitations.
- Certificate pinning compatibility.
- HTTP/3 and QUIC limitations.
- Private-data exposure risks.
- Failure behavior.
- Safe disablement and certificate removal.

Do not silently install certificates.

Do not bypass application security controls or implement indiscriminate certificate-pinning bypasses.

Prefer the least invasive filtering method that meets the use case.

## PHASE 8: BROWSER CONTENT FILTERING

Evaluate building an optional browser using an appropriate Android browser engine.

The browser should eventually support:

- Network request filtering.
- Selected cosmetic filtering rules.
- Blocking third-party tracking scripts.
- Hiding unwanted advertising elements.
- Site-specific exceptions.
- Clear indicators showing which resources were blocked.

Keep browser filtering separate from system-wide VPN filtering while allowing both to use shared rules and configuration.

Do not assume that an ordinary Android application can install arbitrary extensions into Chrome for Android or inject scripts into unrelated applications.

## PHASE 9: USER INTERFACE

Build a clean, lightweight interface with Jetpack Compose.

The dashboard should display:

- Protection status.
- Start and stop controls.
- Number of blocked requests or DNS queries, with accurate labels.
- Active blocklists.
- Recent filtering events.
- Settings.
- Application-specific rules.
- Allowlist and denylist management.
- Troubleshooting information.

Avoid decorative complexity and excessive animations.

Do not display fabricated statistics. Clearly distinguish DNS queries blocked from individual advertisements removed.

## PHASE 10: PRIVACY AND SECURITY

The application must follow privacy-by-design principles.

Requirements:

- No mandatory account.
- No mandatory remote backend.
- No unnecessary collection of browsing history.
- No transmission of raw DNS logs by default.
- Secure handling of blocklist downloads.
- Validation of imported rules.
- Resource limits for packet parsing and rule processing.
- No unnecessary permissions.
- No silent certificate installation.
- Clear VPN permission explanations.
- Safe handling of corrupted blocklists.
- A documented privacy policy.
- A mechanism to disable protection and restore connectivity.

Evaluate fail-open and fail-closed behavior explicitly. Do not blindly select either approach for every failure scenario.

## PHASE 11: TESTING AND PERFORMANCE

Implement automated tests covering:

- Domain normalization.
- Exact and subdomain matching.
- Allowlist precedence.
- Rule parsing.
- Malformed DNS packets.
- Upstream DNS timeouts.
- Cache expiration.
- Blocklist update failures.
- VPN startup and shutdown.
- Network transitions.
- IPv4 and IPv6.
- Application compatibility.
- Resource usage.

Measure:
- DNS resolution latency.
- Memory consumption.
- CPU usage.
- Battery impact.
- Rule-matching performance.
- Reliability during network changes.

Test on a physical Android device as well as an emulator when feasible.

Include a test matrix covering Chrome, Facebook, Instagram, YouTube, games, and banking applications.

For each application, document what works, what is blocked, and what remains technically impossible or unreliable.

## PHASE 12: DISTRIBUTION

Before preparing a release, investigate the current policies of Google Play and alternative distribution channels.

Do not assume a system-wide ad blocker is eligible for Google Play merely because it uses a supported Android API.

Prepare:
- Application signing.
- Release build configuration.
- APK generation.
- Versioning.
- Upgrade testing.
- Privacy documentation.
- Installation instructions.
- A secure update strategy.

Do not publish the application automatically or make irreversible distribution changes without explicit authorization.

## DEVELOPMENT WORKFLOW

Follow these rules throughout the project.

1. Inspect the repository and development environment before making changes.
2. If no Android project exists, create the initial project using a suitable, verified Android development toolchain.
3. If a project already exists, preserve its established structure unless there is a compelling technical reason to change it.
4. Write a short implementation plan before each substantial phase.
5. Implement one small, testable milestone at a time.
6. Run the relevant tests and build commands after each milestone.
7. Fix compilation errors and test failures before proceeding.
8. Never claim that a feature works unless its implementation has been verified.
9. Never replace real networking functionality with a fake implementation merely to make the UI appear complete.
10. Never generate an enormous collection of placeholder classes and call the application production-ready.
11. Use official documentation and maintained dependencies. Verify API availability rather than relying on outdated examples.
12. Explain important networking and security decisions in clear language.
13. Maintain a list of completed work, outstanding issues, known limitations, and the next recommended milestone.
14. Keep changes small enough to review and debug.
15. Do not overwrite existing user data, configuration, or unrelated project files.
16. If an essential decision is ambiguous, explain the options and choose the safest reversible default when possible.

## FIRST ACTIONS

Start now.

First, inspect the repository, available Android SDK, Java version, Gradle environment, and connected test devices if accessible.
Second, research the current Android VPN and DNS implementation requirements.
Third, write the initial architecture and implementation documents.
Fourth, define the smallest working MVP and its acceptance tests.
Fifth, implement the domain filtering engine with unit tests.
Sixth, build and verify the initial project before proceeding to VPN integration.

At the end of the first development session, report:

1. What you inspected.
2. What you researched.
3. What files you created or changed.
4. What functionality is implemented.
5. Which tests and builds passed or failed.
6. Any unresolved technical risks.
7. The exact next implementation step.

The objective is not to produce the largest amount of code. It is to build a secure, technically sound, maintainable Android ad blocker whose capabilities can be demonstrated and verified at every stage.
