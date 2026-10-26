# Building

Everything needed to compile Shieldly from a clean checkout.

---

## 1. Toolchain

| Requirement | Version | Notes |
|---|---|---|
| JDK | **17** (temurin/zulu/graalvm all fine) | Android Studio's bundled JBR (File → Project Structure) is JDK 17 |
| Android SDK | **36** (compileSdk / targetSdk) | Installed by Android Studio, or `sdkmanager "platforms;android-36"` |
| Gradle | 8.14 via the **wrapper** | Never install Gradle manually; use `./gradlew` |
| Kotlin | 2.2.21 | Pinned in `gradle/libs.versions.toml` |
| AGP | 8.13.1 | Pinned in `gradle/libs.versions.toml` |

## 2. Command-line build

```bash
git clone https://github.com/RajeshKc777/Sheldly-Ad-Blocker.git
cd Sheldly-Ad-Blocker

# Point the build at your SDK (one time; keep this file out of git)
echo "sdk.dir=C\\:\\\\Android\\\\sdk" > local.properties    # Windows example
echo "sdk.dir=$HOME/Android/Sdk"      > local.properties    # Linux/macOS example

./gradlew test assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (~12 MB, debug-signed,
installable on any Android 8.0+ device).

On Windows use `gradlew.bat`; on Linux/macOS `./gradlew` (make it executable first
if needed: `chmod +x gradlew`).

## 3. Android Studio

Open the repository root as a Gradle project. Studio generates
`local.properties` automatically. Use **Run ▶** with an emulator or device for
live debugging; the app module's launcher activity is `MainActivity`.

## 4. Common build errors

| Error | Cause | Fix |
|---|---|---|
| `Unable to delete directory …/build` | A stale Gradle daemon holds files (common when the project lives in a synced folder such as OneDrive/Dropbox) | `./gradlew --stop`, delete `app/build` and `core/build`, rebuild. Moving the checkout out of the synced folder avoids it entirely |
| `SDK location not found` | Missing `local.properties` | Create it (section 2) or set `ANDROID_HOME` |
| `Unsupported class file major version` | Wrong JDK (e.g. JDK 8/11) | Set `JAVA_HOME` to a JDK 17 |
| `Failed to install the following SDK components` | SDK 36 not installed / licence not accepted | `sdkmanager --licenses`, then install `platforms;android-36` |
| Kotlin `e:` compile errors after an update | Stale caches | `./gradlew clean`, then rebuild |

## 5. Build types

| Type | Command | Signed with | Purpose |
|---|---|---|---|
| `debug` | `./gradlew assembleDebug` | auto-generated debug key | **the distributed APK** (GitHub Releases) |
| `release` | `./gradlew assembleRelease` | unsigned until configured | minified + resource-shrunk; see [RELEASING.md](RELEASING.md) |

The release build enables R8 minification and resource shrinking
(`isMinifyEnabled = true`); it needs a keystore before it can be installed.

## 6. Continuous integration

`.github/workflows/android-build.yml` runs on every push and pull request:

- JDK 17 (temurin) + `gradle/actions/setup-gradle` with dependency caching,
- `./gradlew test assembleDebug`,
- uploads the APK as the **Shieldly-debug-apk** artifact,
- on a `v*.*.*` tag, publishes it to a GitHub Release
  (see [RELEASING.md](RELEASING.md)).

No secrets are required for the current debug-APK pipeline.
