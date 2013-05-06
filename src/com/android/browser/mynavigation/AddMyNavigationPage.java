package com.android.browser.mynavigation;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ParseException;
import android.net.Uri;
import android.net.WebAddress;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.android.browser.UrlUtils;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import com.android.browser.R;
import com.android.browser.BrowserUtils;

public class AddMyNavigationPage extends Activity {

    private static final String LOGTAG = "AddMyNavigationPage";

    private EditText    mName;
    private EditText    mAddress;
    private Button      mButtonOK;
    private Button      mButtonCancel;
    private Bundle      mMap;
    private String      mItemUrl;
    private boolean     mIsAdding;
    private TextView    mDialogText;

    // Message IDs
    private static final int SAVE_SITE_NAVIGATION = 100;

    private Handler mHandler;

    private View.OnClickListener mOKListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (save()) {
                AddMyNavigationPage.this.setResult(Activity.RESULT_OK, (new Intent()).putExtra("need_refresh", true));
                finish();
            }
        }
    };

    private View.OnClickListener mCancelListener = new View.OnClickListener() {
        public void onClick(View v) {
            finish();
        }
    };

    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.my_navigation_add_page);

        String name = null;
        String url = null;

        mMap = getIntent().getExtras(); 
        Log.d(LOGTAG, "onCreate mMap is : " + mMap);
        if (mMap != null) {
            Bundle b = mMap.getBundle("websites");
            if (b != null) {
                mMap = b;
            }
            name = mMap.getString("name");
            url = mMap.getString("url");
            mIsAdding = mMap.getBoolean("isAdding");
        }

        //The original url that is editting
        mItemUrl = url;

        mName = (EditText) findViewById(R.id.title);
        mAddress = (EditText) findViewById(R.id.address);

        BrowserUtils.lengthFilter2(AddMyNavigationPage.this, mName, BrowserUtils.filenameMaxLength);
        BrowserUtils.lengthFilter2(AddMyNavigationPage.this, mAddress, BrowserUtils.addressMaxLength);

        if (url.startsWith("ae://") && url.endsWith("add-fav")) {
            mName.setText("");
            mAddress.setText(""); 
        } else {
            mName.setText(name);
            mAddress.setText(url); 
        }
        mDialogText = (TextView) findViewById(R.id.dialog_title);
        if (mIsAdding) {
            mDialogText.setText(R.string.my_navigation_add_title);
        }

        mButtonOK = (Button)findViewById(R.id.OK);
        mButtonOK.setOnClickListener(mOKListener);

        mButtonCancel = (Button)findViewById(R.id.cancel);
        mButtonCancel.setOnClickListener(mCancelListener);

        if (!getWindow().getDecorView().isInTouchMode()) {
            mButtonOK.requestFocus();
        }
    } 

    /**
     * Runnable to save a website, so it can be performed in its own thread.
     */
    private class SaveMyNavigationRunnable implements Runnable {
        private Message mMessage;
        public SaveMyNavigationRunnable(Message msg) {
            mMessage = msg;
        }
        public void run() {
            // Unbundle website data.
            Bundle bundle = mMessage.getData();
            String title = bundle.getString("title");
            String url = bundle.getString("url");
            String itemUrl = bundle.getString("itemUrl");
            Boolean toDefaultThumbnail = bundle.getBoolean("toDefaultThumbnail");
            // Save to the my navigation DB.
            ContentResolver cr = AddMyNavigationPage.this.getContentResolver();
            Cursor cursor = null;
            try {
                cursor = cr.query(MyNavigationUtil.MY_NAVIGATION_URI,
                                  new String[] {MyNavigationUtil.ID}, "url = ?", new String[] {itemUrl}, null);
                if (cursor != null && cursor.moveToFirst()) {
                    ContentValues values = new ContentValues();
                    values.put(MyNavigationUtil.TITLE, title);
                    values.put(MyNavigationUtil.URL, url);
                    values.put(MyNavigationUtil.WEBSITE, 1 + "");
                    if (toDefaultThumbnail) {
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        Resources res = BrowserUtils.getResourcesFromExternalRes(AddMyNavigationPage.this);
                        int idRawDefault = BrowserUtils.getResourcesIdFromRes(res, "my_navigation_thumbnail_default", "raw", R.raw.my_navigation_thumbnail_default);
                        if (idRawDefault == R.raw.my_navigation_thumbnail_default) {
                            res = AddMyNavigationPage.this.getResources();
                        }
                        Bitmap bm = BitmapFactory.decodeResource(res, idRawDefault);
                        bm.compress(Bitmap.CompressFormat.PNG, 100, os);
                        values.put(MyNavigationUtil.THUMBNAIL, os.toByteArray());
                    }
                    Uri uri = ContentUris.withAppendedId(MyNavigationUtil.MY_NAVIGATION_URI, cursor.getLong(0));
                    Log.d(LOGTAG, "SaveMyNavigationRunnable uri is " + uri);
                    cr.update(uri, values, null, null);
                } else {
                    Log.e(LOGTAG, "this item does not exist!");
                }
            } catch (IllegalStateException e) {
                Log.e(LOGTAG, "SaveMyNavigationRunnable", e);
            } finally {
                if (null != cursor) {
                    cursor.close();
                }
            }
        }
    }


    boolean save() { 

        String name = mName.getText().toString().trim();
        String unfilteredUrl = UrlUtils.fixUrl(mAddress.getText().toString());
        boolean emptyTitle = name.length() == 0;
        boolean emptyUrl = unfilteredUrl.trim().length() == 0;
        Resources r = getResources();
        if (emptyTitle || emptyUrl) {
            if (emptyTitle) {
                mName.setError(r.getText(R.string.website_needs_title));
            }
            if (emptyUrl) {
                mAddress.setError(r.getText(R.string.website_needs_url));
            }
            return false;
        }
        String url = unfilteredUrl.trim();
        try {
            // We allow website with a javascript: scheme, but these will in most cases
            // fail URI parsing, so don't try it if that's the kind of bookmark we have.

            if (!url.toLowerCase().startsWith("javascript:")) {
                URI uriObj = new URI(url);
                String scheme = uriObj.getScheme();
                if (!MyNavigationUtil.urlHasAcceptableScheme(url)) {
                    // If the scheme was non-null, let the user know that we
                    // can't save their website. If it was null, we'll assume
                    // they meant http when we parse it in the WebAddress class.
                    if (scheme != null) {
                        mAddress.setError(r.getText(R.string.my_navigation_cannot_save_url));
                        return false;
                    }
                    WebAddress address;
                    try {
                        address = new WebAddress(unfilteredUrl);
                    } catch (ParseException e) {
                        throw new URISyntaxException("", "");
                    }
                    if (address.getHost().length() == 0) {
                        throw new URISyntaxException("", "");
                    }
                    url = address.toString();
                } else {
                    String mark = "://";
                    int iRet = -1;
                    if (null != url) {
                        iRet = url.indexOf(mark);
                    }
                    if (iRet > 0 && url.indexOf("/", iRet + mark.length()) < 0) {
                        url = url + "/";
                        Log.d(LOGTAG, "URL=" + url);
                    }
                }
            }
        } catch (URISyntaxException e) {
            mAddress.setError(r.getText(R.string.bookmark_url_not_valid));
            return false;
        }

        // When it is adding, avoid duplicate url that already existing in the database
        if (!mItemUrl.equals(url)) {
            boolean exist = MyNavigationUtil.isMyNavigationUrl(this, url);
            if (exist) {
                mAddress.setError(r.getText(R.string.my_navigation_duplicate_url));
                return false;
            }
        }
        // Post a message to write to the DB.
        Bundle bundle = new Bundle();
        bundle.putString("title", name);
        bundle.putString("url", url);
        bundle.putString("itemUrl", mItemUrl);
        if (!mItemUrl.equals(url)) {
            bundle.putBoolean("toDefaultThumbnail", true);
        } else {
            bundle.putBoolean("toDefaultThumbnail", false);
        }
        Message msg = Message.obtain(mHandler, SAVE_SITE_NAVIGATION);
        msg.setData(bundle);
        // Start a new thread so as to not slow down the UI
        Thread t = new Thread(new SaveMyNavigationRunnable(msg));
        t.start();

        return true; 
    }  
} 
