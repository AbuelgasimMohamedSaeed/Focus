// Focus as a real Windows app. The window can turn itself into a small
// always-on-top mini window in the top-right corner (like Windows Clock) and back.
const { app, BrowserWindow, ipcMain, screen, nativeTheme, Menu, Tray, Notification, shell, nativeImage } = require('electron');
const path = require('path');
const fs = require('fs');

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) { app.quit(); }
app.setAppUserModelId('Focus');

let win = null;
let saved = null; // the big window's size and state while mini mode is on
let sit = null;     // the running sit, as told by the page: { endAt, planned, label, over }
let tray = null, endTimer = null, quitting = false, island = null;
const ICON = path.join(__dirname, 'still.ico');
const LOG = path.join(path.dirname(process.execPath), '..', 'focus-log.txt');
function log(...a) {
  try {
    const line = a.map(x => x instanceof Error ? (x.stack || x.message) : typeof x === 'object' ? JSON.stringify(x) : String(x)).join(' ');
    fs.appendFileSync(LOG, new Date().toLocaleString() + '  ' + line + '\n');
  } catch (e) {}
}
process.on('uncaughtException', e => log('ERROR', e));
log('Focus started (pill version 4)');
if (!gotLock) log('another Focus is already running: handing over to it');
// remember this version's files: if they change on disk (an update), the next click on the icon restarts Focus with the new version
const CODE_FILES = ['main.js', 'preload.js', 'island-preload.js'].map(f => path.join(__dirname, f));
const codeStamp = () => CODE_FILES.map(f => { try { return fs.statSync(f).mtimeMs; } catch (e) { return 0; } }).join(',');
const startStamp = codeStamp();
const MINI_W = 340, MINI_H = 300;
const wait = ms => new Promise(r => setTimeout(r, ms));

function createWindow() {
  nativeTheme.themeSource = 'dark';      // dark title bar to match the app
  Menu.setApplicationMenu(null);         // no File/Edit menu bar
  win = new BrowserWindow({
    width: 1200, height: 820, minWidth: 320, minHeight: 260,
    title: 'Focus', backgroundColor: '#09090b', show: false,
    icon: path.join(__dirname, 'still.ico'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true, sandbox: true,
      backgroundThrottling: false,                 // timers and bells keep running when hidden
      autoplayPolicy: 'no-user-gesture-required'   // the end bell can always play
    }
  });
  win.once('ready-to-show', () => { win.maximize(); win.show(); });
  win.webContents.on('preload-error', (_e, p, err) => log('preload error', p, err));
  win.webContents.on('console-message', (_e, level, msg) => { if (level >= 2) log('app says:', msg); });
  win.loadFile(path.join(__dirname, 'still.html'));
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  // closing during a sit: shut the window down completely and keep only the tray + one timer (lightest on battery)
  win.on('close', e => {
    log('window closing; sit running:', !!sit);
    if (quitting || !sit) return;
    e.preventDefault();
    showTray();
    win.destroy();
  });
  win.on('closed', () => { win = null; saved = null; updateIsland(); });
  ['focus', 'blur', 'minimize', 'restore', 'show', 'hide'].forEach(ev => win.on(ev, () => setTimeout(updateIsland, 50)));
}

