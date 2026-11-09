package com.anotherlauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Dedicated launcher entrypoint for Another Launcher Settings / homescreen options.
 *
 * Appears in the app library and system launcher app list with CATEGORY_LAUNCHER.
 * When launched, routes immediately to LauncherActivity to present the full
 * homescreen options surface and terminates without visual flicker.
 */
class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, LauncherActivity::class.java).apply {
            action = LauncherActivity.ACTION_SHOW_SETTINGS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }
}
