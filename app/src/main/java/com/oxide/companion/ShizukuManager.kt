package com.oxide.companion

import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

class ShizukuManager {

    private val _status = MutableStateFlow<ShizukuStatus>(ShizukuStatus.NotInstalled)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _status.value = ShizukuStatus.NotRunning
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            _status.value = determineConnectionType()
        } else {
            _status.value = ShizukuStatus.NoPermission
        }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        updateStatus()
    }

    fun refreshStatus() {
        updateStatus()
    }

    private fun updateStatus() {
        try {
            if (!Shizuku.pingBinder()) {
                _status.value = ShizukuStatus.NotRunning
                return
            }

            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                _status.value = ShizukuStatus.NoPermission
                return
            }

            _status.value = determineConnectionType()
        } catch (e: Exception) {
            _status.value = ShizukuStatus.Error(e.message ?: "Unknown error")
        }
    }

    private fun determineConnectionType(): ShizukuStatus {
        return try {
            val uid = Shizuku.getUid()
            when (uid) {
                0 -> ShizukuStatus.ConnectedRoot
                2000 -> ShizukuStatus.ConnectedAdb
                else -> ShizukuStatus.ConnectedAdb
            }
        } catch (e: Exception) {
            ShizukuStatus.Error("Failed to determine UID: ${e.message}")
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                _status.value = ShizukuStatus.Error(
                    "Permission permanently denied. Grant it manually in Shizuku Manager app."
                )
                return
            }
            Shizuku.requestPermission(Constants.SHIZUKU_REQUEST_CODE)
        } catch (e: Exception) {
            _status.value = ShizukuStatus.Error("Request failed: ${e.message}")
        }
    }

    fun isReady(): Boolean {
        return _status.value.isConnected
    }

    fun cleanup() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }
}