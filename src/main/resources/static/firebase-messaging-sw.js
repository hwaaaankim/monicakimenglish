/*
 * Firebase Messaging compatibility cleanup worker.
 *
 * This project currently does not use Firebase Cloud Messaging, but a browser can
 * retain an old /firebase-messaging-sw.js registration from previous development.
 * Serving this no-op worker prevents a 404 and safely removes that stale registration.
 */
self.addEventListener('install', function () {
  self.skipWaiting();
});

self.addEventListener('activate', function (event) {
  event.waitUntil(self.registration.unregister());
});
