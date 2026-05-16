package com.watchtogether.data.repository

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionHistoryManagerTest {

    @Test
    fun `ConnectionHistoryEntry stores correct data`() {
        val entry = ConnectionHistoryEntry(
            deviceName = "Test Device",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            lastConnected = 1234567890L
        )
        assertEquals("Test Device", entry.deviceName)
        assertEquals("AA:BB:CC:DD:EE:FF", entry.deviceAddress)
        assertEquals(1234567890L, entry.lastConnected)
    }

    @Test
    fun `ConnectionHistoryEntry equality works correctly`() {
        val entry1 = ConnectionHistoryEntry("Device A", "AA:BB:CC:DD:EE:FF", 1000L)
        val entry2 = ConnectionHistoryEntry("Device A", "AA:BB:CC:DD:EE:FF", 1000L)
        assertEquals(entry1, entry2)
    }

    @Test
    fun `ConnectionHistoryEntry copy works correctly`() {
        val entry = ConnectionHistoryEntry("Device A", "AA:BB:CC:DD:EE:FF", 1000L)
        val copied = entry.copy(deviceName = "Device B")
        assertEquals("Device B", copied.deviceName)
        assertEquals("AA:BB:CC:DD:EE:FF", copied.deviceAddress)
    }

    @Test
    fun `JSON serialization roundtrip for history entry`() {
        val original = ConnectionHistoryEntry("My Phone", "11:22:33:44:55:66", 9876543210L)

        val json = JSONObject().apply {
            put("name", original.deviceName)
            put("address", original.deviceAddress)
            put("timestamp", original.lastConnected)
        }

        val restored = ConnectionHistoryEntry(
            deviceName = json.getString("name"),
            deviceAddress = json.getString("address"),
            lastConnected = json.getLong("timestamp")
        )

        assertEquals(original, restored)
    }

    @Test
    fun `JSON array serialization for multiple entries`() {
        val entries = listOf(
            ConnectionHistoryEntry("Device A", "AA:BB:CC:DD:EE:FF", 1000L),
            ConnectionHistoryEntry("Device B", "11:22:33:44:55:66", 2000L)
        )

        val array = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject().apply {
                put("name", entry.deviceName)
                put("address", entry.deviceAddress)
                put("timestamp", entry.lastConnected)
            }
            array.put(obj)
        }

        val restored = (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ConnectionHistoryEntry(
                deviceName = obj.getString("name"),
                deviceAddress = obj.getString("address"),
                lastConnected = obj.getLong("timestamp")
            )
        }

        assertEquals(entries.size, restored.size)
        assertEquals(entries[0], restored[0])
        assertEquals(entries[1], restored[1])
    }

    @Test
    fun `empty JSON array produces empty list`() {
        val array = JSONArray()
        val entries = (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ConnectionHistoryEntry(
                deviceName = obj.getString("name"),
                deviceAddress = obj.getString("address"),
                lastConnected = obj.getLong("timestamp")
            )
        }
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `entry with special characters in name`() {
        val entry = ConnectionHistoryEntry(
            deviceName = "Device \"Special\" & <Test>",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            lastConnected = 1000L
        )

        val json = JSONObject().apply {
            put("name", entry.deviceName)
            put("address", entry.deviceAddress)
            put("timestamp", entry.lastConnected)
        }

        val restored = ConnectionHistoryEntry(
            deviceName = json.getString("name"),
            deviceAddress = json.getString("address"),
            lastConnected = json.getLong("timestamp")
        )

        assertEquals(entry.deviceName, restored.deviceName)
    }
}
