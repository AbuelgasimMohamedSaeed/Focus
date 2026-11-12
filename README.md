<p align="center">
  <img src="icons/icon-192.png" width="96" alt="Focus icon">
</p>

<h1 align="center">Focus</h1>

<p align="center">
  A private, distraction-free timer for meditation, study and dhikr.<br>
  No accounts, no ads, no tracking. Your data never leaves your device.
</p>

<p align="center">
  <img src="docs/screenshots/sit.png" width="49%" alt="Sit screen">
  <img src="docs/screenshots/timer.png" width="49%" alt="Running timer">
  <img src="docs/screenshots/dhikr.png" width="49%" alt="Dhikr counter">
  <img src="docs/screenshots/progress.png" width="49%" alt="Progress charts">
</p>

---

## Features

**Timer**
- Count down from a set time, or count up from zero.
- Labels such as Study or Meditation, each with its own color.
- Rich synthesized bells at the start and end, plus optional interval bells.
- ±5 minutes while it is running, so you never have to stop and restart.
- Overtime counts in 5-minute blocks until you press **Done**.
- Hold to end, so a stray tap can't stop a session.

**Dhikr**
- Set a goal and tap anywhere above the tabs (or press Space) to count.
- History of every session, complete or not, with delete.

**Urge**
- A short pause screen for the moment you want to open social media.

**Progress**
- Daily and weekly charts per label, plus streaks and totals.

**Wisdom**
- A new quote each time you open it: Qur'an verses (Arabic and English, fetched online) and offline quotes.

**Everywhere**
- Works offline once loaded (installable PWA).
- 12-hour time.
- Automatic local backups.

## Install

### Android tablet / phone
1. Open **https://abuelgasimmohamedsaeed.github.io/focus/** in Chrome or Samsung Internet.
2. Menu → **Add to Home screen** / **Install app**.
3. Focus opens full-screen like a normal app and works offline.

> A native Android build with a live status-bar timer is planned (see [Roadmap](#roadmap)).

### Windows desktop
The `desktop/` folder holds an Electron shell that adds:
- a mini window;
- a tray icon;
- notifications when time is up, even with the window closed;
- a top-of-screen "island" pill that shows the time left, which you can collapse to a thin line.

```bash
cd desktop
npm install electron@33.2.1 --save-dev
npx electron .
```

## How updates work

The app is a single page hosted on GitHub Pages.
- **Web / installed PWA:** each launch checks for a newer `index.html` first (network-first) and falls back to the cached copy when offline. Pushing to `main` updates every device; no reinstall is needed.
- **Desktop:** the Electron shell bundles its own copy today; loading the hosted version is planned.

## Privacy

- Everything you record (sessions, labels, dhikr history) is stored in the browser's `localStorage` on that device only.
- Each device keeps its own progress.
- The only network requests are:
  - Google Fonts;
  - the Qur'an verse API (`api.alquran.cloud`) for the wisdom card;
  - and, for updates, the page itself.

## Project structure

```
focus/
├── index.html              # the whole app (HTML + CSS + JS, no build step)
├── manifest.webmanifest    # PWA metadata
├── sw.js                   # service worker: offline + update strategy
├── icons/                  # app icons (PWA, maskable, Apple, Windows .ico)
├── desktop/                # Windows app (Electron)
│   ├── main.js             # window, tray, notifications, island, backups
│   ├── preload.js          # safe bridge between app and Electron
│   ├── island.html         # the top-of-screen pill
│   ├── island-preload.js
│   └── still.html          # bundled copy of the app
└── docs/screenshots/
```

## Roadmap

- [ ] Desktop app loads the hosted version, with an offline fallback.
- [ ] Android app with a live notification / status-bar timer and a time's-up alert.
- [ ] Optional export and import between devices.

## License

Personal project. All rights reserved.
