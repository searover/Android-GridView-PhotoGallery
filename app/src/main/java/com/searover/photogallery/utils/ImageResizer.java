package com.searover.photogallery.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.util.Log;

import com.searover.photogallery.BuildConfig;

import java.io.FileDescriptor;

/**
 * Created by searover on 3/16/15.
 * A simple sublcass of {@link ImageWorker} that resize images from resources given a target width
 * and height. Useful for when the input images might be too large to simply load directly into
 * memory.
 */
public class ImageResizer extends ImageWorker {

    private static final String TAG = "ImageResizer";
    protected int mImageWidth;
    protected int mImageHeight;

    /**
     * Initialize providing a single target image size (used for both width and height)
     * @param context
     */
    protected ImageResizer(Context context, int imageWidth, int imageHeight) {
        super(context);
        setImageSize(imageWidth,imageHeight);
    }

    /**
     * Initialize providing a single target image size (used for both width and height)
     * @param context
     * @param imageSize
     */
    public ImageResizer(Context context, int imageSize){
        super(context);
        setImageSize(imageSize);
    }

    /**
     * Set the target image width and height.
     * @param width
     * @param height
     */
    public void setImageSize(int width, int height){
        mImageWidth = width;
        mImageHeight = height;
    }

    /**
     * Set the target image size (width and height will be the same).
     * @param size
     */
    public void setImageSize(int size){
        setImageSize(size,size);
    }

    private Bitmap processBitmap(int resId){
        if(BuildConfig.DEBUG){
            Log.d(TAG,"processBitmap - " + resId);
        }
        return decodeSampleBitmapFromResource(mResources,resId,mImageWidth,mImageHeight,getImageCache());
    }

    @Override
    protected Bitmap processBitmap(Object data) {
        return processBitmap(Integer.parseInt(String.valueOf(data)));
    }

    /**
     * Decode and sample down a bitmap from resources to the requested width and height
     * @param res
     * @param resId
     * @param reqWidth
     * @param reqHeight
     * @param cache
     * @return
     */
    public static Bitmap decodeSampleBitmapFromResource(Resources res, int resId,
                                        int reqWidth, int reqHeight, ImageCache cache){
        // First deoode with inJustDecodeBounds = true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res,resId,options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options,reqWidth,reqHeight);

        // If we're running on Honeycomb or newer, try to use inBitmap
        if(Utils.hasHoneycomb()){
            addInBitmapOptions(options,cache);
        }
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res,resId,options);
    }

    /**
     * Decode and sample down a bitmap from a file to the requested width and height
     * @param filename
     * @param reqWidth
     * @param reqHeight
     * @param cache
     * @return
     */
    public static Bitmap decodeSampleBitmapFromFile(String filename, int reqWidth, int reqHeight,
                                                    ImageCache cache){
        // First decode with inJustDecodeBounds = true too check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filename);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options,reqWidth,reqHeight);

        // If we're running on Honeycomb or newer, try to use inBitmap
        if(Utils.hasHoneycomb()){
            addInBitmapOptions(options,cache);
        }

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filename,options);
    }

    /**
     * Decode and sample down a bitmap from a file input stream to the requested width and height
     * @param fileDescriptor
     * @param reqWidth
     * @param reqHeight
     * @param cache
     * @return
     */
    public static Bitmap decodeSampleBitmapFromDescriptor(
            FileDescriptor fileDescriptor, int reqWidth, int reqHeight, ImageCache cache){
        // First decode with inJustDecodeBounds = true too check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fileDescriptor,null,options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options,reqWidth,reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        if(Utils.hasHoneycomb()){
            addInBitmapOptions(options,cache);
        }

        return BitmapFactory.decodeFileDescriptor(fileDescriptor,null,options);
    }

    /**
     *
     * @param options
     * @param cache
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static void addInBitmapOptions(BitmapFactory.Options options, ImageCache cache){
        // inBitmap only works with mutable bitmaps so force the decoder to return mutable bitmaps.
        options.inMutable = true;

        if(cache != null){
            // Try and find a bitmap to use for inBitmap
            Bitmap inBitmap = cache.getBitmapFromResuableSet(options);
            if(inBitmap != null){
                options.inBitmap = inBitmap;
            }
        }
    }

    /**
     * Calculate an inSampleSize for use in a {@link android.graphics.BitmapFactory.Options} object
     * when decoding bitmaps using the decode* methods from {@link android.graphics.BitmapFactory}.
     * This implementation calculates the closest inSampleSize that is a power of 2 and will result
     * in the final decoded bitmap having a width and height equal to or larger than the requested
     * width and height.
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    public static int calculateInSampleSize(BitmapFactory.Options options,
                                            int reqWidth, int reqHeight){
        // Raw width and height of image
        final int height = options.outWidth;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if(height > reqHeight || width > reqWidth){
            final int halfWidth = width / 2;
            final int halfHeight = height / 2;

            // Calculate the largest inSampleSize value that is power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfWidth / inSampleSize) > reqWidth && (halfHeight / inSampleSize) > reqHeight){
                inSampleSize *= 2;
            }
        }

        // This offers same additional logic in case the image has a strange aspect ratio.
        // For example, a panorama may have a much larger width than height. In these cases
        // the total pixels might still end up being to large to fit confortably in memory,
        // so we should be more aggressive with sample down the image ( = larger inSampleSize)

        long totalPixels = width * height / inSampleSize;

        // Anything more than 2x the requested pixels we'll sample down further
        final long totalReqPixelsCap = reqWidth * reqHeight * 2;
        while (totalPixels > totalReqPixelsCap){
            inSampleSize *= 2;
            totalPixels /= 2;
        }
        return inSampleSize;
    }
}
