package com.searover.photogallery.utils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.Image;
import com.searover.photogallery.utils.AsyncTask;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.widget.ImageView;

import com.searover.photogallery.BuildConfig;

import java.lang.ref.WeakReference;

/**
 * Created by searover on 3/14/15.
 * This class wraps up completing some arbitrary loong running work when loading a bitmap to an
 * ImageView. It handles things like using a memory and disk cache, running the work in a
 * background thread and setting a placeholder image.
 */
public abstract class ImageWorker {
    private static final String TAG = "ImageWorker";
    private static final int FADE_IN_TIME = 200;

    private ImageCache mImageCache;
    private ImageCache.ImageCacheParams mImageCacheParams;
    private Bitmap mLoadingBitmap;
    private boolean mFadeInBitmap = true;
    private boolean mExitTaskEarly = false;
    protected boolean mPauseWork = false;
    private final Object mPauseworkLock = new Object();

    protected Resources mResources;

    private static final int MESSAGE_CLEAR = 0;
    private static final int MESSAGE_INIT_DISK_CACHE = 1;
    private static final int MESSAGE_FLUSH = 2;
    private static final int MESSAGE_CLOSE = 3;

    protected ImageWorker(Context context){
        mResources = context.getResources();
    }

    /**
     * Load an image specified by the data parameter into an ImageView (override
     * {@link ImageWorker#processBitmap(Object)} to define the processing logic)
     * A memory and disk cache will be used if an {@link ImageCache} has been added
     * using {@link ImageWorker#addImageCache(android.support.v4.app.FragmentManager, ImageCache.ImageCacheParams)}.
     * If the image is found in the memory cache, it is set immediately, otherwise an {@link AsyncTask}
     * will be created to asynchronously load the bitmap.
     * @param data The URL of the image to download
     * @param imageView The Image to bind the download image to.
     */
    public void loadImage(Object data, ImageView imageView){
        if(data == null){
            return;
        }
        BitmapDrawable value = null;
        if(mImageCache != null){
            value = mImageCache.getBitmapFromMemCache(String.valueOf(data));
        }
        if(value != null){
            // Bitmap found in memory cache
            imageView.setImageDrawable(value);
        }else if(cancelPotentialWork(data,imageView)){
            final BitmapWorkerTask task = new BitmapWorkerTask(data,imageView);
            final AsyncDrawable asyncDrawable = new AsyncDrawable(mResources,mLoadingBitmap,task);
            imageView.setImageDrawable(asyncDrawable);
            // NOTE: This uses a custom version of AsyncTask that has been pulled from the
            // framework and slightly modified. Refer to the docs at the top of the class
            // for more info on what was changed.
            task.executeOnExecutor(AsyncTask.DUAL_THREAD_EXECUTOR);
        }
    }

    /**
     * Set placeholder bitmap that shows when the background thread is running.
     * @param bitmap
     */
    public void setmLoadingBitmap(Bitmap bitmap){
        this.mLoadingBitmap = bitmap;
    }

    /**
     * Set placeholder bitmap that shows when the background thread is running.
     * @param resId
     */
    public void setLoadingImage(int resId){
        this.mLoadingBitmap = BitmapFactory.decodeResource(mResources,resId);
    }

    /**
     * Adds an {@link ImageCache} on this {@link ImageWorker} to handle disk and memory bitmap caching
     * @param fragmentManager
     * @param cacheParams
     */
    public void addImageCache(FragmentManager fragmentManager, ImageCache.ImageCacheParams cacheParams){
        mImageCacheParams = cacheParams;
        mImageCache = ImageCache.getInstance(fragmentManager,mImageCacheParams);
        new CacheAsyncTask().execute(MESSAGE_INIT_DISK_CACHE);
    }

    /**
     * If set to true, the image will fade-in once it has been loaded by the background thread.
     * @param fadeIn
     */
    public void setImageFadeIn(boolean fadeIn){
        mFadeInBitmap = fadeIn;
    }

    public void setExitTaskEarly(boolean exitTaskEarly){
        mExitTaskEarly = exitTaskEarly;
        setPauseWork(false);
    }

    /**
     * Subclasses should override this to define any processing or work that must happen to produce
     * the final bitmap. This will be executed in a background thread and be long running. For
     * example, you could resize a large bitmap here, or pull down an amage from network.
     * @param data
     * @return
     */
    protected abstract Bitmap processBitmap(Object data);

