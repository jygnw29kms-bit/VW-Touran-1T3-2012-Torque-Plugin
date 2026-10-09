'use strict';
const dialog = document.querySelector('#image-dialog');
const dialogImage = document.querySelector('#dialog-image');
for (const button of document.querySelectorAll('[data-image]')) {
  button.addEventListener('click', () => {
    dialogImage.src = button.dataset.image;
    dialogImage.alt = button.querySelector('img').alt;
    document.querySelector('#dialog-title').textContent = button.dataset.title;
    dialog.showModal();
  });
}
document.querySelector('#close-dialog').addEventListener('click', () => dialog.close());
dialog.addEventListener('click', event => { if (event.target === dialog) { const r = dialog.getBoundingClientRect(); if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) dialog.close(); } });
// Describe only the actual published package; never advertise the development APK as deployed.
async function updateDownload() {
  try {
    const response = await fetch('update.json', {cache: 'no-store'});
    if (!response.ok) return;
    const manifest = await response.json();
    if (!/^\d+\.\d+\.\d+$/.test(manifest.versionName) || !Number.isSafeInteger(manifest.versionCode)) return;
    const url = new URL(manifest.apkUrl, location.href);
    const hosts = new Set(['dezender.de', 'www.dezender.de', location.hostname]);
    if (url.protocol !== 'https:' || !hosts.has(url.hostname) || !url.pathname.startsWith('/touran/') || !url.pathname.endsWith('.apk') || url.username || url.password || url.port) return;
    const link = document.querySelector('#apk-download');
    link.href = url.href;
    link.textContent = `APK ${manifest.versionName} herunterladen ↓`;
    const status = document.querySelector('#download-status');
    if (manifest.versionName === '0.6.1') status.textContent = 'Öffentlicher Download: 0.6.1 · ältere Bluetooth-/ELM327-Version. Die hier vorgestellte ESP32-Entwicklung 0.7.1 ist noch nicht als Server-Update veröffentlicht.';
    else if (manifest.versionName === '0.7.1') status.textContent = 'Öffentlicher Download: 0.7.1 · ESP32-/WLAN-Entwicklungsstand. Die Firmware liefert zunächst Status und rohe CAN-Frames; vollständige Fahrzeugdiagnose ist noch in Entwicklung.';
    else status.textContent = `Öffentlicher Download laut Server-Manifest: ${manifest.versionName}. Die hier gezeigten Ansichten beschreiben Entwicklungsstand 0.7.1; aktuelle Änderungen bitte im Projekt prüfen.`;
  } catch { /* The explicit static version remains visible if the manifest cannot be read. */ }
}
updateDownload();
