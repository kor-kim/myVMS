package com.myvms.app

import android.content.Context
import org.json.JSONArray

object DeviceStorage {
    private const val PREF_NAME = "myVMS_devices"
    private const val KEY_DEVICES = "devices"

    fun loadDevices(context: Context): MutableList<Device> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_DEVICES, null)
        if (json.isNullOrEmpty()) return mutableListOf()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { Device.fromJson(arr.getJSONObject(it)) }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
    }

    fun saveDevices(context: Context, devices: List<Device>) {
        val arr = JSONArray()
        devices.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEVICES, arr.toString()).apply()
    }

    fun addDevice(context: Context, device: Device) {
        val list = loadDevices(context)
        list.add(device)
        saveDevices(context, list)
    }

    fun removeDevice(context: Context, deviceId: String) {
        val list = loadDevices(context)
        list.removeAll { it.id == deviceId }
        saveDevices(context, list)
    }
}