// ---- the pill: a phone-style live timer at the top of the screen while a sit runs and Focus isn't in front
const ISLAND_W = 380, ISLAND_H = 60;
let islandDismissed = '';  // the sit whose pill you closed with the cross
const PREFS = path.join(app.getPath('userData'), 'pill.json');
let islandCollapsed = false;
try { islandCollapsed = !!JSON.parse(fs.readFileSync(PREFS, 'utf8')).collapsed; } catch (e) {}
const sitKey = () => sit ? String(sit.endAt - sit.planned * 60000) : ''; // stays the same when you add or remove 5 minutes
const islandMsg = () => Object.assign({}, sit, { collapsed: islandCollapsed });
function islandWanted() {
  if (!sit || saved) return false;                 // no sit, or Focus is already a mini window on top
  if (islandDismissed && islandDismissed === sitKey()) return false; // you closed it for this sit
  if (!win) return true;                           // Focus closed: show it
  return !(win.isVisible() && !win.isMinimized() && win.isFocused());
}
function ensureIsland() {
  if (island) return island;
  const wa = screen.getPrimaryDisplay().workArea;
  log('creating pill at top of screen', wa);
  island = new BrowserWindow({
    x: Math.round(wa.x + (wa.width - ISLAND_W) / 2), y: wa.y + 4, width: ISLAND_W, height: ISLAND_H,
    frame: false, transparent: true, resizable: false, movable: false, minimizable: false, maximizable: false,
    fullscreenable: false, skipTaskbar: true, focusable: true, hasShadow: false, show: false,
    alwaysOnTop: true, backgroundColor: '#00000000',
    webPreferences: { preload: path.join(__dirname, 'island-preload.js'), contextIsolation: true, sandbox: true }
  });
  island.webContents.on('did-finish-load', () => { log('pill loaded'); if (island && sit) island.webContents.send('island:state', islandMsg()); updateIsland(); });
  island.webContents.on('did-fail-load', (_e, code, desc) => log('pill failed to load', code, desc));
  island.webContents.on('render-process-gone', (_e, d) => log('pill crashed', d));
  island.webContents.on('console-message', (_e, level, msg) => log('pill says:', msg));
  island.on('closed', () => { island = null; });
  try { island.setIgnoreMouseEvents(true, { forward: true }); } catch (e) { log('click-through error', e); }
  island.loadFile(path.join(__dirname, 'island.html')).catch(e => log('pill loadFile error', e));
  try { island.setAlwaysOnTop(true, 'screen-saver'); } catch (e) { log('setAlwaysOnTop error', e); }
  return island;
}
function updateIsland() {
  try {
    if (!sit) { if (island) { island.destroy(); island = null; log('pill removed'); } return; }
    if (!islandWanted()) { if (island && island.isVisible()) { island.hide(); log('pill hidden (Focus in front)'); } return; }
    const isl = ensureIsland();
    if (!isl.webContents.isLoading()) {
      isl.webContents.send('island:state', islandMsg());
      if (!isl.isVisible()) { isl.showInactive(); log('pill shown'); }
    }
  } catch (e) { log('updateIsland error', e); }
}
ipcMain.on('island:open', () => openWindow());
ipcMain.on('island:close', () => { islandDismissed = sitKey(); log('pill closed for this sit'); updateIsland(); });
ipcMain.on('island:collapse', (_e, on) => {
  islandCollapsed = !!on; log('pill', on ? 'shows only the line' : 'shows the full timer');
  try { fs.writeFileSync(PREFS, JSON.stringify({ collapsed: islandCollapsed })); } catch (e) {}
});
ipcMain.on('island:mouse', (_e, over) => { try { if (island) island.setIgnoreMouseEvents(!over, { forward: true }); } catch (e) {} });

function openWindow() {
  if (win) { if (win.isMinimized()) win.restore(); win.show(); win.focus(); return; }
  createWindow();
}

// ---- tray (only while a sit runs with the window closed)
const clock = t => new Date(t).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
function trayText() {
  if (!sit) return 'Focus';
  return 'Focus \u00b7 ' + (sit.label ? sit.label + ' \u00b7 ' : '') + (sit.over ? 'time is up, extra time counting' : 'ends at ' + clock(sit.endAt));
}
function showTray() {
  if (!tray) {
    tray = new Tray(nativeImage.createFromPath(ICON));
    tray.on('click', openWindow);
    tray.setContextMenu(Menu.buildFromTemplate([
      { label: 'Open Focus', click: openWindow },
      { type: 'separator' },
      { label: 'Quit (the sit stays saved)', click: () => { quitting = true; app.quit(); } }
    ]));
  }
  tray.setToolTip(trayText());
}
function hideTray() { if (tray) { tray.destroy(); tray = null; } }

// ---- one timer for the end of the sit, then a Windows notification
function scheduleEnd() {
  clearTimeout(endTimer); endTimer = null;
  if (!sit || sit.over) return;
  const ms = sit.endAt - Date.now();
  if (ms <= 0) return;
  endTimer = setTimeout(onEnd, Math.min(ms, 2147483000));
}
function onEnd() {
  log('time is up');
  if (!sit) return;
  sit.over = true;
  if (tray) tray.setToolTip(trayText());
  updateIsland();
  const open = win && win.isVisible() && win.isFocused();
  if (open || !Notification.isSupported()) return; // the open app rings its own bell
  const n = new Notification({
    title: 'Time\u2019s up' + (sit.label ? ' \u00b7 ' + sit.label : ''),
    body: sit.planned + ' min done. Extra time counts every 5 min until you press Done.',
    icon: ICON, silent: false
  });
  n.on('click', openWindow);
  n.show();
}
ipcMain.handle('still:sit', (_e, state) => {
  sit = state && typeof state.endAt === 'number' ? state : null;
  log('sit from app:', sit ? { label: sit.label, planned: sit.planned, over: sit.over } : 'none');
  scheduleEnd();
  updateIsland();
  if (!sit) { hideTray(); if (!win) app.quit(); }
  else if (tray) tray.setToolTip(trayText());
  return true;
});

