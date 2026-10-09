# Website dezender.de/touran

`index.html` und `assets/` bilden eine eigenständige, responsive deutsche Projektseite. Keine externen Fonts, Tracker oder JavaScript-Bibliotheken. Die Galerie öffnet Bilder im Tastatur-bedienbaren Dialog. Downloadinformationen werden gegen das vorhandene `update.json` abgeglichen.

Die Touran-Grafik ist das vorhandene App-Icon. Die drei SVG-Ansichten sind ausdrücklich gekennzeichnete Nachbildungen des App-Entwicklungsstands 0.7.1; keine Screenshots und keine simulierten Fahrzeugmessungen. Die vorhandene `touran_master_final.webp` ist nicht als Bild dekodierbar, auch nicht im gebauten APK. Ein Foto des tatsächlichen Fahrzeugs und echte Radio-Screenshots sind noch nicht verfügbar.

## Bereitstellung

Die Nutzeranweisung autorisiert die Aktualisierung der Website. In dieser Sitzung ist aber kein Serverzugang oder Deployment-Workflow eingerichtet. Die Datei `/srv/touranradio/upload/inbox` aus einer früheren Sitzung belegt keinen Zugriff in dieser Sitzung und auch nicht den Webroot.

1. Tatsächlichen Webroot für `/touran/` bestimmen und die bisherige `index.html` sichern.
2. `assets/` hochladen und Bild-/CSS-/JS-Abrufe prüfen.
3. `index.html` zuletzt ersetzen, anschließend öffentliche Seite auf Desktop und Mobilgerät prüfen.
4. Bei Problemen die gesicherte `index.html` zurückspielen.

Nur die Website-Dateien veröffentlichen. `api/`, `logs/`, Inbox, APK und `update.json` nicht ersetzen: die öffentliche APK ist laut abgerufenem Manifest weiterhin 0.6.1. Die Veröffentlichung von 0.7.1 benötigt einen getrennten konsistenten APK-/Signatur-/Manifest-Schritt.

Lokale Vorschau:

```sh
python3 -m http.server 8080 --directory server
```

`http://localhost:8080` öffnen. Die statische Downloadinformation bleibt auch ohne HTTPS erreichbar; die serverseitige Paket-URL wird bei einem gültigen Manifest übernommen.
