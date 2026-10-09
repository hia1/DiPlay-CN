package com.shilapi.xcertplay

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Runtime-permission gates for the platform calls that throw [SecurityException] when their
 * runtime permission is denied. Head units enforce these more strictly than phones, so every
 * accessor is total: it returns a safe fallback instead of letting the denial escape.
 *
 * Centralising the try/catch keeps callers free of scattered permission checks and lets lint see
 * that the SecurityException is handled.
 */
object PlatformPermissions {
    fun bluetoothConnect(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            granted(context, Manifest.permission.BLUETOOTH_CONNECT)

    fun nearbyWifiDevices(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(context, Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun recordAudio(context: Context): Boolean = granted(context, Manifest.permission.RECORD_AUDIO)

    fun granted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun bondedDevices(adapter: BluetoothAdapter?): Set<BluetoothDevice> {
        val target = adapter ?: return emptySet()
        return try {
            target.bondedDevices.orEmpty()
        } catch (_: SecurityException) {
            emptySet()
        }
    }

    fun deviceName(device: BluetoothDevice?): String? {
        val target = device ?: return null
        return try {
            target.name
        } catch (_: SecurityException) {
            null
        }
    }

    fun deviceAddress(device: BluetoothDevice?): String? {
        val target = device ?: return null
        return try {
            target.address
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * The local adapter address needs the privileged LOCAL_MAC_ADDRESS permission on API 31+,
     * which an ordinary app never holds. The call therefore falls back to the Settings value for
     * the caller; the SecurityException path is expected, not an error.
     */
    @SuppressLint("MissingPermission")
    fun adapterAddress(adapter: BluetoothAdapter?): String? {
        val target = adapter ?: return null
        return try {
            target.address
        } catch (_: SecurityException) {
            null
        }
    }
}
