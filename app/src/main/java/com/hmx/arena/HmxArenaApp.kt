package com.hmx.arena

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HmxArenaApp : Application(), ImageLoaderFactory {

    /**
     * Smaller, bounded image caches than Coil's defaults.
     *
     * The app only renders avatars, tournament banners and evidence previews,
     * all of which are decoded to their on-screen size by Coil. Capping the
     * memory cache (10% on low-memory devices, 15% otherwise) and the disk
     * cache at 32 MB keeps a 4 GB device comfortable without changing any
     * visual behaviour. Crossfade is disabled because list scrolling already
     * has enough motion and an extra animation on every row is wasteful on
     * low-end GPUs.
     */
    override fun newImageLoader(): ImageLoader {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowMemory = activityManager?.isLowRamDevice == true ||
            (activityManager?.memoryClass ?: 512) <= 256
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (lowMemory) 0.10 else 0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("hmx_images"))
                    .maxSizeBytes(32L * 1024L * 1024L)
                    .build()
            }
            .crossfade(false)
            .build()
    }
}
