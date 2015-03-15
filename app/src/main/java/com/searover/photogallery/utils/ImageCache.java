package com.searover.photogallery.utils;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.util.LruCache;

/**
 * Created by searover on 3/14/15.
 * This class handles disk and memory caching of bitmaps in conjunction with the
 * {@link com.searover.photogallery.utils.ImageWorker} class and its subclasses.
 * Use {@link com.searover.photogallery.utils.ImageCache#getInstance(android.support.v4.app.FragmentManager, ImageCacheParams)}
 * to get an instance of this class, although usually a cache should be added directly to an
 * {@link com.searover.photogallery.utils.ImageWorker} by calling
 * @{@link com.searover.photogallery.utils.ImageWorker#addImageCache(android.support.v4.app.FragmentManager, ImageCacheParams)}.
 */
public class ImageCache {
    private static final String TAG = "ImageCache";

    // Default memory cache size in kilobytes
    private static final int DEFAULT_MEM_CACHE_SIZE = 1024 * 5; // 5MB

    // Default disk cache size in bytes
    private static final int DEFAULT_DISK_CACHE_SIZE = 1024 * 1024 * 10; // 10MB

    // Compression settings when writing images to disk cache
    private static final Bitmap.CompressFormat DEFAULT_COMPRESS_FORMAT = Bitmap.CompressFormat.JPEG;
    private static final int DEFAULT_COMPRESS_QUALITY = 70;
    private static final int DISK_CACHE_INDEX = 0;

    // Contants to easily toggle various caches
    private static final boolean DEFAULT_MEM_CACHE_ENABLED = true;
    private static final boolean DEFAULT_DISK_CACHE_ENABLED = true;
    private static final boolean DEFAULT_INIT_DISK_CACHE_ON_CREATE = false;

    private DiskLruCache mDiskLruCache;
    private LruCache<String, BitmapDrawable> mMemoryCache;
    private ImageCacheParams mCacheParams;


    /**
     * A holder class that contains cache parameters.
     */
    public static class ImageCacheParams{
        public int memCacheSize = DEFAULT_MEM_CACHE_SIZE;
    }

}
