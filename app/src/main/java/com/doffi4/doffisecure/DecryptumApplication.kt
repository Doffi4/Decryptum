package com.doffi4.doffisecure

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.doffi4.doffisecure.di.appModule
import com.doffi4.doffisecure.security.VaultWarmup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class DecryptumApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            // Ignore Cache-Control so every cold start reuses the disk cache
            // instead of refetching favicons from the network. VaultWarmup
            // prefetches into this same loader, so the first pass is all hits.
            .respectCacheHeaders(false)
            .build()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            // Diagnostic Koin logging is only useful while developing; on release
            // it would emit a per-resolution line on every cold start.
            if (isDebuggable()) androidLogger() else androidLogger(Level.ERROR)
            androidContext(this@DecryptumApplication)
            modules(appModule)
        }
        startVaultWarmUp()
    }

    /** True for debug-build installs (matches BuildConfig.DEBUG without enabling it). */
    private fun isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * Starts the background warm-up as early as possible - right after Koin is
     * up, i.e. while the lock screen is still being shown. By the time the
     * user finishes the biometric/master-password step, the vault and favicons
     * are already in memory, so the main list appears instantly and scrolls
     * smoothly on the very first pass (no 30 fps "loading" sweep).
     */
    private fun startVaultWarmUp() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                GlobalContext.get().get<VaultWarmup>().warm()
            } catch (t: Throwable) {
                Log.w("Decryptum", "Vault warm-up skipped", t)
            }
        }
    }
}
