package com.anotherlauncher.duo

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceStatus(
    val timeMillis: Long = System.currentTimeMillis(),
    val battery: Int? = null,
    val charging: Boolean = false,
    val wifiConnected: Boolean = false,
    val wifiEnabled: Boolean = false,
    val wifiLevel: Int? = null,
    val cellularConnected: Boolean = false,
    val cellularLevel: Int? = null,
    val networkType: String? = null,
    val airplaneMode: Boolean = false,
    val powerSaveMode: Boolean = false,
    val phonePermissionGranted: Boolean = false,
)

/** Foreground-only telemetry. Unknown levels stay unknown; no invented signal bars. */
class StatusMonitor(private val activity: ComponentActivity) : DefaultLifecycleObserver, AutoCloseable {
    private companion object {
        const val ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED =
            "android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED"
    }
    private val context = activity.applicationContext
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val wifi = context.getSystemService(WifiManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val mutableState = MutableStateFlow(DeviceStatus())
    val state = mutableState.asStateFlow()
    private var active = false
    private var networkRegistered = false
    private var phoneRegistered = false
    private var registeredTelephony: TelephonyManager? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            var next = mutableState.value.copy(timeMillis = System.currentTimeMillis())
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                next = next.copy(
                    battery = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null,
                    charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
                )
            }
            next = next.copy(airplaneMode = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0)
            next = next.copy(
                wifiEnabled = wifi?.isWifiEnabled == true,
                powerSaveMode = power?.isPowerSaveMode == true,
            )
            mutableState.value = next
            if (intent.action == ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED) {
                refreshPhonePermission()
            }
        }
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = updateNetwork(capabilities)
        override fun onLost(network: Network) = updateNetwork(null)
    }

    private val displayCallback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
        override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
            mutableState.value = mutableState.value.copy(
                networkType = formatDisplayInfo(telephonyDisplayInfo)
            )
        }
    }

    private val signalCallback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            val level = extractSignalLevel(signalStrength)
            if (level != null) {
                mutableState.value = mutableState.value.copy(cellularLevel = level)
            }
        }
    }

    init { activity.lifecycle.addObserver(this) }
    override fun onStart(owner: LifecycleOwner) {
        if (active) return
        active = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)
        }
        // Only protected system broadcasts are registered here.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        mutableState.value = mutableState.value.copy(
            timeMillis = System.currentTimeMillis(),
            wifiEnabled = wifi?.isWifiEnabled == true,
            powerSaveMode = power?.isPowerSaveMode == true,
            airplaneMode = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0,
        )
        try {
            updateNetwork(connectivity.getNetworkCapabilities(connectivity.activeNetwork))
            connectivity.registerDefaultNetworkCallback(networkCallback, Handler(Looper.getMainLooper()))
            networkRegistered = true
        } catch (_: SecurityException) { updateNetwork(null) }
        refreshPhonePermission()
    }

    fun refreshPhonePermission() {
        if (phoneRegistered) {
            try { registeredTelephony?.unregisterTelephonyCallback(displayCallback) } catch (_: Exception) {}
            try { registeredTelephony?.unregisterTelephonyCallback(signalCallback) } catch (_: Exception) {}
            phoneRegistered = false
            registeredTelephony = null
        }
        if (!active) return

        val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasPermission = hasPhoneState || hasFineLocation
        mutableState.value = mutableState.value.copy(phonePermissionGranted = hasPermission)

        val subscriptionId = SubscriptionManager.getDefaultDataSubscriptionId()
        val selectedTelephony = telephony?.let { manager ->
            if (SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
                manager.createForSubscriptionId(subscriptionId)
            } else {
                manager
            }
        } ?: return

        registeredTelephony = selectedTelephony

        // 1. TelephonyCallback.DisplayInfoListener does NOT require READ_PHONE_STATE on API 31+
        try {
            selectedTelephony.registerTelephonyCallback(context.mainExecutor, displayCallback)
            phoneRegistered = true
        } catch (_: SecurityException) {}
        catch (_: UnsupportedOperationException) {}

        // 2. Initial network type detection if available
        if (hasPermission) {
            try {
                @Suppress("DEPRECATION")
                val initialType = selectedTelephony.dataNetworkType
                if (initialType != TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                    val formatted = formatNetworkType(initialType)
                    if (formatted != null && mutableState.value.networkType == null) {
                        mutableState.value = mutableState.value.copy(networkType = formatted)
                    }
                }
            } catch (_: SecurityException) {}
        }

        // 3. Initial signal strength and listener registration
        try {
            val initialSignal = extractSignalLevel(selectedTelephony.signalStrength)
            if (initialSignal != null) {
                mutableState.value = mutableState.value.copy(cellularLevel = initialSignal)
            }
            selectedTelephony.registerTelephonyCallback(context.mainExecutor, signalCallback)
            phoneRegistered = true
        } catch (_: SecurityException) {
            // Signal strength listener requires permission; level stays null if denied
        } catch (_: UnsupportedOperationException) {}
    }

    internal fun extractSignalLevel(signalStrength: SignalStrength?): Int? {
        if (signalStrength == null) return null
        // On 5G NSA (Non-Standalone) connections, mNr can be invalid/idle while the primary LTE carrier
        // has full signal. Evaluating max level across all reported cell signal strengths gives the true level.
        val fromCells = signalStrength.cellSignalStrengths
            .map { it.level }
            .filter { it in 0..4 }
            .maxOrNull()
        return (fromCells ?: signalStrength.level).coerceIn(0, 4)
    }

    internal fun formatDisplayInfo(info: TelephonyDisplayInfo): String? {
        return when (info.overrideNetworkType) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G+"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "5G"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> "4G"
            else -> formatNetworkType(info.networkType)
        }
    }

    @Suppress("DEPRECATION")
    internal fun formatNetworkType(networkType: Int): String? {
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "H+"
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE -> "E"
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_GSM -> "2G"
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "1x"
            else -> null
        }
    }

    private fun updateNetwork(capabilities: NetworkCapabilities?) {
        val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val cellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val rssi = (capabilities?.transportInfo as? WifiInfo)?.rssi
        @Suppress("DEPRECATION")
        val level = rssi?.takeIf { it in -126..-1 }?.let { WifiManager.calculateSignalLevel(it, 5) }
        mutableState.value = mutableState.value.copy(
            wifiConnected = wifi,
            wifiLevel = if (wifi) level else null,
            cellularConnected = cellular,
        )
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!active) return
        active = false
        context.unregisterReceiver(receiver)
        if (networkRegistered) connectivity.unregisterNetworkCallback(networkCallback)
        if (phoneRegistered) {
            try { registeredTelephony?.unregisterTelephonyCallback(displayCallback) } catch (_: Exception) {}
            try { registeredTelephony?.unregisterTelephonyCallback(signalCallback) } catch (_: Exception) {}
        }
        networkRegistered = false
        phoneRegistered = false
        registeredTelephony = null
    }

    override fun onDestroy(owner: LifecycleOwner) = close()
    override fun close() { onStop(activity); activity.lifecycle.removeObserver(this) }
}
