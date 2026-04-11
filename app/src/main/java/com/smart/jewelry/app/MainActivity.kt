package com.smart.jewelry.app

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.smart.jewelry.app.ble.BleEvent
import com.smart.jewelry.app.ble.BleManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SmartJewelry"
    }

    private val bleManager: BleManager by inject()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isEmpty()) {
            Log.d(TAG, "All BLE permissions granted")
            startBleSession()
        } else {
            Log.e(TAG, "BLE permissions denied: $denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isBluetoothReady()) return

        observeBleState()
        requestBlePermissions()
    }

    private fun isBluetoothReady(): Boolean {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        return when {
            adapter == null -> {
                Log.e(TAG, "Device does not support Bluetooth")
                false
            }
            !adapter.isEnabled -> {
                Log.e(TAG, "Bluetooth is disabled — please enable it and relaunch")
                false
            }
            else -> true
        }
    }

    private fun observeBleState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bleManager.connectionState.collect { state ->
                    Log.d(TAG, "Connection state: $state")
                }
            }
        }
    }

    private fun startBleSession() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bleManager.session().collect { event ->
                    when (event) {
                        is BleEvent.DeviceFound ->
                            Log.d(TAG, "Device found: ${event.name} (${event.address}) rssi=${event.rssi}")
                        is BleEvent.ServicesDiscovered ->
                            Log.d(TAG, "Services: ${event.services.map { it.uuid }}")
                        is BleEvent.NotificationReceived -> {
                            val hex = event.bytes.joinToString(" ") { "%02X".format(it) }
                            val text = event.bytes.toString(Charsets.UTF_8)
                            Log.d(TAG, "Data [${event.characteristicUuid}]: $hex  ($text)")
                        }
                        is BleEvent.ScanFailed ->
                            Log.e(TAG, "Scan failed: ${event.reason} (code=${event.errorCode})")
                    }
                }
            }
        }
    }

    private fun requestBlePermissions() {
        val required = blePermissions()
        val allGranted = required.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            Log.d(TAG, "BLE permissions already granted")
            startBleSession()
        } else {
            permissionLauncher.launch(required)
        }
    }

    private fun blePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
}
