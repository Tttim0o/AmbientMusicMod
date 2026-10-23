package com.kieronquinn.app.ambientmusicmod.repositories

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.kieronquinn.app.ambientmusicmod.BuildConfig
import com.kieronquinn.app.ambientmusicmod.PACKAGE_NAME_GSB
import com.kieronquinn.app.ambientmusicmod.PACKAGE_NAME_PAM
import com.kieronquinn.app.ambientmusicmod.utils.extensions.isAppInstalled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

interface DiagnosticsRepository {

    /**
     *  Builds a plain text report of the things setup depends on, for users to read or paste
     *  into a bug report
     */
    suspend fun buildReport(): String

}

class DiagnosticsRepositoryImpl(
    private val context: Context,
    private val shizukuServiceRepository: ShizukuServiceRepository,
    private val accessibilityRepository: AccessibilityRepository
): DiagnosticsRepository {

    companion object {
        private const val PACKAGE_NAME_SHIZUKU = "moe.shizuku.privileged.api"
        private const val SERVICE_TIMEOUT = 15000L

        private val PAM_AUTHORITIES = listOf(
            "com.google.android.as.pam.ambientmusic.settings",
            "com.google.android.as.pam.ambientmusic.leveldbprovider",
            "com.google.android.as.pam.ambientmusic.historyprovider",
            "com.google.android.as.pam.ambientmusic.backuprestoreprovider"
        )
    }

    private val packageManager = context.packageManager

    override suspend fun buildReport(): String = withContext(Dispatchers.IO) {
        buildString {
            appendLine("Ambient Music Mod ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.DISPLAY}")
            appendLine()
            appendShizuku()
            appendLine()
            appendLine("[Apps]")
            appendCheck("Pixel Ambient Music installed", packageManager.isAppInstalled(PACKAGE_NAME_PAM))
            if(packageManager.isAppInstalled(PACKAGE_NAME_PAM)) {
                val signaturesMatch = packageManager.checkSignatures(
                    context.packageName, PACKAGE_NAME_PAM
                ) == PackageManager.SIGNATURE_MATCH
                appendCheck("Pixel Ambient Music signed with the same key", signaturesMatch)
                if(!signaturesMatch) {
                    appendLine("  -> Both apps must be signed with the same key, install the matching Pixel Ambient Music build")
                }
            }
            appendCheck("Google app installed (On Demand)", packageManager.isAppInstalled(PACKAGE_NAME_GSB))
            appendLine()
            appendLine("[Providers]")
            PAM_AUTHORITIES.forEach {
                appendCheck(it.removePrefix("com.google.android.as.pam.ambientmusic."), canResolveProvider(it))
            }
            appendLine()
            appendLine("[Permissions]")
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appendCheck("Notifications", hasPermission(Manifest.permission.POST_NOTIFICATIONS))
            }
            appendCheck("Accessibility (lock screen)", accessibilityRepository.enabled.first())
        }.trim()
    }

    private suspend fun StringBuilder.appendShizuku() {
        appendLine("[Shizuku / Sui]")
        val shizukuInstalled = packageManager.isAppInstalled(PACKAGE_NAME_SHIZUKU)
        appendCheck("Shizuku app installed", shizukuInstalled)
        val binderAlive = Shizuku.pingBinder()
        appendCheck("Binder received", binderAlive)
        if(!binderAlive) {
            if(!shizukuInstalled) {
                appendLine("  -> Install Shizuku, or Sui if rooted")
            }else{
                appendLine("  -> Start Shizuku (it needs restarting after every reboot unless rooted)")
            }
            return
        }
        val uid = runCatching { Shizuku.getUid() }.getOrNull()
        val mode = when(uid) {
            0 -> "root"
            2000 -> "adb"
            null -> "unknown"
            else -> "uid $uid"
        }
        //Sui provides the Shizuku binder without the Shizuku app being installed
        val provider = if(!shizukuInstalled) "Sui" else "Shizuku"
        val version = runCatching { Shizuku.getVersion() }.getOrNull()
        appendLine("  Provider: $provider, running as $mode, API ${version ?: "unknown"}")
        val permission = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrNull()
        appendCheck("Permission granted", permission)
        if(permission != true) return
        val compatible = withTimeoutOrNull(SERVICE_TIMEOUT) {
            shizukuServiceRepository.runWithService { it.isCompatible }.unwrap()
        }
        if(compatible == null){
            appendCheck("Service bound", false)
            appendLine("  -> Shizuku is running but the service did not start, try restarting Shizuku")
        }else{
            appendCheck("Service bound", true)
            appendCheck("Recognition supported", compatible)
            if(!compatible && uid != 0) {
                appendLine("  -> This device needs root: use Sui, or start Shizuku as root")
            }
        }
    }

    private fun canResolveProvider(authority: String): Boolean {
        return try {
            packageManager.resolveContentProvider(authority, 0) != null
        }catch (e: Exception){
            false
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun StringBuilder.appendCheck(label: String, ok: Boolean?) {
        val mark = when(ok) {
            true -> "OK  "
            false -> "FAIL"
            null -> "?   "
        }
        appendLine("  $mark $label")
    }

}
