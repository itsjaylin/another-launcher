# Privacy Policy for Another Launcher

**Effective Date:** October 12, 2026

**Application:** Another Launcher (`com.wiselion.anotherlauncher`)

This policy explains what the launcher accesses, saves on your device, and sends to optional services.

---

## 1. Summary & Core Privacy Principles

- **No analytics or telemetry:** No tracking libraries, ads, analytics SDKs, or crash-reporting telemetry.
- **Local processing:** App categories, local search, and personalization run on your device.
- **No account:** No registration or login is required.
- **No data sales:** We do not sell, rent, or share personal data with data brokers or marketing services.

---

## 2. Sensitive Permissions & Data Handling

Permissions are optional and tied to specific features. The app explains each use inline before opening Android's permission prompt or access settings.

### A. Device Location (`ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`)

- **Purpose:** Weather on the Hub.
- **Data:** Approximate or precise coordinates, depending on your Android permission choice. You can deny access and choose a city manually.
- **Handling:** The selected city and coordinates are saved in private settings and included in exported JSON backups. Forecast requests send coordinates to Open-Meteo. Android's geocoding service may resolve coordinates to a city; its provider and data handling depend on your device.
- **Limits:** The launcher does not continuously track you, keep location history, or use location for advertising/profiling. Revoking permission stops new device lookups, but weather can still use saved coordinates. Choose another city to replace them.

### B. Contacts (`READ_CONTACTS`)

- **Purpose:** Find and call contacts from App Library search.
- **Data:** Names, lookup keys, and phone numbers.
- **Handling:** Queries use Android's local `ContactsContract`. The launcher does not upload, transmit, or share your address book.

### C. Phone State & Telephony (`READ_PHONE_STATE`)

- **Purpose:** Show cellular signal dots and network type in the status pill.
- **Data:** Android-reported network type and signal strength.
- **Limits:** The launcher does not access, store, or transmit phone numbers, IMSI, IMEI, call logs, or carrier account data for this feature.

### D. Notification Listener Access (`BIND_NOTIFICATION_LISTENER_SERVICE`)

- **Purpose:** Show red dots on apps with active notifications.
- **Data:** Package names from active notifications.
- **Limits:** The launcher does not read, store, log, or transmit notification text, sender details, or message contents.

### E. Installed Applications (`<queries>` declaration)

- **Purpose:** Populate home pages, the App Library, and categories.
- **Handling:** Standard Android queries find activities matching `ACTION_MAIN` and `CATEGORY_LAUNCHER`. The installed-app list stays on your device.

---

## 3. Third-Party Services & Network Requests

### Open-Meteo weather and city search

Forecasts use `api.open-meteo.com`; manual city search uses `geocoding-api.open-meteo.com`.

- Forecasts send selected coordinates; city searches send the entered city query.
- Requests contain no launcher account, advertising ID, contacts, or app list. The service can see your IP address.
- Open-Meteo states it may collect IP addresses for operations and retain troubleshooting logs containing coordinates for up to 90 days. See [its terms and privacy policy](https://open-meteo.com/en/terms).
- Android's geocoding provider may separately receive coordinates to resolve a city name.

### Web search

Choosing a Web action in the App Library opens your browser with the query in the selected provider's URL: Google, DuckDuckGo, Brave, or Startpage. That provider's policy applies. App, contact, and Settings matching stays on-device.

---

## 4. Data Storage, Portability & Deletion

- **Storage:** Layouts, widgets, folders, favorites, categories, and settings live in private Android DataStore files under the app sandbox (`/data/data/com.wiselion.anotherlauncher`).
- **Export:** **Settings → Backup and restore** saves configuration as JSON through Android's Storage Access Framework.
- **Deletion:** Settings can reset layouts/settings; uninstalling removes private app data. Exported files and applied wallpapers remain until you delete or replace them. Backups can include weather coordinates; keep them private and delete them when no longer needed.

---

## 5. Children's Privacy

Another Launcher is a general-audience app. It does not target or knowingly collect personal information from children under 13, or the local minimum legal age.

---

## 6. Security

There are no launcher accounts or remote user databases. Android's app sandbox and file permissions protect local data. The project limits permission scope and exported components and follows Android security practices.

---

## 7. Changes to This Privacy Policy

Updates for new features or platform requirements will appear here and in the in-app policy viewer.

---

## 8. Contact & Questions

For privacy questions, use the [project's support page](https://github.com/itsjaylin/another-launcher/issues). Do not post private data in a public issue.
