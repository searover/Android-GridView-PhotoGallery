package com.searover.photogallery.ui;

import android.content.Context;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.searover.photogallery.BuildConfig;
import com.searover.photogallery.R;
import com.searover.photogallery.provider.Photos;
import com.searover.photogallery.utils.Utils;

import java.lang.reflect.TypeVariable;

/**
 * Simple FragmentActivity to hold the main {@link com.searover.photogallery.ui.PhotoGalleryFragment}
 * and not much else.
 */
public class MainActivity extends FragmentActivity {

    private static final String TAG = "MainActivity";
    private static final String IMAGE_CACHE_DIR = "thumbs";

    private int mImageThumbSize;
    private int mImageThumbSpacing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(BuildConfig.DEBUG){
            Utils.enableStrictMode();
        }
        super.onCreate(savedInstanceState);

        if(getSupportFragmentManager().findFragmentByTag(TAG) == null){
            final FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.add(android.R.id.content, new PhotoGalleryFragment(), TAG);
            transaction.commit();
        }
    }

    private class ImageAdapter extends BaseAdapter{

        private final Context mContext;
        private int mItemHeight = 0;
        private int mNumColumns = 0;
        private int mActionBarHeight = 0;
        private GridView.LayoutParams mImageViewLayoutParams;

        public ImageAdapter (Context context){
            super();
            mContext = context;
            mImageViewLayoutParams = new GridView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            TypedValue tv = new TypedValue();
            if (context.getTheme().resolveAttribute(android.R.attr.actionBarSize,tv,true)){
                mActionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data,
                        context.getResources().getDisplayMetrics());
            }
        }

        public int getCount() {
            if(getmNumColumns() == 0){
                return 0;
            }
            return Photos.imageThumbUrls.length + mNumColumns;
        }

        @Override
        public Object getItem(int position) {
            return position < mNumColumns ?
                    null : Photos.imageThumbUrls[position - mNumColumns];
        }

        @Override
        public long getItemId(int position) {
            return position < mNumColumns ? 0 : position -mNumColumns;
        }

        @Override
        public int getViewTypeCount(){
            return 2;
        }

        @Override
        public int getItemViewType(int position){
            return (position < mNumColumns) ? 1: 0;
        }

        @Override
        public boolean hasStableIds(){
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if(position < mNumColumns){
                if(convertView == null){
                    convertView = new View(mContext);
                }
                convertView.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,mActionBarHeight
                ));
                return  convertView;
            }

            // Now handle the main ImageView thumbnails
            ImageView imageView;
            if(convertView == null){
                imageView = new RecyclingImageView(mContext);
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setLayoutParams(mImageViewLayoutParams);
            }else { // Otherwise re-use the converted view
                imageView = (ImageView) convertView;
            }

            // Check the height matches our calculated column width
            if(imageView.getLayoutParams().height != mItemHeight){
                imageView.setLayoutParams(mImageViewLayoutParams);
            }

            // Finally load the image asynchronously into the ImageView, this also takes care of
            // setting a placeholder image while the background thread runs
            return null;
        }

        public int getmNumColumns(){
            return mNumColumns;
        }
    }
}
