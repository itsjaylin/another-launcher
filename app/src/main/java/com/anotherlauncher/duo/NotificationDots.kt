package com.anotherlauncher.duo

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Packages with at least one live notification, for home/dock/library dots. Same process, no IPC. */
class NotificationDotsService : NotificationListenerService() {
    override fun onListenerConnected() = publish()
    override fun onNotificationPosted(notification: StatusBarNotification?) = publish()
    override fun onNotificationRemoved(notification: StatusBarNotification?) = publish()
    override fun onSilentStatusBarIconsVisibilityChanged(hideSilentStatusIcons: Boolean) = publish()
    private fun publish() {
        mutableActive.value = try {
            activeNotifications.mapNotNullTo(mutableSetOf()) { it.packageName }
        } catch (_: SecurityException) { emptySet() }
    }
    companion object {
        private val mutableActive = MutableStateFlow<Set<String>>(emptySet())
        val active: StateFlow<Set<String>> = mutableActive.asStateFlow()
    }
}

fun Context.notificationDotsGranted(): Boolean =
    androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
