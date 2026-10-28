# Releasing

How a versioned release with a real, downloadable APK gets published.

---

## 1. What the workflow does

`.github/workflows/android-build.yml` has two jobs:

```
push / PR / manual  ─►  build:  ./gradlew test assembleDebug
                        verify APK is non-empty
                        upload artifact "Shieldly-debug-apk"

tag push v*.*.*     ─►  release job (needs: build):
                        ./gradlew assembleDebug -Papp.versionName=<tag>
                        rename to Shieldly-<version>-debug.apk
                        gh release create <tag> + auto-generated notes + APK
```

- On every push/PR the APK is available as a workflow **artifact** (retained 14 days).
- On a **version tag** the APK is published as a **GitHub Release asset**, which
  is the permanent download link the README points to
  (`…/releases/latest` resolves to the newest release).
- The release job has `permissions: contents: write`, the least privilege that
  allows creating a release, while the rest of the pipeline runs with
  `contents: read`.
- If tests fail or the APK is missing, the release step never runs.

## 2. Publishing the first release (exact steps)

```bash
# 1. Make sure main is green on Actions.
# 2. Tag and push:
git tag v1.0.0
git push origin v1.0.0
# 3. Watch Actions → "android-build" → release job.
# 4. Verify: https://github.com/RajeshKc777/Sheldly-Ad-Blocker/releases
```

The tag must match `v*.*.*` or the release job is skipped. The release title and
notes are generated from the commits since the previous tag.

## 3. Verifying a release before telling anyone

```bash
gh release view v1.0.0 --json assets --jq '.assets[].name'
# expect: Shieldly-1.0.0-debug.apk
gh release download v1.0.0 -p '*.apk' -D ./
# install it on a device and check Settings → About shows version 1.0.0
```

## 4. What is published today

A **debug-signed** APK. It is fully functional and installable, but it is not a
release-signed Play-style build. That is stated in the README, with no overclaim.

## 5. Production signing (when you are ready)

Never commit a keystore. Use GitHub Actions **secrets**:

1. Generate a keystore locally (keep the file private):

   ```bash
   keytool -genkeypair -v \
     -keystore shieldly-release.jks -alias shieldly \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Base64-encode it and put it in repository secrets
   (**Settings → Secrets and variables → Actions → New repository secret**):

   | Secret | Value |
   |---|---|
   | `SIGNING_KEYSTORE_BASE64` | base64 of the `.jks` |
   | `SIGNING_KEYSTORE_PASSWORD` | keystore password |
   | `SIGNING_KEY_ALIAS` | e.g. `shieldly` |
   | `SIGNING_KEY_PASSWORD` | key password |

3. Add a signing step to the release job (draft):

   ```yaml
   - name: Decode signing keystore
     run: echo "${{ secrets.SIGNING_KEYSTORE_BASE64 }}" | base64 -d > shieldly.jks

   - name: Build signed release APK
     env:
       SIGNING_STORE_FILE: shieldly.jks
       SIGNING_STORE_PASSWORD: ${{ secrets.SIGNING_KEYSTORE_PASSWORD }}
       SIGNING_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
       SIGNING_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
     run: ./gradlew assembleRelease
   ```

   with matching `signingConfigs` in `app/build.gradle.kts` that read those
   environment variables (and fall back to skipping signing when absent, so the
   workflow still works for forks).

4. Upload `Shieldly-<version>-release.apk` to the release; keep publishing the
   debug APK as a secondary asset for testers.

**Secrets hygiene:** secrets never appear in logs or source; rotate the keystore
password if it was ever exposed; back the `.jks` up offline; losing it means
losing the ability to ship updates under the same app identity.

## 6. Version numbering

- `versionCode` is currently fixed at `1` in `app/build.gradle.kts`. Before
  shipping to anything store-like, derive it from the tag or bump it per release
  (Android requires a strictly higher code per update).
- `versionName` is `0.1.0` locally, and stamped from the tag on release builds
  (`-Papp.versionName=${GITHUB_REF_NAME#v}`), so **Settings → About** shows the
  released version.
