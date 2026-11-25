package com.wu.nfccardskin.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri

/** Wallet presence / launch, LSPosed manager, launcher icon, optional root force-stop. */
object WalletOps {

    fun walletVersion(context: Context): String? = try {
        context.packageManager.getPackageInfo(Constants.WALLET_PACKAGE, 0).versionName ?: "?"
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun openWallet(context: Context): Boolean {
        val i = context.packageManager.getLaunchIntentForPackage(Constants.WALLET_PACKAGE) ?: return false
        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun openLsposed(context: Context): Boolean {
        val i = context.packageManager.getLaunchIntentForPackage("org.lsposed.manager") ?: return false
        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** Force-stops the wallet so it reloads card faces. Needs root; returns false without it. */
    fun forceStopWallet(): Boolean = root("am force-stop ${Constants.WALLET_PACKAGE}")

    /**
     * Deletes the wallet's Glide disk cache and force-stops it, so original card faces are
     * downloaded again (needs root). Our own replacements never need this: their URIs carry `?v=`.
     */
    fun clearWalletImageCache(): Boolean = root(
        "rm -rf /data/data/${Constants.WALLET_PACKAGE}/cache/image_manager_disk_cache " +
            "/data/data/${Constants.WALLET_PACKAGE}/cache/*glide* ; am force-stop ${Constants.WALLET_PACKAGE}",
    )

    private fun root(cmd: String): Boolean = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        p.waitFor(8, TimeUnit.SECONDS) && p.exitValue() == 0
    } catch (_: Throwable) {
        false
    }

    fun openUrl(context: Context, url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Throwable) {
        false
    }

    private fun launcher(context: Context) = ComponentName(context, "${context.packageName}.Launcher")

    fun isIconHidden(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(launcher(context)) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun setIconHidden(context: Context, hidden: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            launcher(context),
            if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP,
        )
    }
}
