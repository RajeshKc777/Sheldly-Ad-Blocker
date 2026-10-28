# Getting started with Shieldly

A complete beginner's guide. No Android or networking knowledge is assumed.
Every technical term is explained the first time it appears.

---

## 1. What you need

| Thing | Minimum |
|---|---|
| A phone or tablet | Android **8.0** (API 26) or newer |
| The APK | From the [releases page](https://github.com/RajeshKc777/Sheldly-Ad-Blocker/releases/latest), with a file named like `Shieldly-1.0.0-debug.apk` |
| Free storage | ~50 MB |
| Nothing else | No account, no sign-up, no server |

## 2. Installing the APK

An **APK** is the file format Android uses to install apps, the same thing the
Play Store installs for you, except here you hand the file to Android directly.

1. Copy or download the APK onto the phone.
2. Tap it in your file manager.
3. If Android warns *"unknown apps"*, tap **Settings** → enable
   **Allow from this source** → back → **Install**. This is standard for any app
   installed outside the Play Store.
4. **Android 13+ note:** because the APK is not from the Play Store, the
   accessibility toggle (used only by the optional Facebook hider) may first show
   *"Restricted setting"*. If you want that feature: **Settings → Apps → Shieldly
   → ⋮ → Allow restricted settings** once.
5. Tap **Open**.

## 3. Turning protection on

1. On the **Dashboard**, flip the switch.
2. Android asks *"Shieldly wants to set up a VPN connection. It can monitor
   network traffic…"*. Tap **OK**.
   This dialog is the one permission that matters. A **local VPN** means the app
   creates a network doorway on the device itself; no data is sent to a VPN
   company, and Shieldly has no server. See
   [HOW_IT_WORKS.md](HOW_IT_WORKS.md) for exactly what is and is not monitored.
3. The status becomes **Protection on**, and a quiet "protection is active"
   notification appears. Counters on the Dashboard start moving.

## 4. What to expect

- The **Dashboard** shows how many DNS lookups were seen and how many were
  blocked. Blocked domains are listed under *Recent blocked lookups*.
- The bundled starter list is deliberately small (49 rules), so do not expect
  dramatic numbers yet; it exists to prove the pipeline, not to replace EasyList.
- Nothing is configured anywhere else on the phone; Shieldly sets itself as the
  DNS resolver only while protection is running.

## 5. Day-to-day

| Task | Where |
|---|---|
| Pause protection | Dashboard → switch off (counters reset) |
| Stop an app from being blocked | **Rules** tab → add an allow rule, or test the domain in the checker |
| Block an extra domain | **Rules** tab → add a block rule |
| Change the upstream resolver | **Settings** tab → Upstream DNS resolver |
| See every counter | **Stats** tab |

## 6. Uninstalling

Uninstall like any app. The tunnel is released the moment the app is removed;
nothing remains on the phone except, on Android 8–10, a "VPN app" entry that
Android cleans up automatically.

## 7. First steps for developers

Head to [BUILDING.md](BUILDING.md) to compile the project, and
[ARCHITECTURE.md](ARCHITECTURE.md) for a guided tour of the code.