// Windows shows notifications reliably when the Start menu shortcut carries the app's ID
function ensureStartMenuShortcut() {
  if (process.platform !== 'win32') return;
  try {
    const lnk = path.join(app.getPath('appData'), 'Microsoft', 'Windows', 'Start Menu', 'Programs', 'Focus.lnk');
    shell.writeShortcutLink(lnk, fs.existsSync(lnk) ? 'update' : 'create', {
      target: process.execPath, cwd: path.dirname(process.execPath), icon: ICON, iconIndex: 0,
      appUserModelId: 'Focus', description: 'Focus: meditation, study timer and dhikr'
    });
  } catch (e) {}
}

ipcMain.handle('still:mini', async (_e, on) => {
  if (!win) return false;
  if (on && !saved) {
    saved = { bounds: win.getNormalBounds(), max: win.isMaximized(), full: win.isFullScreen() };
    if (saved.full) { win.setFullScreen(false); await wait(250); }
    if (saved.max) { win.unmaximize(); await wait(120); }
    const wa = screen.getDisplayMatching(win.getBounds()).workArea;
    win.setMinimumSize(220, 180);
    win.setBounds({ x: wa.x + wa.width - MINI_W - 16, y: wa.y + 16, width: MINI_W, height: MINI_H }, true);
    win.setAlwaysOnTop(true, 'floating');
    updateIsland();
  } else if (!on && saved) {
    const s = saved; saved = null;
    win.setAlwaysOnTop(false);
    win.setMinimumSize(320, 260);
    win.setBounds(s.bounds, true);
    if (s.max) { await wait(60); win.maximize(); }
    win.focus();
    updateIsland();
  }
  return true;
});

// ---- automatic backups: Documents\Focus backups
const bkDir = () => path.join(app.getPath('documents'), 'Focus backups');
const hasData = j => { const d = j && j.data; return !!d && ((d.sessions || []).length > 0 || ((d.dhikr && d.dhikr.log) || []).length > 0); };
ipcMain.handle('still:backup', async (_e, json) => {
  try {
    if (typeof json !== 'string' || json.length > 20e6) return false;
    const next = JSON.parse(json);
    const dir = bkDir();
    fs.mkdirSync(dir, { recursive: true });
    const latest = path.join(dir, 'focus-backup-latest.json');
    if (!hasData(next) && fs.existsSync(latest)) {            // never let an empty app overwrite real data
      try { if (hasData(JSON.parse(fs.readFileSync(latest, 'utf8')))) return false; } catch (e) {}
    }
    fs.writeFileSync(latest, json);
    const d = new Date(), pad = n => String(n).padStart(2, '0');
    fs.writeFileSync(path.join(dir, 'focus-backup-' + d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + '.json'), json);
    const dated = fs.readdirSync(dir).filter(f => /^focus-backup-\d{4}-\d{2}-\d{2}\.json$/.test(f)).sort();
    dated.slice(0, Math.max(0, dated.length - 30)).forEach(f => fs.unlinkSync(path.join(dir, f))); // keep 30 days
    return true;
  } catch (e) { return false; }
});
ipcMain.handle('still:findBackup', async () => {
  const found = [];
  const look = (dir, re) => {
    try { fs.readdirSync(dir).filter(f => re.test(f)).forEach(f => { const p = path.join(dir, f); found.push({ p, t: fs.statSync(p).mtimeMs }); }); } catch (e) {}
  };
  look(bkDir(), /^focus-backup-.*\.json$/i);
  look(app.getPath('downloads'), /^(focus|still)-backup-.*\.json$/i);  // backups saved from the old browser version
  let best = null; // newest by the save time written inside each backup
  for (const f of found) {
    try {
      const j = JSON.parse(fs.readFileSync(f.p, 'utf8'));
      if (!hasData(j)) continue;
      const when = Date.parse(j.savedAt) || f.t;
      if (!best || when > best.when) best = { when, json: JSON.stringify(j), file: path.basename(f.p) };
    } catch (e) {}
  }
  return best && { json: best.json, file: best.file };
});

app.whenReady().then(() => { ensureStartMenuShortcut(); createWindow(); });
app.on('second-instance', () => {
  if (codeStamp() !== startStamp) { // Focus was updated while running: restart with the new version (the sit is saved)
    log('update found: restarting');
    quitting = true;
    if (island) { island.destroy(); island = null; }
    app.relaunch(); app.exit(0);
    return;
  }
  openWindow();
});
app.on('before-quit', () => { quitting = true; if (island) { island.destroy(); island = null; } });
app.on('window-all-closed', () => { if (!sit || quitting) app.quit(); }); // a running sit keeps Focus in the tray (and the pill)
