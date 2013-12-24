/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.browser;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.conn.params.ConnRouteParams;

import android.app.Activity;
import android.content.Context;
import android.net.Proxy;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import java.io.IOException;

/**
 * This class is used to pull down the http headers of a given URL so that
 * we can analyse the mimetype and make any correction needed before we give
 * the URL to the download manager.
 * This operation is needed when the user long-clicks on a link or image and
 * we don't know the mimetype. If the user just clicks on the link, we will
 * do the same steps of correcting the mimetype down in
 * android.os.webkit.LoadListener rather than handling it here.
 *
 */
class FetchUrlMimeType extends Thread {

    private final static String LOGTAG = "FetchUrlMimeType";

    private Context mContext;
    private String mUri;
    private String mUserAgent;
    private String mFilename;
    private String mReferer;
    private Activity mActivity;
    private boolean mPrivateBrowsing;
    private long mContentLength;

    public FetchUrlMimeType(Activity activity, String url, String userAgent,
            String referer, boolean privateBrowsing, String filename) {
        mActivity = activity;
        mContext = activity.getApplicationContext();
        mUri = url;
        mUserAgent = userAgent;
        mPrivateBrowsing = privateBrowsing;
        mFilename = filename;
        mReferer = referer;
    }

    @Override
    public void run() {
        // User agent is likely to be null, though the AndroidHttpClient
        // seems ok with that.
        AndroidHttpClient client = AndroidHttpClient.newInstance(mUserAgent);
        HttpHost httpHost;
        try {
            httpHost = Proxy.getPreferredHttpHost(mContext, mUri);
            if (httpHost != null) {
                ConnRouteParams.setDefaultProxy(client.getParams(), httpHost);
            }
        } catch (IllegalArgumentException ex) {
            Log.e(LOGTAG,"Download failed: " + ex);
            client.close();
            return;
        }
        HttpHead request = new HttpHead(mUri);

        String cookies = CookieManager.getInstance().getCookie(mUri, mPrivateBrowsing);
        if (cookies != null && cookies.length() > 0) {
            request.addHeader("Cookie", cookies);
        }

        HttpResponse response;
        String filename = mFilename;
        String mimeType = null;
        String contentDisposition = null;
        String contentLength = null;
        try {
            response = client.execute(request);
            // We could get a redirect here, but if we do lets let
            // the download manager take care of it, and thus trust that
            // the server sends the right mimetype
            if (response.getStatusLine().getStatusCode() == 200) {
                Header header = response.getFirstHeader("Content-Type");
                if (header != null) {
                    mimeType = header.getValue();
                    final int semicolonIndex = mimeType.indexOf(';');
                    if (semicolonIndex != -1) {
                        mimeType = mimeType.substring(0, semicolonIndex);
                    }
                }
                Header contentLengthHeader = response.getFirstHeader("Content-Length");
                if (contentLengthHeader != null) {
                    contentLength = contentLengthHeader.getValue();
                }
                Header contentDispositionHeader = response.getFirstHeader("Content-Disposition");
                if (contentDispositionHeader != null) {
                    contentDisposition = contentDispositionHeader.getValue();
                }
            }
        } catch (IllegalArgumentException ex) {
            request.abort();
        } catch (IOException ex) {
            request.abort();
        } finally {
            client.close();
        }

        if (mimeType != null) {
            Log.e(LOGTAG, "-----------the mimeType from http header is ------------->" + mimeType);
            if (mimeType.equalsIgnoreCase("text/plain") ||
                    mimeType.equalsIgnoreCase("application/octet-stream")) {
                String newMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        MimeTypeMap.getFileExtensionFromUrl(mUri));
                if (newMimeType != null) {
                    mimeType = newMimeType;
                }
            }

            String fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (fileExtension == null || (fileExtension != null && fileExtension.equals("bin"))) {
                fileExtension = MimeTypeMap.getFileExtensionFromUrl(mUri);
                if (fileExtension == null) {
                    fileExtension = "bin";
                }
            }
            filename = DownloadHandler.getFilenameBase(filename) + "." + fileExtension;

        } else {
            String fileExtension = getFileExtensionFromUrlEx(mUri);
            if (fileExtension == "") {
                fileExtension = "bin";
            }
            String newMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
            if (newMimeType != null) {
                mimeType = newMimeType;
            }
            filename = URLUtil.guessFileName(mUri, contentDisposition, mimeType);

        }

        if (contentLength != null) {
            mContentLength = Long.parseLong(contentLength);
        } else {
            mContentLength = 0;
        }

        DownloadHandler.startDownloadSettings(mActivity, mUri, mUserAgent, contentDisposition,
                mimeType, mReferer, mPrivateBrowsing, mContentLength, filename);
    }

    /**
     * when we can not parse MineType and Filename from the header of http body
     * ,Call the fallowing functions for this matter
     * getFileExtensionFromUrlEx(String url) : get the file Extension from Url
     * guessFileNameEx() : get the file name from url Note: this modified for
     * download http://www.baidu.com girl picture error extension and error
     * filename
     */
    private String getFileExtensionFromUrlEx(String url) {
        Log.e("FetchUrlMimeType",
                "--------can not get mimetype from http header, the URL is ---------->" + url);
        if (!TextUtils.isEmpty(url)) {
            int fragment = url.lastIndexOf('#');
            if (fragment > 0) {
                url = url.substring(0, fragment);
            }

            int filenamePos = url.lastIndexOf('/');
            String filename =
                    0 <= filenamePos ? url.substring(filenamePos + 1) : url;
            Log.e(LOGTAG,
                    "--------can not get mimetype from http header, the temp filename is----------"
                            + filename);
            // if the filename contains special characters, we don't
            // consider it valid for our matching purposes:
            if (!filename.isEmpty()) {
                int dotPos = filename.lastIndexOf('.');
                if (0 <= dotPos) {
                    return filename.substring(dotPos + 1);
                }
            }
        }

        return "";
    }

}
