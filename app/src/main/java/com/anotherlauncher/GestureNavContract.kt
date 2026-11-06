package com.anotherlauncher

import android.content.ComponentName
import android.content.Intent
import android.graphics.RectF
import android.os.Bundle
import android.os.Message
import android.os.UserHandle
import android.util.Log

/**
 * Encapsulates the handshake protocol between SystemUI / Quickstep gesture navigation and the Launcher.
 * When gesture navigation triggers a return-to-home action, the system provides a callback in the intent.
 * Promptly replying informs the system gesture controller that the launcher is active, preventing
 * gesture timeouts or animation cancellation.
 */
class GestureNavContract(
    val componentName: ComponentName?,
    val user: UserHandle?,
    private val callback: Message
) {
    fun sendEndPosition(position: RectF? = null) {
        val result = Bundle()
        if (position != null) {
            result.putParcelable(EXTRA_ICON_POSITION, position)
        }
        val reply = Message.obtain()
        reply.copyFrom(callback)
        reply.data = result
        try {
            callback.replyTo?.send(reply)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reply to gesture nav contract", e)
        }
    }

    companion object {
        private const val TAG = "GestureNavContract"
        const val EXTRA_GESTURE_CONTRACT = "gesture_nav_contract_v1"
        const val EXTRA_ICON_POSITION = "gesture_nav_contract_icon_position"
        const val EXTRA_REMOTE_CALLBACK = "android.intent.extra.REMOTE_CALLBACK"

        @Suppress("DEPRECATION")
        fun fromIntent(intent: Intent): GestureNavContract? {
            val extras = intent.getBundleExtra(EXTRA_GESTURE_CONTRACT) ?: return null
            intent.removeExtra(EXTRA_GESTURE_CONTRACT)
            val componentName = extras.getParcelable<ComponentName>(Intent.EXTRA_COMPONENT_NAME)
            val userHandle = extras.getParcelable<UserHandle>(Intent.EXTRA_USER)
            val callback = extras.getParcelable<Message>(EXTRA_REMOTE_CALLBACK)
            if (callback != null && callback.replyTo != null) {
                return GestureNavContract(componentName, userHandle, callback)
            }
            return null
        }
    }
}
