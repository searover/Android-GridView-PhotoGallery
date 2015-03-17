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
}