    /**
     * @return The {@link ImageCache} object currently being used by the {@link ImageWorker}
     */
    protected ImageCache getImageCache(){
        return mImageCache;
    }

    /**
     * Cancels any pending work attached to the provided ImageView.
     * @param imageView
     */
    public static void cancelWork(ImageView imageView){
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
        if(bitmapWorkerTask != null){
            bitmapWorkerTask.cancel(true);
            if(BuildConfig.DEBUG){
                final Object bitmapData = bitmapWorkerTask.mData;
                Log.d(TAG,"cancelWork - cancelled work for " + bitmapData);
            }
        }
    }

    /**
     * Returns ture if the current work has been canceled or if there was no work in progress
     * on this imageview.
     * Returns false if the work in progress deals with the same data. The work is not stopped
     * in that case.
     * @param data
     * @param imageView
     * @return
     */
    public static boolean cancelPotentialWork(Object data, ImageView imageView){
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
        if(bitmapWorkerTask != null){
            final Object bitmapData = bitmapWorkerTask.mData;
            if(bitmapData == null || !bitmapData.equals(data)){
                bitmapWorkerTask.cancel(true);
                if(BuildConfig.DEBUG){
                    Log.d(TAG,"cancelPotentialWork - cancelled work for " + data);
                }
            }else {
                // The same work is already in progress
                return false;
            }
        }
        return true;
    }

