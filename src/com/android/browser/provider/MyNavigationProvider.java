package com.android.browser.provider;


import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.android.browser.BrowserUtils;
import com.android.browser.R;
import com.android.browser.mynavigation.MyNavigationRequestHandler;
import com.android.browser.mynavigation.MyNavigationUtil;
import com.android.browser.provider.BrowserProvider2;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import android.content.res.Resources;

public class MyNavigationProvider extends ContentProvider {

    private static final String LOGTAG = "MyNavigationProvider";

    private static final String TABLE_WEB_SITES = "websites";

    private static final int WEB_SITES_ALL = 0;
    private static final int WEB_SITES_ID = 1;

    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        URI_MATCHER.addURI(MyNavigationUtil.AUTHORITY, "websites" , WEB_SITES_ALL);
        URI_MATCHER.addURI(MyNavigationUtil.AUTHORITY, "websites/#" , WEB_SITES_ID);
    }

    private static final Uri NOTIFICATION_URI = MyNavigationUtil.MY_NAVIGATION_URI;

    private SiteNavigationDatabaseHelper mOpenHelper;
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        //Current not used, just return 0
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        //Current not used, just return null
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        //Current not used, just return null
        return null;
    }

    @Override
    public boolean onCreate() {
        mOpenHelper =  new SiteNavigationDatabaseHelper(this.getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(TABLE_WEB_SITES);

        switch (URI_MATCHER.match(uri)) {
        case WEB_SITES_ALL:
            break;
        case WEB_SITES_ID:
            qb.appendWhere(MyNavigationUtil.ID + "=" + uri.getPathSegments().get(0));
            break;
        default:
            Log.e(LOGTAG, "MyNavigationProvider query Unknown URI: " + uri);
            return null;
        }

        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = null;
        } else {
            orderBy = sortOrder;
        }

        //Get the database and run the query        
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
        if (c != null) {
            //Tell the cursor what uri to watch, so it knows when its source data changes
            c.setNotificationUri(getContext().getContentResolver(), NOTIFICATION_URI);
        }
        return c;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, 
                      String[] selectionArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = 0;

        switch (URI_MATCHER.match(uri)) {
        case WEB_SITES_ALL:
            count = db.update(TABLE_WEB_SITES, values, selection, selectionArgs);
            break;            
        case WEB_SITES_ID:
            String newIdSelection = MyNavigationUtil.ID + "=" + uri.getLastPathSegment() 
                                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
            count = db.update(TABLE_WEB_SITES, values, newIdSelection, selectionArgs);
            break;
        default:
            Log.e(LOGTAG, "MyNavigationProvider update Unknown URI: " + uri);
            return count;
        }

        if (count > 0) {
            ContentResolver cr = getContext().getContentResolver();
            cr.notifyChange(uri, null);
        }
        return count;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) {
        try {
            ParcelFileDescriptor[] pipes = ParcelFileDescriptor.createPipe();
            final ParcelFileDescriptor write = pipes[1];
            AssetFileDescriptor afd = new AssetFileDescriptor(write, 0, -1);
            new MyNavigationRequestHandler(getContext(), uri, afd.createOutputStream())
            .start();
            return pipes[0];
        } catch (IOException e) {
            Log.e(LOGTAG, "Failed to handle request: " + uri, e);
            return null;
        }
    }

    private class SiteNavigationDatabaseHelper extends SQLiteOpenHelper {

        private Context mContext;
        static final String DATABASE_NAME = "mynavigation.db";

        public SiteNavigationDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, 1); //"1" is the db version here
            // TODO Auto-generated constructor stub
            mContext = context;
        }

        public SiteNavigationDatabaseHelper(Context context, String name,
                                            CursorFactory factory, int version) {
            super(context, name, factory, version);
            // TODO Auto-generated constructor stub
            mContext = context;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // TODO Auto-generated method stub

            // create table websites for my navigation
            createWebsitesTable(db);
            // initial table websites for my navigation
            initWebsitesTable(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
            // TODO Auto-generated method stub
        }

        private void createWebsitesTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE websites (" +
                       MyNavigationUtil.ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                       MyNavigationUtil.URL + " TEXT," +
                       MyNavigationUtil.TITLE + " TEXT," +
                       MyNavigationUtil.DATE_CREATED + " LONG," +
                       MyNavigationUtil.WEBSITE + " INTEGER," +
                       MyNavigationUtil.THUMBNAIL + " BLOB DEFAULT NULL," +
                       MyNavigationUtil.FAVICON + " BLOB DEFAULT NULL," +
                       MyNavigationUtil.DEFAULT_THUMB + " TEXT" +
                       ");");

        }

        //initial table , insert websites to table websites
        private void initWebsitesTable(SQLiteDatabase db) {  
                 
            int WebsiteNumber = MyNavigationUtil.WEBSITE_NUMBER;
            for (int i = 0; i < WebsiteNumber; i++) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                //get Resouces from external res package(Browser_res)
                Resources res = BrowserUtils.getResourcesFromExternalRes(mContext);
                int idRawAdd = BrowserUtils.getResourcesIdFromRes(res, "my_navigation_add", "raw", R.raw.my_navigation_add);
                if (idRawAdd == R.raw.my_navigation_add) {
                    res = mContext.getResources();
                }
                Bitmap bm = BitmapFactory.decodeResource(res, idRawAdd);
                bm.compress(Bitmap.CompressFormat.PNG, 100, os);
                ContentValues values = new ContentValues();
                values.put(MyNavigationUtil.URL, "ae://" + (i + 1) + "add-fav");
                values.put(MyNavigationUtil.TITLE, mContext.getString(R.string.my_navigation_add));
                values.put(MyNavigationUtil.DATE_CREATED, 0 + "");
                values.put(MyNavigationUtil.WEBSITE, 1 + "");
                values.put(MyNavigationUtil.THUMBNAIL, os.toByteArray());       
                db.insertOrThrow(TABLE_WEB_SITES, MyNavigationUtil.URL, values);
            }
            
        }
    }

    private  String getClientId(Context context) {
            String ret = "android-google";
            Cursor c = null;
        ContentResolver cr = context.getContentResolver();
            try {
                c = cr.query(Uri.parse("content://com.google.settings/partner"),
                        new String[] { "value" }, "name='client_id'", null, null);
                if (c != null && c.moveToNext()) {
                    ret = c.getString(0);
                }
            } catch (RuntimeException ex) {
                // fall through to return the default
            } finally {
                if (c != null) {
                    c.close();
                }
            }
            return ret;
        }

    private CharSequence replaceSystemPropertyInString(Context context, CharSequence srcString) {
            StringBuffer sb = new StringBuffer();
            int lastCharLoc = 0;

        final String clientId = getClientId(context);

            for (int i = 0; i < srcString.length(); ++i) {
                char c = srcString.charAt(i);
                if (c == '{') {
                    sb.append(srcString.subSequence(lastCharLoc, i));
                    lastCharLoc = i;
              inner:
                    for (int j = i; j < srcString.length(); ++j) {
                        char k = srcString.charAt(j);
                        if (k == '}') {
                            String propertyKeyValue = srcString.subSequence(i + 1, j).toString();
                            if (propertyKeyValue.equals("CLIENT_ID")) {
                            sb.append(clientId);
                            } else {
                                sb.append("unknown");
                            }
                            lastCharLoc = j + 1;
                            i = j;
                            break inner;
                        }
                    }
                }
            }
            if (srcString.length() - lastCharLoc > 0) {
                // Put on the tail, if there is one
                sb.append(srcString.subSequence(lastCharLoc, srcString.length()));
            }
            return sb;
        }


}
