package com.smart.jewelry.app.ble

import android.bluetooth.BluetoothGattService
import java.util.UUID

sealed interface BleEvent {

    data class DeviceFound(
        val name: String?,
        val address: String,
        val rssi: Int,
    ) : BleEvent

    data class ServicesDiscovered(
        val services: List<BluetoothGattService>,
    ) : BleEvent

    data class NotificationReceived(
        val characteristicUuid: UUID,
        val bytes: ByteArray,
    ) : BleEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is NotificationReceived) return false
            return characteristicUuid == other.characteristicUuid && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * characteristicUuid.hashCode() + bytes.contentHashCode()
    }

    data class ScanFailed(
        val errorCode: Int,
        val reason: String,
    ) : BleEvent
}
