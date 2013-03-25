package com.android.browser.mynavigation;

import android.content.Context;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.android.browser.R;

public class MyNavigationRequestHandler extends Thread {

    private static final String LOGTAG = "MyNavigationRequestHandler";
    private static final int MY_NAVIGATION = 1;
    private static final int RESOURCE = 2;
    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    Uri mUri;
    Context mContext;
    OutputStream mOutput;

    static {
        URI_MATCHER.addURI(MyNavigationUtil.AUTHORITY, "websites/res/*/*", RESOURCE);
        URI_MATCHER.addURI(MyNavigationUtil.AUTHORITY, "websites", MY_NAVIGATION);
    }

    public MyNavigationRequestHandler(Context context, Uri uri, OutputStream out) {
        mUri = uri;
        mContext = context.getApplicationContext();
        mOutput = out;
    }

    @Override
    public void run() {
        super.run();
        try {
            doHandleRequest();
        } catch (IOException e) {
            Log.e(LOGTAG, "Failed to handle request: " + mUri, e);
        } finally {
            cleanup();
        }
    }

    void doHandleRequest() throws IOException {
        int match = URI_MATCHER.match(mUri);
        switch (match) {
        case MY_NAVIGATION:
            writeTemplatedIndex();
            break;
        case RESOURCE:
            writeResource(getUriResourcePath());
            break;
        default:
            break;
        }
    }

    private void writeTemplatedIndex()  throws IOException {
        MyNavigationTemplate t = MyNavigationTemplate.getCachedTemplate(mContext, R.raw.my_navigation);
        Cursor cursor = mContext.getContentResolver().query(
                                                           Uri.parse("content://com.android.browser.mynavigation/websites"),
                                                           new String[] { "url", "title", "thumbnail"},
                                                           null, null, null);


        t.assignLoop("my_navigation", new MyNavigationTemplate.CursorListEntityWrapper(cursor) {
                         @Override
                         public void writeValue(OutputStream stream, String key) throws IOException {
                             Cursor cursor = getCursor();
                             if (key.equals("url")) {
                                 stream.write(htmlEncode(cursor.getString(0)));
                             } else if (key.equals("title")) {
                                 String title = cursor.getString(1);
                                 if (title == null || title.length() == 0) {
                                     title = mContext.getString(R.string.my_navigation_add);
                                 }
                                 stream.write(htmlEncode(title));

                             } else if (key.equals("thumbnail")) {
                                 stream.write("data:image/png".getBytes());
                                 stream.write(htmlEncode(cursor.getString(0)));
                                 stream.write(";base64,".getBytes());                        
                                 byte[] thumb = cursor.getBlob(2);
                                 stream.write(Base64.encode(thumb, Base64.DEFAULT));
                             }
                         }
                     });
        t.write(mOutput);
        cursor.close();
    }

    byte[] htmlEncode(String s) {
        return TextUtils.htmlEncode(s).getBytes();
    }


    String getUriResourcePath() {
        final Pattern pattern = Pattern.compile("/?res/([\\w/]+)");
        Matcher m = pattern.matcher(mUri.getPath());
        if (m.matches()) {
            return m.group(1);
        } else {
            return mUri.getPath();
        }
    }

    void writeResource(String fileName) throws IOException {
        Resources res = mContext.getResources();
        String packageName = R.class.getPackage().getName();
        int id = res.getIdentifier(fileName, null, packageName);
        if (id != 0) {
            InputStream in = res.openRawResource(id);
            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) > 0) {
                mOutput.write(buf, 0, read);
            }
        }
    }

    void writeString(String str) throws IOException {
        mOutput.write(str.getBytes());
    }

    void writeString(String str, int offset, int count) throws IOException {
        mOutput.write(str.getBytes(), offset, count);
    }

    void cleanup() {
        try {
            mOutput.close();
        } catch (IOException e) {
            Log.e(LOGTAG, "Failed to close pipe!", e);
        }
    }

}
