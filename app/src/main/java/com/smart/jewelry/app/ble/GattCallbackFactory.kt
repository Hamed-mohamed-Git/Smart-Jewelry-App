package com.smart.jewelry.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.os.Build
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

internal fun createGattCallback(
    connectionState: MutableStateFlow<ConnectionState>,
    channel: SendChannel<BleEvent>,
    onEnableNotifications: (BluetoothGatt) -> Boolean,
): BluetoothGattCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    modernGattCallback(connectionState, channel, onEnableNotifications)
} else {
    legacyGattCallback(connectionState, channel, onEnableNotifications)
}

private fun modernGattCallback(
    connectionState: MutableStateFlow<ConnectionState>,
    channel: SendChannel<BleEvent>,
    onEnableNotifications: (BluetoothGatt) -> Boolean,
) = object : BluetoothGattCallback() {

    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) =
        handleConnectionStateChange(connectionState, channel, g, status, newState)

    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) =
        handleServicesDiscovered(connectionState, channel, g, status, onEnableNotifications)

    override fun onCharacteristicChanged(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) = handleNotification(channel, characteristic.uuid, value)

    override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) =
        handleDescriptorWrite(connectionState, channel, status)
}

@Suppress("DEPRECATION")
private fun legacyGattCallback(
    connectionState: MutableStateFlow<ConnectionState>,
    channel: SendChannel<BleEvent>,
    onEnableNotifications: (BluetoothGatt) -> Boolean,
) = object : BluetoothGattCallback() {

    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) =
        handleConnectionStateChange(connectionState, channel, g, status, newState)

    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) =
        handleServicesDiscovered(connectionState, channel, g, status, onEnableNotifications)

    override fun onCharacteristicChanged(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ) = handleNotification(channel, characteristic.uuid, characteristic.value ?: byteArrayOf())

    override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) =
        handleDescriptorWrite(connectionState, channel, status)
}

private fun handleConnectionStateChange(
    connectionState: MutableStateFlow<ConnectionState>,
    channel: SendChannel<BleEvent>,
    g: BluetoothGatt,
    status: Int,
    newState: Int,
) {
    when {
        newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
            connectionState.value = ConnectionState.DiscoveringServices
            g.discoverServices()
        }
        newState == BluetoothProfile.STATE_DISCONNECTED -> {
            connectionState.value = ConnectionState.Disconnected(status)
            channel.close()
        }
        else -> {
            connectionState.value = ConnectionState.Error(
                "Unexpected GATT state: ${newState.toConnectionStateString()} " +
                    "(status=${status.toGattStatusString()})"
            )
            channel.close()
        }
    }
}

private fun handleServicesDiscovered(
    connectionState: MutableStateFlow<ConnectionState>,
    channel: SendChannel<BleEvent>,
    g: BluetoothGatt,
    status: Int,
    onEnableNotifications: (BluetoothGatt) -> Boolean,
) {
    if (status != BluetoothGatt.GATT_SUCCESS) {
        connectionState.value = ConnectionState.Error(
            "Service discovery failed: ${status.toGattStatusString()}"
        )
        channel.close()
        return
    }
    channel.trySend(BleEvent.ServicesDiscovered(g.services))
    if (!onEnableNotifications(g)) {
        channel.close()
    }
}

private fun handleNotification(channel: SendChannel<BleEvent>, uuid: UUID, value: ByteArray) {
    if (value.isNotEmpty()) {
        channel.trySend(BleEvent.NotificationReceived(uuid, value))
    }
}

private fun handleDescriptorWrite(
    connectionState: MutableStateFlow<ConnectionState>,
    channel: SendChannel<BleEvent>,
    status: Int,
) {
    if (status == BluetoothGatt.GATT_SUCCESS) {
        connectionState.value = ConnectionState.Ready
    } else {
        connectionState.value = ConnectionState.Error(
            "Failed to enable notifications: ${status.toGattStatusString()}"
        )
        channel.close()
    }
}

@SuppressLint("MissingPermission")
internal fun BluetoothGatt.enableNotifications(
    serviceUuid: UUID,
    characteristicUuid: UUID,
    cccdUuid: UUID,
    connectionState: MutableStateFlow<ConnectionState>,
): Boolean {
    val characteristic = getService(serviceUuid)
        ?.getCharacteristic(characteristicUuid)
        ?: run {
            connectionState.value = ConnectionState.Error("Target characteristic not found")
            return false
        }

    val props = characteristic.properties
    val supportsNotify = props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
    val supportsIndicate = props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0

    if (!supportsNotify && !supportsIndicate) {
        connectionState.value = ConnectionState.Error("Characteristic does not support notifications")
        return false
    }

    if (!setCharacteristicNotification(characteristic, true)) {
        connectionState.value = ConnectionState.Error("setCharacteristicNotification failed")
        return false
    }

    val cccd = characteristic.getDescriptor(cccdUuid) ?: run {
        connectionState.value = ConnectionState.Error("CCCD descriptor not found")
        return false
    }

    val cccdValue = if (supportsNotify) {
        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
    } else {
        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        writeDescriptor(cccd, cccdValue)
    } else {
        @Suppress("DEPRECATION")
        cccd.value = cccdValue
        @Suppress("DEPRECATION")
        writeDescriptor(cccd)
    }

    return true
}
