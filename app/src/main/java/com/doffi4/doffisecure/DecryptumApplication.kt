package com.doffi4.doffisecure

import android.app.Application
import android.content.pm.ApplicationInfo
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.doffi4.doffisecure.di.appModule
import com.doffi4.doffisecure.security.FaviconFetcher
import com.doffi4.doffisecure.security.IcoDecoder
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class DecryptumApplication : Application(), ImageLoaderFactory {

    init {
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: Throwable) {
            android.util.Log.e("DecryptumApp", "Failed to load sqlcipher library", e)
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(FaviconFetcher.Factory(this@DecryptumApplication))
                add(IcoDecoder.Factory())
            }
            // Ignore Cache-Control so favicons downloaded from websites
            // are persistently reused from the local disk cache without re-requesting.
            .respectCacheHeaders(enable = false)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("favicons_cache"))
                    .maxSizeBytes(20L * 1024 * 1024)
                    .build()
            }
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
    }

    /** True for debug-build installs (matches BuildConfig.DEBUG without enabling it). */
    private fun isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
