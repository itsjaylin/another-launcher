package com.anotherlauncher

import com.anotherlauncher.duo.DeviceStatus
import org.junit.Assert.*
import org.junit.Test

class DeviceStatusTest {

    @Test
    fun defaultValuesAreSafe() {
        val status = DeviceStatus()
        assertNull(status.battery)
        assertFalse(status.charging)
        assertFalse(status.wifiConnected)
        assertFalse(status.wifiEnabled)
        assertNull(status.wifiLevel)
        assertFalse(status.cellularConnected)
        assertNull(status.cellularLevel)
        assertNull(status.networkType)
        assertFalse(status.airplaneMode)
        assertFalse(status.powerSaveMode)
        assertFalse(status.phonePermissionGranted)
    }

    @Test
    fun copyMaintainsIntegrity() {
        val original = DeviceStatus(
            battery = 85,
            charging = true,
            wifiConnected = false,
            cellularConnected = true,
            cellularLevel = 4,
            networkType = "5G+",
            phonePermissionGranted = true
        )

        val updated = original.copy(
            cellularLevel = 3,
            networkType = "5G"
        )

        assertEquals(85, updated.battery)
        assertTrue(updated.charging)
        assertFalse(updated.wifiConnected)
        assertTrue(updated.cellularConnected)
        assertEquals(3, updated.cellularLevel)
        assertEquals("5G", updated.networkType)
        assertTrue(updated.phonePermissionGranted)
    }

    @Test
    fun cellularNetworkFormatting() {
        val wifiActive = DeviceStatus(
            wifiConnected = true,
            cellularConnected = true,
            networkType = "5G",
            cellularLevel = 4
        )
        // When Wi-Fi is connected, networkType is available in status for popup and dialog
        assertEquals("5G", wifiActive.networkType)

        val cellularActive = DeviceStatus(
            wifiConnected = false,
            cellularConnected = true,
            networkType = "5G+",
            cellularLevel = 4
        )
        assertEquals("5G+", cellularActive.networkType)
        assertEquals(4, cellularActive.cellularLevel)
    }
}
