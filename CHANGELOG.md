# Changelog

## Android app 1.6 (2026-11-13)
- Mini button opens Focus in the phone's own floating window (Xiaomi's, Samsung's…) when the phone allows it; otherwise Focus's own mini window.
- Focus's own mini window keeps its shape when you resize it, and everything inside grows and shrinks with it. Pull the line at the bottom down (or up) for full screen; the expand button is gone.
- The top line is thicker, so you can see it at a glance.
- Dhikr: History moved to a button at the top; no Undo; no instructions while counting — just the counter.

## Android app 1.5 (2026-11-13)
- Silence button during a sit (top left, and in the mini window): mutes the bells and the ticking; vibration stays.
- The top line now disappears whenever Focus is open.
- The mini window lays itself out again after you resize it.

## Android app 1.4 (2026-11-13)
- Mini window looks like Xiaomi's floating windows: one surface in the page's color (white at a Dhikr goal), three dots on top to move it, a curved corner to resize it.
- A line at the bottom of the mini window: tap it or swipe it up to open the full app.
- Opening Focus in Xiaomi's own floating window (or split screen) switches it to the small timer / counter view by itself.

## Android app 1.3 (2026-11-13)
- Line at the top of the screen now draws over the status bar, through Focus's Accessibility service (which reads nothing on screen). It redraws only when it would move a pixel, and never with the screen off.
- Mini window opens small (Sit timer or Dhikr) and can be resized from its bottom-right corner; it remembers the size.
- Dhikr vibration goes through Android itself: a strong tap on each of the last 10, a double buzz at the goal.
- New Tick sound: a soft tick-tock every second while the sit runs (with the Ring bell at the start and end).
- Dhikr history moved behind a History button, so the Dhikr screen is only the counter.
- The Urge screen is removed completely, along with its "urges beaten" count on Progress.

## Android app 1.2 (2026-11-13): App Time, stage 1
- New App Time page (Android): minutes and number of opens per app today, read from Android's own usage records.
- Pick which apps to watch; add or remove any app, with search.
- Settings › App Time page switch hides the page completely.
- Needs "Usage access", which the page walks you through. Everything stays on the device.

## Android app 1.1 (2026-11-13)
- Line at the top of the screen: while a sit runs and Focus is closed, a thin line across the top shrinks as time passes (grows when counting up) and turns amber when time is up.
- Mini window: Focus floats in a small window over your other apps. Drag it by its top bar; tap anywhere in it to count Dhikr; ✕ closes it, ⤢ opens the full app.
- Both need "Display over other apps", which Focus asks for once.

## 1.2.0 (2026-11-13)
- Dhikr: the last 10 taps before your goal vibrate; the goal itself gives a longer buzz.
- Dhikr: reaching the goal turns the whole screen white until Start fresh; extra counts show big as +1, +2… with the goal and total underneath.
- History times in 12-hour format (9:13 PM).
- Phones: the Sit screen no longer slides sideways; the bell choices wrap to fit.
- Android app: the status-bar area turns white with the screen.

## Android app 1.0 (2026-11-12)
- Focus as a real Android app: live countdown in the notifications and on the lock screen (status-bar chip on Android 16).
- Start, interval and end bells ring in your chosen sound even when Focus is closed or the screen is off.
- Screen stays on during a sit; automatic backups to Download › Focus backups.
- Updates itself: new app versions are offered inside the app.

## 1.1.0 (2026-11-12)
- Installable web app (PWA) with offline support and automatic updates.
- Desktop "island" pill: close button and line-only mode.
- ±5 minutes on a running timer.
- Automatic backups.
- Chart axis shows minutes and hours properly (e.g. `1h 15m`).

## 1.0.0
- First release: timer with bells, labels, overtime in 5-minute blocks, dhikr counter with history, urge screen, progress charts, wisdom quotes.
- Windows desktop app with a mini window, tray and notifications.
