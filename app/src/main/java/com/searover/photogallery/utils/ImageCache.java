package com.searover.photogallery.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.util.LruCache;
import android.util.Log;

import com.searover.photogallery.BuildConfig;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

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
    private final Object mDiskCacheLock = new Object();
    private boolean mDiskCacheStarting = true;

    private Set<SoftReference<Bitmap>> mReusableBitmaps;

    private ImageCache(ImageCacheParams cacheParams){
        init(cacheParams);
    }

    public static ImageCache getInstance(
            FragmentManager fragmentManager, ImageCacheParams cacheParams
    ){
        // Search for, or create an instance of the non-UI RetainFragment
        final RetainFragment mRetainFragment = findOrCreateRetainFragment(fragmentManager);

        // See if we already have an ImageCache stored in RetainFragment
        ImageCache imageCache = (ImageCache) mRetainFragment.getmObject();

        // No existing ImageCache, create one and store it in RetainFragment
        if(imageCache == null){
            imageCache = new ImageCache(cacheParams);
            mRetainFragment.setObject(imageCache);
        }
        return imageCache;
    }

    /**
     * Initialize the cache, providing all parameters.
     * @param cacheParams
     */
    private void init(ImageCacheParams cacheParams){
        mCacheParams = cacheParams;

        // Set up memory cache
        if(mCacheParams.memoryCacheEnabled){
            if(BuildConfig.DEBUG){
                Log.d(TAG,"Memory cache created (size = " + mCacheParams.memCacheSize + ")");
            }

            // If we're running on Honeycomb or newer, create a set of reusable bitmaps that can
            // be populated into the inBitmap field of BitmapFactory.Options. Ntte that the set
            // is of SoftReferences which will actually not be very effective due to the gerbage
            // collector being aggressive clearing Soft/WeakReferences. A better approach would
            // be to use a strongly references bitmaps, however this would require some balancing
            // of memory usage between this set and the bitmap LruCache. It would also require
            // knowledge of the expected size of the bitmaps. From Honeycomb to JellyBean the
            // size would need to be precise, from Kitkat onward the size would just need to be
            // the upper bound (due to changes in how inBitmap can re-use bitmap).
            if(Utils.hasHoneycomb()){
                mReusableBitmaps = Collections.synchronizedSet(new HashSet<SoftReference<Bitmap>>());
            }

            mMemoryCache = new LruCache<String, BitmapDrawable>(mCacheParams.memCacheSize){

                /**
                 * Notify the removed entry that is no longer being cached
                 * @param evicted
                 * @param key
                 * @param oldValue
                 * @param newValue
                 */
                protected void entryRemoved(boolean evicted, String key, BitmapDrawable oldValue,
                                            BitmapDrawable newValue){
                    if(RecylingBitmapDrawable.class.isInstance(oldValue)){
                        // The removed entry is a recycling drawable, so notify it that it has
                        // been removed from the memory cache.
                        ((RecylingBitmapDrawable)oldValue).setIsCached(false);
                    }else {
                        // The removed entry is a standard BitmapDrawable
                        if(Utils.hasHoneycomb()){
                            // We're running an Honeycomb or later, so add the bitmap
                            // to a SoftReference set for possible use with inBitmap later
                            mReusableBitmaps.add(new SoftReference<Bitmap>(oldValue.getBitmap()));
                        }
                    }
                }

                /**
                 * Measure item size in kilobytes rather than units which is more
                 * partical for a bitmap cache
                 * @param key
                 * @param value
                 * @return
                 */
                protected int sizeOf(String key, BitmapDrawable value){
                   final int bitmapSize = getBitmapSize(value) / 1024;
                    return bitmapSize == 0 ? 1 : bitmapSize;
                }
            };
        }

        // By default the disk cache is not initialized here as it should be initialized
        // on a separate thread due to disk access.
        if(cacheParams.initDiskCacheOnCreate){
            // Set up disk cache
            initDiskCache();
        }
    }

    /**
     * Initialized the disk cache. Note that this includes disk access so this should not be
     * executed on the Main/UI thread. By default an ImageCache does not initialize the disk
     * cache when it is created, instead you should call initDiskCache() to initialize it on
     * a background thread.
     */
    public void initDiskCache(){
        // Set up disk cache
        synchronized (mDiskCacheLock){
            if(mDiskLruCache == null || mDiskLruCache.isClosed()){
                File diskCacheDir = mCacheParams.diskCacheDir;
                if(mCacheParams.diskCacheEnabled && diskCacheDir != null){
                    if(!diskCacheDir.exists()){
                        diskCacheDir.mkdirs();
                    }
                    if(getUsableSpace(diskCacheDir) > mCacheParams.diskCacheSize){
                        try {
                            mDiskLruCache = DiskLruCache.open(
                                    diskCacheDir,1,1,mCacheParams.diskCacheSize
                            );
                            if(BuildConfig.DEBUG){
                                Log.d(TAG,"Disk cache initialized");
                            }
                        } catch (IOException e) {
                            mCacheParams.diskCacheDir = null;
                            Log.e(TAG,"initDiskCache - " + e);
                        }
                    }
                }
            }
        }
        mDiskCacheStarting = false;
        mDiskCacheLock.notifyAll();
    }

    /**
     * Adds a bitmap to both memory and disk cache
     * @param data
     * @param value
     */
    public void addBitmapToCache(String data, BitmapDrawable value){
        if( data == null || value == null){
            return;
        }
        // Add to memory cache
        if(mMemoryCache != null){
            if(RecylingBitmapDrawable.class.isInstance(value)){
                // The added entry is a recycling drawable, so notify it
                // that it has been added into the memory cache
                ((RecylingBitmapDrawable)value).setIsCached(true);
            }
            mMemoryCache.put(data,value);
        }

        synchronized (mDiskCacheLock){
            // Add to disk cache
            if(mDiskLruCache != null){
                final String key = hasKeyForDisk(data);
                OutputStream out = null;
                try {
                    DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
                    if(snapshot == null){
                        final DiskLruCache.Editor editor = mDiskLruCache.edit(key);
                        if(editor != null){
                            out = editor.newOutputStream(DISK_CACHE_INDEX);
                            value.getBitmap().compress(mCacheParams.compressFormat,
                                    mCacheParams.compressQuality,out);
                            editor.commit();
                            out.close();
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG,"addBitmapToCache - " + e);
                } catch (Exception e){
                    Log.e(TAG,"addBitmapToCache - " + e);
                }finally {
                    try {
                        if(out != null){
                            out.close();
                        }
                    } catch (IOException e) {

                    }
                }
            }
        }
    }

    /**
     * Get from memory cache
     * @param data Unique identifier for which item to get
     * @return The bitmap drawable if found in cache, null otherwise
     */
    public BitmapDrawable getBitmapFromMemCache(String data){
        BitmapDrawable memValue = null;
        if(mMemoryCache != null){
            memValue = mMemoryCache.get(data);
        }

        if(BuildConfig.DEBUG && memValue != null){
            Log.d(TAG, "Memory cache hit");
        }
        return memValue;
    }

    /**
     * Get from disk cache
     * @param data
     * @return
     */
    public Bitmap getBitmapFromDiskCache(String data){
        final String key = hasKeyForDisk(data);
        Bitmap bitmap = null;

        synchronized (mDiskCacheLock){
            while (mDiskCacheStarting){
                try {
                    mDiskCacheLock.wait();
                } catch (InterruptedException e) {

                }
            }
            if(mDiskLruCache != null){
                InputStream inputStream = null;
                try {
                    final DiskLruCache.Snapshot snapshot = mDiskLruCache.get(data);
                    if(snapshot != null){
                        if(BuildConfig.DEBUG){
                            Log.d(TAG,"Disk cache hit");
                        }
                        inputStream = snapshot.getInputStream(DISK_CACHE_INDEX);
                        if(inputStream != null){
                            FileDescriptor fd = ((FileInputStream)inputStream).getFD();
                            // Decode bitmap, but we don't want to sample so give
                            // MAX_VALUE as the target dimensions
                            bitmap = ImageResizer.decodeSampleBitmapFormDescriptor(
                                        fd,Integer.MAX_VALUE,Integer.MAX_VALUE, this);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG,"getBitmapFromDiskCache - " + e);
                } finally {
                  try {
                      if(inputStream != null){
                          inputStream.close();
                      }
                  } catch (IOException e) {

                  }
                }
            }
        }
        return bitmap;
    }

    /**
     *
     * @param options - BitmapFactory.Options with out* options populated
     * @return Bitmap that case be used for inBitmap
     */
    protected Bitmap getBitmapFromResuableSet(BitmapFactory.Options options){
        Bitmap bitmap = null;
        if(mReusableBitmaps != null && !mReusableBitmaps.isEmpty()){
            synchronized (mReusableBitmaps){
                final Iterator<SoftReference<Bitmap>> iterator = mReusableBitmaps.iterator();
                Bitmap item;
                while (iterator.hasNext()){
                    item = iterator.next().get();
                    if(null != item && item.isMutable()){
                        // Check to see it the item can be used for inBitmap
                        if(canUseForInBitmap(item,options)){
                            bitmap = item;
                            // Remove from reusable set so it can't be used again
                            iterator.remove();
                            break;
                        }
                    }else {
                        // Remove from the set if the reference has been cleared.
                        iterator.remove();
                    }
                }
            }
        }
        return bitmap;
    }

    /**
     * Clears both the memory and disk cache associated with this ImageCache object. Note that
     * this includes disk access so this should not be executed on the Main/UI thread.
     */
    public void clearCache(){
        if(mMemoryCache != null){
            mMemoryCache.evictAll();
            if(BuildConfig.DEBUG){
                Log.d(TAG,"Memory cache cleared");
            }
        }
        synchronized (mDiskCacheLock){
            mDiskCacheStarting = true;
            if(mDiskLruCache != null && !mDiskLruCache.isClosed()){
                try {
                    mDiskLruCache.delete();
                    if(BuildConfig.DEBUG){
                        Log.d(TAG,"Disk cache cleared");
                    }
                } catch (IOException e) {
                    Log.e(TAG,"clearCache - " + e);
                }
                mDiskLruCache = null;
                initDiskCache();
            }
        }
    }

    /**
     * Flushes the disk cache associated with this ImageCache object. Note that this includes
     * disk access so this should not be executed on the Main/UI thread.
     */
    public void flush(){
        synchronized (mDiskCacheLock){
            if(mDiskLruCache != null){
                try {
                    mDiskLruCache.flush();
                    if(BuildConfig.DEBUG){
                        Log.d(TAG,"Disk cache flushed");
                    }
                } catch (IOException e) {
                    Log.e(TAG,"flush - " + e);
                }
            }
        }
    }

    /**
     * Closes the disk cache associated with this ImageCache object. NOte that this includes
     * disk access so this should not be executed on the Main/UI thread.
     */
    public void close(){
        synchronized (mDiskCacheLock){
            if(mDiskLruCache != null){
                try {
                    if(!mDiskLruCache.isClosed()){
                        mDiskLruCache.close();
                        mDiskLruCache = null;
                        if(BuildConfig.DEBUG){
                            Log.d(TAG,"Disk cache closed");
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG,"close - " + e);
                }
            }
        }
    }

    /**
     *
     * @param candidate - Bitmap to check
     * @param targetOptions Options that have the out* value populated
     * @return ture if <code>candidate</code> can be used for inBitmap re-use with <code>targetOptions</code>
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private static boolean canUseForInBitmap(Bitmap candidate, BitmapFactory.Options targetOptions){
        if(!Utils.hasKitkat()){
            // On earlier versions, the dimensions must match exactly and the inSampleSize must be 1
            return candidate.getWidth() == targetOptions.outWidth
                    && candidate.getHeight() == targetOptions.outHeight
                    && targetOptions.inSampleSize == 1;
        }

        // From Android 4.4 (KitKat) onward we can re-use if the byte size of the new bitmap
        // is smaller then the resuable bitmap candidate allocation byte count.
        int width = targetOptions.outWidth / targetOptions.inSampleSize;
        int height = targetOptions.outHeight / targetOptions.inSampleSize;
        int byteCount = width * height * getBytesPerPixel(candidate.getConfig());
        return byteCount <= candidate.getAllocationByteCount();
    }

    /**
     * Return the bytes per pixel of a bitmap based on its configuration.
     * @param config The bitmap configuration
     * @return The byte usage per pixel
     */
    private static int getBytesPerPixel(Bitmap.Config config){
        if(config == Bitmap.Config.ARGB_8888){
            return 4;
        }else if(config == Bitmap.Config.RGB_565){
            return 2;
        }else if(config == Bitmap.Config.ARGB_4444){
            return 2;
        }else if(config == Bitmap.Config.ALPHA_8){
            return 1;
        }
        return 1;
    }

    /**
     * Get the size in bytes of a bitmap in a BitmapDrawable. Note that from Android 4.4(KitKat)
     * onward this returns the allocated memory size of the bitmap which can be larger than the
     * actual bitmap data byte count ( in the case it was re-used).
     * @param value
     * @return
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static int getBitmapSize(BitmapDrawable value){
        Bitmap bitmap = value.getBitmap();

        // From Kitkat onward use getAllocationByteCount() as allocated bytes can potentially be
        // larger than bitmap byte count.
        if(Utils.hasKitkat()){
            return bitmap.getAllocationByteCount();
        }
        if(Utils.hasHoneycombMR1()){
            return bitmap.getByteCount();
        }
        // Pre HC-MR1
        return bitmap.getRowBytes() * bitmap.getHeight();
    }

    /**
     * Check if external storage is removable (like an SD card), false otherwise.
     * @return
     */
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public static boolean isExternalStorageReovable(){
        if(Utils.hasGingerbread()){
            return Environment.isExternalStorageRemovable();
        }
        return true;
    }

    public static File getExternalCacheDir(Context context){
        if(Utils.hasFroyo()){
            return context.getExternalCacheDir();
        }

        // Before Froyo we need to construct the external cache dir ourselves.
        final String cacheDir = "/Android/data/" + context.getPackageName() + "/cache/";
        return new File(Environment.getExternalStorageDirectory().getPath() + cacheDir);
    }

    /**
     * Get a usable cache directory ( external if available, internal otherwise).
     * @param context
     * @param uniqueName
     * @return
     */
    public static File getDiskCacheDir(Context context, String uniqueName){
        // Check if media is mounted or storage is built-in, if so, try and use external
        // cache dir, otherwise use internal cache dir.
        final String cachePath =
                Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                !isExternalStorageReovable() ? getExternalCacheDir(context).getPath() :
                context.getCacheDir().getPath();
        return new File(cachePath + File.separator + uniqueName);
    }

    /**
     * A hashing method that changes a string (like a URL) into a hash suitable for using as a
     * disk file
     * @param key
     * @return
     */
    public static String hasKeyForDisk(String key){
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    private static String bytesToHexString(byte[] bytes){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i ++){
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if(hex.length() == 1){
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * Check how much usable space is available at a given path.
     * @param path The path to check
     * @return The space available in bytes
     */
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public static long getUsableSpace(File path){
        if(Utils.hasGingerbread()){
            return path.getUsableSpace();
        }
        final StatFs statFs = new StatFs(path.getPath());
        return statFs.getBlockSizeLong() * statFs.getAvailableBlocksLong();
    }

    private static RetainFragment findOrCreateRetainFragment(FragmentManager fm){
        // Check to see if we have retained the worker fragment
        RetainFragment mRetainFragment = (RetainFragment) fm.findFragmentByTag(TAG);

        // If not retained (or first time running), we need to create and add it.
        if(mRetainFragment == null){
            mRetainFragment = new RetainFragment();
            fm.beginTransaction().add(mRetainFragment,TAG).commitAllowingStateLoss();
        }
        return mRetainFragment;
    }


    /**
     * A holder class that contains cache parameters.
     */
    public static class ImageCacheParams{
        public int memCacheSize = DEFAULT_MEM_CACHE_SIZE;
        public int diskCacheSize = DEFAULT_DISK_CACHE_SIZE;
        public File diskCacheDir;
        public Bitmap.CompressFormat compressFormat = DEFAULT_COMPRESS_FORMAT;
        public int compressQuality = DEFAULT_COMPRESS_QUALITY;
        public boolean memoryCacheEnabled = DEFAULT_MEM_CACHE_ENABLED;
        public boolean diskCacheEnabled = DEFAULT_DISK_CACHE_ENABLED;
        public boolean initDiskCacheOnCreate = DEFAULT_INIT_DISK_CACHE_ON_CREATE;

        /**
         * Create a set of image cache parameters that can be provided to
         * {@link com.searover.photogallery.utils.ImageCache#getInstance(android.support.v4.app.FragmentManager,com.searover.photogallery.utils.ImageCache.ImageCacheParams)}
         * or {@link com.searover.photogallery.utils.ImageWorker#addImageCache(android.support.v4.app.FragmentManager,com.searover.photogallery.utils.ImageCache.ImageCacheParams}
         * @param context
         * @param diskCacheDirectoryName
         */
        public ImageCacheParams(Context context, String diskCacheDirectoryName){
            diskCacheDir = getDiskCacheDir(context,diskCacheDirectoryName);
        }

        public void setMemCacheSizePercent(float percent){
            if(percent < 0.01f || percent > 0.8f){
                throw new IllegalArgumentException("setMemCacheSizePercent - percent must be"
                + "between 0.01 and 0.8(inclusive)");
            }
        }
    }

    /**
     * A simple non-UI Fragment that stores a single Object and is retained over configuration
     * changes. It will be used to retain the ImageCache object.
     */
    public static class RetainFragment extends Fragment{
        private Object mObject;

        public RetainFragment(){}

        @Override
        public void onCreate(Bundle savedInstanceState){
            super.onCreate(savedInstanceState);

            // Make sure this Fragment is retained over a configuration change.
            setRetainInstance(true);
        }

        /**
         * Store a single object in this Fragment
         * @param object
         */
        public void setObject(Object object){
            mObject = object;
        }

        /**
         * Get the stored object
         * @return
         */
        public Object getmObject(){
            return mObject;
        }
    }

}
