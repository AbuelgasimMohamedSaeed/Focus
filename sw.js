// Focus service worker: network first (so updates arrive as soon as you're online), cached copy when offline.
const CACHE = 'focus-v1';
const PHOTOS = 'focus-photos'; // background photos, kept across updates
const CORE = ['./', 'index.html', 'manifest.webmanifest', 'icons/icon-192.png', 'icons/icon-512.png', 'icons/icon-maskable-512.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(CORE)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE && k !== PHOTOS).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin === location.origin) {
    // the app itself: try the network, keep the fresh copy, fall back to the saved one offline
    e.respondWith(fetch(req).then(res => {
      if (res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
      return res;
    }).catch(() => caches.match(req).then(r => r || caches.match('index.html'))));
  } else if (url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com') {
    // fonts: saved after the first visit
    e.respondWith(caches.match(req).then(r => r || fetch(req).then(res => {
      const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); return res;
    })));
  } else if ((url.hostname === 'picsum.photos' && url.pathname.startsWith('/id/')) || url.hostname === 'upload.wikimedia.org') {
    // background photos: saved the first time, then they work offline
    e.respondWith(caches.open(PHOTOS).then(c => c.match(req.url).then(r => r || fetch(req).then(res => {
      if (res.ok || res.type === 'opaque') c.put(req.url, res.clone());
      return res;
    }))));
  }
  // everything else (the online Qur'an verses, the photo searches) goes straight to the internet
});
