# Privacy Policy for Another Launcher

**Effective Date:** October 12, 2026
**Application:** Another Launcher (`com.wiselion.anotherlauncher`)

Another Launcher is committed to providing a fluid, modern, foldable-first home screen experience while strictly protecting your privacy. This policy describes the data the launcher accesses, stores locally, and sends when you use optional services.

---

## 1. Summary & Core Privacy Principles

- **Zero Analytics & Zero Telemetry:** Another Launcher contains no third-party tracking libraries, analytics SDKs, advertising frameworks, or crash reporting telemetry.
- **Local-First Execution:** All launcher logic, app categorization, search matching, and personalization run entirely on your local device.
- **No Account Required:** You do not need to register, create an account, or log in to use Another Launcher.
- **Zero Data Monetization:** We never sell, rent, monetize, or share your personal data with third-party data brokers or marketing services.

---

## 2. Sensitive Permissions & Data Handling

Another Launcher only requests Android permissions that are strictly necessary to deliver specific, user-facing launcher features. Every sensitive permission operates on an explicit opt-in basis and includes prominent in-app disclosure prior to the system runtime prompt.

### A. Device Location (`ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`)
- **Purpose:** Used exclusively to retrieve current weather forecasts and temperatures for the Hub (Page 0) weather widget.
- **Data Accessed:** Approximate or precise latitude and longitude, depending on the permission you choose in Android. Both choices work; you can also deny location access and select a city manually.
- **Handling:** The selected city and coordinates are saved in private app settings, used for weather refreshes, and included in exported JSON backups. Coordinates are sent to Open-Meteo for forecasts. Android's system geocoding service may resolve device coordinates to a city name; its provider and data handling depend on your device.
- **Safeguards:** The launcher does not continuously track your movements or keep location history. Denying or revoking location permission prevents new device-location lookups, but weather can still use the previously selected coordinates. Choose another city in Settings to replace them. Location is not used for advertising or profiling by the launcher.

### B. Contacts (`READ_CONTACTS`)
- **Purpose:** Enables finding and calling contacts directly from the App Library search bar.
- **Data Accessed:** Contact names, lookup keys, and phone numbers.
- **Handling:** Search queries are processed strictly on-device using local Android `ContactsContract` queries.
- **Safeguards:** Your contacts and address book are never uploaded to any cloud server, never transmitted off-device, and never shared with third parties.

### C. Phone State & Telephony (`READ_PHONE_STATE`)
- **Purpose:** Enables displaying real-time cellular signal strength dots (0 to 4 dots) and mobile network type (e.g., 5G+, 5G, 4G, LTE) in the Duo Status Pill.
- **Data Accessed:** Network type and signal strength levels reported by Android telephony APIs.
- **Safeguards:** No phone numbers, subscriber IDs (IMSI), IMEI, call logs, or personal carrier account data are ever accessed, stored, or transmitted.

### D. Notification Listener Access (`BIND_NOTIFICATION_LISTENER_SERVICE`)
- **Purpose:** Displays red unread alert dots on app icons with active notifications.
- **Data Accessed:** Package names with active unread status bar notifications.
- **Safeguards:** Notification message contents, message text, sender details, and sensitive alert data are never read, stored, logged, or transmitted off-device.

### E. Installed Applications (`<queries>` declaration)
- **Purpose:** Discovers installed applications to populate your home screen grid, App Library, and categories.
- **Handling:** Uses standard Android package manager queries matching `ACTION_MAIN` and `CATEGORY_LAUNCHER`.
- **Safeguards:** Your list of installed applications is never transmitted off your device.

---

## 3. Third-Party Services & Network Requests

Another Launcher makes network requests only for explicitly requested external services:

1. **Open-Meteo Weather and City Search (`api.open-meteo.com`, `geocoding-api.open-meteo.com`):**
   - Forecast requests send the selected latitude and longitude. Manual city searches send the city query to Open-Meteo's geocoding API.
   - Requests do not include a launcher account, advertising identifier, contact list, or installed-app list. The receiving service can see the request IP address.
   - Open-Meteo states that it may collect IP addresses for operational reasons and keep troubleshooting logs containing coordinates for up to 90 days. Its handling is governed by [Open-Meteo's Terms and Privacy](https://open-meteo.com/en/terms).
   - Android's system geocoding service may separately resolve device coordinates to a city name, depending on your device's provider.

2. **Web Search (User-Initiated):**
   - When you choose a web search action from the Hub, Another Launcher opens your browser at the selected provider (Google, DuckDuckGo, Brave, or Startpage) with your query in the URL. The search happens outside Another Launcher and is governed by that provider's privacy policy. App, contact, and Settings matching remains on your device.

---

## 4. Data Storage, Portability & Deletion

- **Local Storage:** All launcher configuration data (page layouts, widget placements, folder assignments, pinned favorites, and category overrides) is stored in private local Android DataStore files in your app sandbox (`/data/data/com.wiselion.anotherlauncher`).
- **Backup & Portability:** You can export your full configuration to a human-readable JSON backup file via Android's Storage Access Framework (**Settings → Backup and restore**).
- **Data Deletion:** You can reset your layout and settings at any time in Settings. Uninstalling removes the launcher's private app data. Exported backup files and wallpapers applied to Android remain until you delete or replace them. Backups may contain the selected weather city and coordinates; keep them private or delete them when no longer needed.

---

## 5. Children's Privacy

Another Launcher is a general-audience home screen launcher for Android devices and does not target or knowingly collect any personal information from children under the age of 13 (or local minimum legal age).

---

## 6. Security

Because Another Launcher operates without external user accounts or remote database servers, your data is inherently protected by Android's application sandbox and Linux file permissions. We follow Android security best practices, enforce strict component export boundaries, and minimize permission scope.

---

## 7. Changes to This Privacy Policy

We may update this Privacy Policy from time to time to reflect new features or platform requirements. Any updates will be reflected in this document and within the in-app privacy policy viewer.

---

## 8. Contact & Questions

If you have questions, feedback, or concerns regarding this Privacy Policy or Another Launcher's data practices, please reach out via the developer support channels provided on the application's store listing.
