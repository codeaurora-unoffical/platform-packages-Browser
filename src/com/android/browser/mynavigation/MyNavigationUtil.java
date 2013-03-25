package com.android.browser.mynavigation;

import android.net.Uri;
import android.util.Log;
import android.content.res.Resources;
import android.database.Cursor;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContentResolver;

public class MyNavigationUtil {

    public static final String ID = "_id";

    public static final String URL = "url";

    public static final String TITLE = "title";

    public static final String DATE_CREATED = "created";

    public static final String WEBSITE = "website";

    public static final String FAVICON = "favicon";

    public static final String THUMBNAIL = "thumbnail";

    public static final int WEBSITE_NUMBER = 12;

    public static final String AUTHORITY = "com.android.browser.mynavigation";

    public static final String MY_NAVIGATION = "content://" + AUTHORITY + "/" + "websites";

    public static final Uri MY_NAVIGATION_URI = Uri.parse("content://com.android.browser.mynavigation/websites");

    public static final String DEFAULT_THUMB = "default_thumb";

    public static final String LOGTAG = "MyNavigationUtil";

    public static boolean isDefaultMyNavigation(String url) {
        if (url != null && url.startsWith("ae://") && url.endsWith("add-fav")) {
            Log.d(LOGTAG, "isDefaultMyNavigation will return true.");
            return true;
        }
        return false;
    }

    public static String getMyNavigationUrl(String srcUrl) {
        String srcPrefix = "data:image/png";
        String srcSuffix = ";base64,";
        if (srcUrl != null && srcUrl.startsWith(srcPrefix)) {
            int indexPrefix = srcPrefix.length();
            int indexSuffix = srcUrl.indexOf(srcSuffix);
            return srcUrl.substring(indexPrefix, indexSuffix);
        }
        return "";
    }

    public static boolean isMyNavigationUrl(Context context, String itemUrl) {
        ContentResolver cr = context.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = cr.query(MyNavigationUtil.MY_NAVIGATION_URI,
                              new String[] {MyNavigationUtil.TITLE}, "url = ?", new String[] {itemUrl}, null);
            if (null != cursor && cursor.moveToFirst()) {
                Log.d(LOGTAG, "isMyNavigationUrl will return true.");
                return true;
            }
        } catch (IllegalStateException e) {
            Log.e(LOGTAG, "isMyNavigationUrl", e);
        } finally {
            if (null != cursor) {
                cursor.close();
            }
        }

        return false;
    }

    private static final String ACCEPTABLE_WEBSITE_SCHEMES[] = {
        "http:",
        "https:",
        "about:",
        "data:",
        "javascript:",
        "file:",
        "content:"
    };

    public static boolean urlHasAcceptableScheme(String url) {
        if (url == null) {
            return false;
        }

        for (int i = 0; i < ACCEPTABLE_WEBSITE_SCHEMES.length; i++) {
            if (url.startsWith(ACCEPTABLE_WEBSITE_SCHEMES[i])) {
                return true;
            }
        }
        return false;
    }

}