    /**
     * @param imageView Any ImageView
     * @return Retrieve the currently active work task (if any) associated with this imageview,
     * null if there is no such task.
     */
    private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView){
        if(imageView != null){
            final Drawable drawable = imageView.getDrawable();
            if(drawable instanceof AsyncDrawable){
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    /**
     * The actual AsyncTask that will asynchronously process the image
     */
    private class BitmapWorkerTask extends AsyncTask<Void, Void, BitmapDrawable>{

        private Object mData;
        private final WeakReference<ImageView> imageViewWeakReference;

        public BitmapWorkerTask(Object data, ImageView imageView){
            mData = data;
            imageViewWeakReference = new WeakReference<ImageView>(imageView);
        }

        @Override
        protected BitmapDrawable doInBackground(Void... params) {
            if(BuildConfig.DEBUG){
                Log.d(TAG,"doInBackground - starting work");
            }
            final String dataString = String.valueOf(mData);
            Bitmap bitmap = null;
            BitmapDrawable drawable = null;

            // wait here if work is paused and the task is not canceled
            synchronized (mPauseworkLock){
                while (mPauseWork && !isCancelled()){
                    try {
                        mPauseworkLock.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }

            // If the image cache is available and this task has not been cancelled by another
            // thread and the ImageView that was originally bound to this task is still bound back
            // to this task and our "exit early" flag is not set then try and fetch the bitmap from
            // the cache
            if(mImageCache != null && !isCancelled() && getAttachedImageView() != null &&
                    !mExitTaskEarly){
                bitmap = mImageCache.getBitmapFromDiskCache(dataString);
            }

            // If the bitmap was not found in the cache and this task has not been cancelled by
            // another thread and the ImageView that was originally bound to this task is still
            // bound back to this task and out "exit earlly" flag is not set, then call the main
            // process method ( as implemented by a subclass)
            if(bitmap == null && !isCancelled() && getAttachedImageView() != null &&
                    !mExitTaskEarly){
                bitmap = processBitmap(dataString);
            }

            // If the bitmap was processed and the image cache is available, then add the processed
            // bitmap to the cache for future use. Note we don't check if the task ws cancelled
            // here, if it was, and the thread is still running, we may as well add the processed
            // bitmap to our cache as it might be used again in the future.
            if(bitmap != null){
                if(Utils.hasHoneycomb()){
                    // Running on Honeycomb or newer, so wrap in a standard BitmapDrawable
                    drawable = new BitmapDrawable(mResources,bitmap);
                }else {
                    // Running on Gingerbread or older, so wrap in a RecyclingBitmapDrawable
                    // which will recycle automagically
                    drawable = new RecylingBitmapDrawable(mResources,bitmap);
                }
                if(mImageCache != null){
                    mImageCache.addBitmapToCache(dataString,drawable);
                }
            }

            if(BuildConfig.DEBUG){
                Log.d(TAG, "doInBackground - finished work");
            }

            return drawable;
        }

        /**
         * Once the image is processed, associates it to the imageview
         * @param value
         */
        @Override
        protected void onPostExecute(BitmapDrawable value){
            // If cancel was called on this task or the "exit early" flag is set when we're done
            if(isCancelled() || mExitTaskEarly){
                value = null;
            }
            final ImageView imageView = getAttachedImageView();
            if(value != null && imageView != null){
                if(BuildConfig.DEBUG){
                    Log.d(TAG, "onPostExecute - setting bitmap");
                }
                setImageDrawable(imageView, value);
            }
        }

        protected void onCancelled(BitmapDrawable value){
            super.onCancelled(value);
            synchronized (mPauseworkLock){
                mPauseworkLock.notifyAll();
            }
        }

        private ImageView getAttachedImageView(){
            final ImageView imageView = imageViewWeakReference.get();
            final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
            if(this == bitmapWorkerTask){
                return imageView;
            }
            return null;
        }
    }

    /**
     * A custom Drawable that will be attached to the imageView while the work is in progress,
     * Contains a reference to the actual worker task, so that it can be stopped if a new binding is
     * required, and makes sure that only the last started worker process can bind its result,
     * independently of the finish order.
     */
    private static class AsyncDrawable extends BitmapDrawable{
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskWeakReference;

        public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask){
            super(res,bitmap);
            bitmapWorkerTaskWeakReference =
                    new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask(){
            return bitmapWorkerTaskWeakReference.get();
        }
    }

    /**
     * Called when the processing is complete and the final drawable should be set
     * on the Imageview.
     * @param imageView
     * @param drawable
     */
    private void setImageDrawable(ImageView imageView, Drawable drawable){
        if(mFadeInBitmap){
            // Transition drawable with a tranparent drawable and the final drawable
            final TransitionDrawable td =
                    new TransitionDrawable(new Drawable[]{
                            new ColorDrawable(android.R.color.transparent),drawable
                    });
            // Set background to loading bitmap
            imageView.setBackground(new BitmapDrawable(mResources,mLoadingBitmap));
            imageView.setImageDrawable(td);
            td.startTransition(FADE_IN_TIME);
        }else {
            imageView.setImageDrawable(drawable);
        }
    }

    /**
     * Pause any ongoing background work. This can be used as a temporary measure
     * to improve performance. For example background work could
     * be paused when a ListView or GridView is being scrolled using a
     * {@link android.widget.AbsListView.OnScrollListener} to keeep
     * scrolling smooth.
     * If work is paused, be sure setPauseWork(false) is called again before your
     * fragment or activity is destroyed ( for example during
     * {@link android.app.Activity#onPause()}, or there is a risk the background
     * thread will never finish.
     * @param pauseWork
     */
    public void setPauseWork(boolean pauseWork){
        synchronized (mPauseworkLock){
            mPauseWork = pauseWork;
            if(!mPauseWork){
                mPauseworkLock.notifyAll();
            }
        }
    }

    protected class CacheAsyncTask extends AsyncTask<Object, Void, Void>{

        @Override
        protected Void doInBackground(Object... params) {
            switch ((Integer)params[0]){
                case MESSAGE_CLEAR:
                    clearCacheInternal();
                    break;
                case MESSAGE_INIT_DISK_CACHE:
                    initDiskCacheInternal();
                    break;
                case MESSAGE_FLUSH:
                    flushCacheInternal();
                    break;
                case MESSAGE_CLOSE:
                    closeCacheInternal();
                    break;
            }
            return null;
        }
    }

    protected void initDiskCacheInternal(){
        if(mImageCache != null){
            mImageCache.initDiskCache();
        }
    }

    protected void clearCacheInternal(){
        if(mImageCache != null){
            mImageCache.clearCache();
        }
    }

    protected void flushCacheInternal(){
        if(mImageCache != null){
            mImageCache.flush();
        }
    }

    protected void closeCacheInternal(){
        if(mImageCache != null){
            mImageCache.close();
            mImageCache = null;
        }
    }

    public void clearCache(){
        new CacheAsyncTask().execute(MESSAGE_CLEAR);
    }

    public void flushCache(){
        new CacheAsyncTask().execute(MESSAGE_FLUSH);
    }

    public void closeCache(){
        new CacheAsyncTask().execute(MESSAGE_CLOSE);
    }
}