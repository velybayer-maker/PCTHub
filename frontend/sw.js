/* PCTHub service worker: caches static files only.
   Pages that contain student data (index.php) are never cached. */
const CACHE = 'pcthub-static-v2';
const STATIC = ['offline.html','assets/style.css','assets/logo.png','assets/favicon.png','assets/icon-192.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(STATIC)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  if (req.mode === 'navigate') {
    e.respondWith(fetch(req).catch(() => caches.match('offline.html')));
    return;
  }
  const url = new URL(req.url);
  if (url.origin === location.origin && url.pathname.includes('/assets/')) {
    // stale-while-revalidate: show the cached copy fast, refresh it in the background
    e.respondWith(caches.open(CACHE).then(cache => cache.match(req).then(hit => {
      const fresh = fetch(req).then(res => { if (res.ok) cache.put(req, res.clone()); return res; }).catch(() => hit);
      return hit || fresh;
    })));
  }
});
