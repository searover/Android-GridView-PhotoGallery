package com.searover.photogallery.utils;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.StrictMode;

import com.searover.photogallery.ui.MainActivity;

/**
 * Created by searover on 3/15/15.
 * Class containing some static utility methods.
 */
public class Utils {
    private Utils(){};

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void enableStrictMode(){
        if(Utils.hasGingerbread()){
            StrictMode.ThreadPolicy.Builder threadPolicyBuilder =
                    new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog();
            StrictMode.VmPolicy.Builder vmPolicyBuider =
                    new StrictMode.VmPolicy.Builder().detectAll().penaltyLog();
            if(Utils.hasHoneycomb()){
                threadPolicyBuilder.penaltyFlashScreen();
                vmPolicyBuider
                        .setClassInstanceLimit(MainActivity.class, 1);
            }
            StrictMode.setThreadPolicy(threadPolicyBuilder.build());
            StrictMode.setVmPolicy(vmPolicyBuider.build());
        }
    }

    public static boolean hasFroyo(){
        // Can use static final constants like FROYO, declared in later versions
        // of the OS since they are inlined at compile time. This is guaranteed behavior.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;
    }

    public static boolean hasGingerbread(){
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
    }

    public static boolean hasHoneycomb(){
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

    public static boolean hasHoneycombMR1(){
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1;
    }

    public static boolean hasJellyBean(){
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }

    public static boolean hasKitkat(){
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }
}
