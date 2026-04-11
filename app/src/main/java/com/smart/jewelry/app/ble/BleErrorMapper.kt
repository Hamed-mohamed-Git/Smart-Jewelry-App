package com.smart.jewelry.app.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback

internal fun Int.toScanErrorReason(): String = when (this) {
    ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "APP_REGISTRATION_FAILED"
    ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
    else -> "UNKNOWN($this)"
}

internal fun Int.toGattStatusString(): String = when (this) {
    BluetoothGatt.GATT_SUCCESS -> "SUCCESS"
    BluetoothGatt.GATT_READ_NOT_PERMITTED -> "READ_NOT_PERMITTED"
    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "WRITE_NOT_PERMITTED"
    BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> "INSUFFICIENT_AUTHENTICATION"
    BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> "REQUEST_NOT_SUPPORTED"
    BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> "INSUFFICIENT_ENCRYPTION"
    BluetoothGatt.GATT_INVALID_OFFSET -> "INVALID_OFFSET"
    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "INVALID_ATTRIBUTE_LENGTH"
    BluetoothGatt.GATT_CONNECTION_CONGESTED -> "CONNECTION_CONGESTED"
    BluetoothGatt.GATT_FAILURE -> "FAILURE"
    else -> "UNKNOWN($this)"
}

internal fun Int.toConnectionStateString(): String = when (this) {
    BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
    BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
    BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
    BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
    else -> "UNKNOWN($this)"
}
