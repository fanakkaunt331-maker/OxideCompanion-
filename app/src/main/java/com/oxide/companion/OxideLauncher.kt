package com.oxide.companion

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.oxide.companion.Constants

object OxideLauncher {

    fun isInstalled(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(Constants.OXIDE_PACKAGE_NAME)
            intent != null
        } catch (e: Exception) {
            false
        }
    }

    fun getVersionInfo(context: Context): PackageInfoWrapper? {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(Constants.OXIDE_PACKAGE_NAME, 0)
            PackageInfoWrapper(
                packageName = pkgInfo.packageName,
                versionName = pkgInfo.versionName ?: "unknown",
                versionCode = pkgInfo.longVersionCode.toInt(),
                isInstalled = true
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    fun launch(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(Constants.OXIDE_PACKAGE_NAME)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    data class PackageInfoWrapper(
        val packageName: String,
        val versionName: String,
        val versionCode: Int,
        val isInstalled: Boolean
    )
}