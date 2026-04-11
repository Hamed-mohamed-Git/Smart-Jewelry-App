package com.smart.jewelry.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.Singleton
import java.util.UUID

@Singleton
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        private const val TARGET_DEVICE_NAME = "MyMicrocontroller"
        private const val TARGET_DEVICE_MAC = "10:00:3b:d0:d3:c2"

        private val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
        private val CHARACTERISTIC_UUID = UUID.fromString("abcdefab-1234-1234-1234-abcdefabcdef")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val SCAN_TIMEOUT_MS = 15_000L
    }

    private val bluetoothAdapter = context
        .getSystemService(BluetoothManager::class.java)
        ?.adapter

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /**
     * Starts a full BLE session: scan → connect → discover services → subscribe.
     *
     * Collect the returned [Flow] to receive [BleEvent]s. Cancelling the
     * collection tears down the entire session (stops scan, disconnects GATT).
     */
    fun session(): Flow<BleEvent> = callbackFlow {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            _connectionState.value = ConnectionState.Error("BLE scanner unavailable")
            close()
            return@callbackFlow
        }

        var gatt: android.bluetooth.BluetoothGatt? = null
        var scanStopped = false

        val gattCallback = createGattCallback(
            connectionState = _connectionState,
            channel = channel,
            onEnableNotifications = { g ->
                g.enableNotifications(SERVICE_UUID, CHARACTERISTIC_UUID, CCCD_UUID, _connectionState)
            },
        )

        val scanCallback = object : ScanCallback() {

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                if (isTargetDevice(device)) {
                    scanStopped = true
                    scanner.stopScan(this)
                    trySend(BleEvent.DeviceFound(device.name, device.address, result.rssi))
                    _connectionState.value = ConnectionState.Connecting(device.address)
                    gatt = device.connectGatt(
                        context, false, gattCallback, BluetoothDevice.TRANSPORT_LE
                    )
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                scanStopped = true
                val reason = errorCode.toScanErrorReason()
                _connectionState.value = ConnectionState.Error("Scan failed: $reason")
                trySend(BleEvent.ScanFailed(errorCode, reason))
                channel.close()
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        _connectionState.value = ConnectionState.Scanning
        scanner.startScan(emptyList(), settings, scanCallback)

        launch {
            delay(SCAN_TIMEOUT_MS)
            if (_connectionState.value is ConnectionState.Scanning) {
                scanStopped = true
                scanner.stopScan(scanCallback)
                _connectionState.value = ConnectionState.Error("Scan timed out")
                channel.close()
            }
        }

        awaitClose {
            if (!scanStopped) scanner.stopScan(scanCallback)
            gatt?.disconnect()
            gatt?.close()
            _connectionState.value = ConnectionState.Idle
        }
    }

    private fun isTargetDevice(device: BluetoothDevice): Boolean = when {
        TARGET_DEVICE_MAC.isNotBlank() -> device.address.equals(TARGET_DEVICE_MAC, ignoreCase = true)
        else -> device.name.equals(TARGET_DEVICE_NAME, ignoreCase = true)
    }
}
