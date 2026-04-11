package com.smart.jewelry.app.ble

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Scanning : ConnectionState
    data class Connecting(val address: String) : ConnectionState
    data object DiscoveringServices : ConnectionState
    data object Ready : ConnectionState
    data class Disconnected(val status: Int) : ConnectionState
    data class Error(val message: String) : ConnectionState
}
