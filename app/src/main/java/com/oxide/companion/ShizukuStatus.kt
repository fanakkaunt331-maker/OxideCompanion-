package com.oxide.companion

sealed class ShizukuStatus {
    object NotInstalled : ShizukuStatus()
    object NotRunning : ShizukuStatus()
    object NoPermission : ShizukuStatus()
    object ConnectedAdb : ShizukuStatus()
    object ConnectedRoot : ShizukuStatus()
    data class Error(val message: String) : ShizukuStatus()

    val isConnected: Boolean
        get() = this == ConnectedAdb || this == ConnectedRoot

    val displayName: String
        get() = when (this) {
            is NotInstalled -> "Shizuku не установлен"
            is NotRunning -> "Shizuku запущен, но сервис остановлен"
            is NoPermission -> "Нет разрешения для Oxide Companion"
            is ConnectedAdb -> "Подключено через ADB Wireless Debugging"
            is ConnectedRoot -> "Подключено через Root"
            is Error -> "Ошибка: $message"
        }

    val description: String
        get() = when (this) {
            is NotInstalled -> "Установите Shizuku из Google Play или GitHub Releases"
            is NotRunning -> "Откройте Shizuku и активируйте сервис через Wireless Debugging"
            is NoPermission -> "Нажмите 'Запросить разрешение' ниже"
            is ConnectedAdb -> "Сервис работает с привилегиями shell (ADB)"
            is ConnectedRoot -> "Сервис работает с привилегиями root"
            is Error -> message
        }
}