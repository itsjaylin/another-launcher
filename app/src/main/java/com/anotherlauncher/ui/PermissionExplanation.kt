package com.anotherlauncher.ui

/** Shown in context before the user's action opens Android's permission prompt or settings. */
internal object PermissionExplanation {
    const val LOCATION = "Use approximate or precise location for weather. Your selected city and coordinates are saved on this phone, included in backups, and sent to Open-Meteo for forecasts. Open-Meteo may log requests; Android geocoding may resolve the city name. No movement tracking. You can choose a city manually instead."
    const val CONTACTS = "Search contact names and phone numbers on this phone and open your dialer. Your contacts stay on this device and are never uploaded."
    const val PHONE_STATE = "Read cellular signal strength and network type for the status pill. This stays on your phone; the launcher does not read phone numbers or call history."
    const val NOTIFICATIONS = "Check which apps have active notifications to show badge dots, including while you use other apps. Message text and sender details aren't read or saved. Badge data stays on this phone."
}
