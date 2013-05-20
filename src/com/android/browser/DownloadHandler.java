/*
 * Copyright (C) 2010 The Android Open Source Project
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

import java.net.URI;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.MediaFile;
import android.net.Uri;
import android.net.WebAddress;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.widget.Toast;
import android.content.DialogInterface;
import java.io.File;
import android.app.DownloadManager.Request;
import android.os.StatFs;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * Handle download requests
 */
public class DownloadHandler {

    private static final boolean LOGD_ENABLED = com.android.browser.Browser.LOGD_ENABLED;

    private static final String LOGTAG = "DLHandler";
    public static String INTERNAL_STORAGE_SUFFIX;
    public static String EXTERNAL_STORAGE_SUFFIX;
    /**
     * Notify the host application a download should be done, or that
     * the data should be streamed if a streaming viewer is available.
     * @param activity Activity requesting the download.
     * @param url The full url to the content that should be downloaded
     * @param userAgent User agent of the downloading application.
     * @param contentDisposition Content-disposition http header, if present.
     * @param mimetype The mimetype of the content reported by the server
     * @param referer The referer associated with the downloaded url
     * @param privateBrowsing If the request is coming from a private browsing tab.
     */
    public static boolean onDownloadStart(final Activity activity, final String url,
                                          final String userAgent, final String contentDisposition, final String mimetype,
                                          final String referer, final boolean privateBrowsing,final Controller mcontroller,final Tab tab, final long contentLength) {
        // if we're dealing wih A/V content that's not explicitly marked
        //     for download, check if it's streamable.
        if (contentDisposition == null
            || !contentDisposition.regionMatches(
                                                true, 0, "attachment", 0, 10)) {
            // query the package manager to see if there's a registered handler
            //     that matches.
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            Log.v(LOGTAG,"DownloadHandler scheme ="+scheme+" mimetype ="+mimetype);
            // If downloading video or audio file, pop up a dialog to let
            // the user choose play or download. However, some audio or
            // video files with mime type not started with audio or video,
            // such as ogg audio file with mime type "application/ogg". So
            // we add extra check by MediaFile.isAudioFileType() and
            // MediaFile.isVideoFileType(). For other file types other than
            // audio or video, download it immediately.
            int fileType = MediaFile.getFileTypeForMimeType(mimetype);
            if ("http".equalsIgnoreCase(scheme) &&
                (mimetype.startsWith("video/") ||mimetype.startsWith("audio/") ||
                 mimetype.equalsIgnoreCase("application/x-mpegurl") ||
                 mimetype.equalsIgnoreCase("application/vnd.apple.mpegurl") ||
                 MediaFile.isAudioFileType(fileType) ||
                 MediaFile.isVideoFileType(fileType))) {

                new AlertDialog.Builder(activity)
                    .setTitle(R.string.download_or_play_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.download_or_play_msg)
                    .setPositiveButton(R.string.download_btn,new DialogInterface.OnClickListener(){
                        public void onClick(DialogInterface dialog, int which) {
                            onDownloadStartNoStream(activity, url, userAgent, contentDisposition, mimetype, referer, privateBrowsing, contentLength);
                        }
                    })
                    .setNegativeButton(R.string.play_online_btn,new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int which) {
                           Intent intent = new Intent(Intent.ACTION_VIEW);
                           intent.setDataAndType(Uri.parse(url), mimetype);
                           try {
                               String title = URLUtil.guessFileName(url,contentDisposition,mimetype);
                               intent.putExtra(Intent.EXTRA_TITLE,title);
                               activity.startActivity(intent);
                           } catch (ActivityNotFoundException ex) {
                               Log.d(LOGTAG, "When http stream play, activity not found for " + mimetype
                                     + " over " + Uri.parse(url).getScheme(),ex);
                           }
                       }
                    })
                    .show();

                return true;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(url), mimetype);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ResolveInfo info = activity.getPackageManager().resolveActivity(intent,
                                                                            PackageManager.MATCH_DEFAULT_ONLY);
            if (info != null) {
                ComponentName myName = activity.getComponentName();
                // If we resolved to ourselves, we don't want to attempt to
                // load the url only to try and download it again.
                if (!myName.getPackageName().equals(
                                                   info.activityInfo.packageName)
                    || !myName.getClassName().equals(
                                                    info.activityInfo.name)) {
                    // someone (other than us) knows how to handle this mime
                    // type with this scheme, don't download.
                    try {
                        activity.startActivity(intent);
                        return false;
                    } catch (ActivityNotFoundException ex) {
                        if (LOGD_ENABLED) {
                            Log.d(LOGTAG, "activity not found for " + mimetype
                                  + " over " + Uri.parse(url).getScheme(),
                                  ex);
                        }
                        // Best behavior is to fall back to a download in this
                        // case
                    }
                }
            }
        }
        onDownloadStartNoStream(activity, url, userAgent, contentDisposition,
                                mimetype, referer, privateBrowsing, contentLength);
        return false;
    }

    // This is to work around the fact that java.net.URI throws Exceptions
    // instead of just encoding URL's properly
    // Helper method for onDownloadStartNoStream
    private static String encodePath(String path) {
        char[] chars = path.toCharArray();

        boolean needed = false;
        for (char c : chars) {
            if (c == '[' || c == ']' || c == '|') {
                needed = true;
                break;
            }
        }
        if (needed == false) {
            return path;
        }

        StringBuilder sb = new StringBuilder("");
        for (char c : chars) {
            if (c == '[' || c == ']' || c == '|') {
                sb.append('%');
                sb.append(Integer.toHexString(c));
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * Notify the host application a download should be done, even if there
     * is a streaming viewer available for thise type.
     * @param activity Activity requesting the download.
     * @param url The full url to the content that should be downloaded
     * @param userAgent User agent of the downloading application.
     * @param contentDisposition Content-disposition http header, if present.
     * @param mimetype The mimetype of the content reported by the server
     * @param referer The referer associated with the downloaded url
     * @param privateBrowsing If the request is coming from a private browsing tab.
     */
    /*package */ public static void onDownloadStartNoStream(Activity activity,
                                                            String url, String userAgent, String contentDisposition,
                                                            String mimetype, String referer, boolean privateBrowsing, long contentLength) {

        initStorageDefaultPath();

        if (mimetype != null && !Pattern.matches("^\\w*/\\w*$", mimetype) ) {
            mimetype = mimetype.replaceAll("\"", "");
        }

        Log.d(LOGTAG, "contentDisposition is : ->" + contentDisposition);
        String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
        Log.d(LOGTAG, "start filenameis :->" + filename + 
              " and mimetype is :->" + mimetype);
        if (mimetype == null) {
            // We must have long pressed on a link or image to download it. We
            // are not sure of the mimetype in this case, so do a head request
            new FetchUrlMimeTypeAndContentLength(activity, url, userAgent, referer, privateBrowsing, filename).start();
        } else {
            startDownloadSettings(activity, url, userAgent, contentDisposition,  mimetype, referer, privateBrowsing, contentLength, filename);
        }
    }

    public static void initStorageDefaultPath() {
        EXTERNAL_STORAGE_SUFFIX = Environment.getExternalStorageDirectory().getPath();
        if (isPhoneStorageSupported()) {
            INTERNAL_STORAGE_SUFFIX= getPhoneStorageDirectory();
        } else {
            INTERNAL_STORAGE_SUFFIX = null;
        }

    }

    public static void startDownloadSettings(Activity activity,
                                             String url, String userAgent, String contentDisposition,
                                             String mimetype, String referer, boolean privateBrowsing, long contentLength, String filename) {

        Bundle fileInfo = new  Bundle();
        fileInfo.putString("url",url);
        fileInfo.putString("userAgent", userAgent);
        fileInfo.putString("contentDisposition", contentDisposition);
        fileInfo.putString("mimetype", mimetype);
        fileInfo.putString("referer", referer);        
        fileInfo.putLong("contentLength", contentLength);
        fileInfo.putBoolean("privateBrowsing", privateBrowsing);
        fileInfo.putString("filename", filename);
        Intent intent = new Intent("android.intent.action.BROWSERDOWNLOAD");
        intent.putExtras(fileInfo);
        int nFlags = intent.getFlags();
        nFlags &= (~Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setFlags(nFlags);

        activity.startActivity(intent);
    }



    public static void setAppointedFolder(String downloadPath) {
        File file = new File(downloadPath);
        if (file.exists()) {
            if (!file.isDirectory()) {
                throw new IllegalStateException(file.getAbsolutePath() +
                                                " already exists and is not a directory");
            }
        } else {
            if (!file.mkdir()) {
                throw new IllegalStateException("Unable to create directory: "+
                                                file.getAbsolutePath());
            }
        }
    }

    public static void setDestinationDir(String downloadPath, String filename, Request request) {
        File file = new File(downloadPath);         
        if (file.exists()) {
            if (!file.isDirectory()) {
                throw new IllegalStateException(file.getAbsolutePath() +
                                                " already exists and is not a directory");
            }
        } else {
            if (!file.mkdir()) {
                throw new IllegalStateException("Unable to create directory: "+
                                                file.getAbsolutePath());
            }
        }
        setDestinationFromBase(file, filename, request);
    }

    private static void setDestinationFromBase(File file, String filename, Request request) {
        if (filename == null) {
            throw new NullPointerException("filename cannot be null");
        }
        request.setDestinationUri(Uri.withAppendedPath(Uri.fromFile(file), filename));
    }

    public static void fileExistQueryDialog(Activity activity){
        new AlertDialog.Builder(activity)
            .setTitle(R.string.download_file_exist)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setMessage(R.string.download_file_exist_msg)
            //if yes, delete existed file and start new download thread
            .setPositiveButton(R.string.ok, null)
            //if no, do nothing at all		
            .show();
    }

    public static long getAvailableMemory(String root) {
        StatFs stat = new StatFs(root);
        final long LEFT10MByte = 2560;
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks() - LEFT10MByte;
        return availableBlocks * blockSize; 
    }  

    public static void showNoEnoughMemoryDialog(Activity mContext) {
        new AlertDialog.Builder(mContext)
            .setTitle(R.string.download_no_enough_memory)
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setMessage(R.string.download_no_enough_memory)
            .setPositiveButton(R.string.ok, null)
            .show();
    }   

    public  static boolean manageNoEnoughMemory (Activity mContext, long contentLength, String root) { 
        Log.i(LOGTAG,"----------- download file contentLength is ------------>"+contentLength);
        long mAvailableBytes = getAvailableMemory(root);
        if (mAvailableBytes > 0) {
            if (contentLength > mAvailableBytes) {
                showNoEnoughMemoryDialog(mContext);
                return true;
            }
        } else {
            showNoEnoughMemoryDialog(mContext);    
            return true;     
        }
        return false;
    }     

    public static void showStartDownloadToast(Activity activity) {
        Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
        activity.startActivity(intent);        
        Toast.makeText(activity, R.string.download_pending, Toast.LENGTH_SHORT)
        .show();
    }

    /**
     * the operation for starting download 
     * 
     * @param activity Activity requesting the download.
     * @param url The full url to the content that should be downloaded
     * @param userAgent User agent of the downloading application.
     * @param contentDisposition Content-disposition http header, if present.
     * @param mimetype The mimetype of the content reported by the server
     * @param referer The referer associated with the downloaded url
     * @param privateBrowsing If the request is coming from a private browsing tab. 
     * @param contentLength file's size
     * @param filename the download file's name
     * @param downloadPath the downlaod file will be save in 
     */
    public static void  startingDownload(Activity activity,
                                         String url, String userAgent, String contentDisposition,
                                         String mimetype,  String referer, boolean privateBrowsing, long contentLength, String filename , String downloadPath) {
        // java.net.URI is a lot stricter than KURL so we have to encode some
        // extra characters. Fix for b 2538060 and b 1634719
        WebAddress webAddress;
        try {
            webAddress = new WebAddress(url);
            webAddress.setPath(encodePath(webAddress.getPath()));
        } catch (Exception e) {
            // This only happens for very bad urls, we want to chatch the
            // exception here
            Log.e(LOGTAG, "Exception trying to parse url:" + url);
            return;
        }

        String addressString = webAddress.toString();
        Uri uri = Uri.parse(addressString);
        final DownloadManager.Request request;
        try {
            request = new DownloadManager.Request(uri);
        } catch (IllegalArgumentException e) {
            Toast.makeText(activity, R.string.cannot_download, Toast.LENGTH_SHORT).show();
            return;
        }
        request.setMimeType(mimetype);
        // set downloaded file destination to /sdcard/Download.
        // or, should it be set to one of several Environment.DIRECTORY* dirs depending on mimetype?
        //request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
        try {
            setDestinationDir(downloadPath, filename, request);
        } catch (Exception e) {
            showNoEnoughMemoryDialog(activity);
            return;
        }
        // let this downloaded file be scanned by MediaScanner - so that it can 
        // show up in Gallery app, for example.
        request.allowScanningByMediaScanner();
        request.setDescription(webAddress.getHost());
        // XXX: Have to use the old url since the cookies were stored using the
        // old percent-encoded url.
        String cookies = CookieManager.getInstance().getCookie(url, privateBrowsing);
        request.addRequestHeader("cookie", cookies);
        request.addRequestHeader("User-Agent", userAgent);
        request.addRequestHeader("Referer", referer);
        request.setNotificationVisibility(
                                         DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        final DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        new Thread("Browser download") {
            public void run() {
                manager.enqueue(request);
            }
        }.start();
        showStartDownloadToast(activity);
    }

    /**
     * wheather the storage status OK for download file
     * 
     * @param activity 
     * @param filename the download file's name 
     * @param downloadPath the download file's path will be in
     * 
     * @return boolean true is ok,and false is not
     */
    public static boolean isStorageStatusOK(Activity activity, String filename, String downloadPath) {

        if (!(isPhoneStorageSupported() && downloadPath.contains(INTERNAL_STORAGE_SUFFIX))) {
            String status = Environment.getExternalStorageState();
            if (!status.equals(Environment.MEDIA_MOUNTED)) {
                int title;
                String msg;

                // Check to see if the SDCard is busy, same as the music app
                if (status.equals(Environment.MEDIA_SHARED)) {
                    msg = activity.getString(R.string.download_sdcard_busy_dlg_msg);
                    title = R.string.download_sdcard_busy_dlg_title;
                } else {
                    msg = activity.getString(R.string.download_no_sdcard_dlg_msg, filename);
                    title = R.string.download_no_sdcard_dlg_title;
                }

                new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(msg)
                    .setPositiveButton(R.string.ok, null)
                    .show();
                return false;
            }
        } else {
            String status = getPhoneStorageState();
            if (!status.equals(Environment.MEDIA_MOUNTED)) {
                int mTitle = R.string.download_path_unavailable_dlg_title;
                String mMsg = activity.getString(R.string.download_path_unavailable_dlg_msg);
                new AlertDialog.Builder(activity)
                    .setTitle(mTitle)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(mMsg)
                    .setPositiveButton(R.string.ok, null)
                    .show();
                return false;
            }
        }
        return true;
    }

    /**
     * wheather support Phone Storage
     * 
     * @return boolean true support Phone Storage ,false will be not
     */
    public static boolean isPhoneStorageSupported() {
        return true;
    }

    /**
     * 
     * show Dialog to warn filename is null
     * 
     * @param activity 
     */
    public static void showFilenameEmptyDialog(Activity activity) {
        new AlertDialog.Builder(activity)
            .setTitle(R.string.filename_empty_title)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(R.string.filename_empty_msg)
            .setPositiveButton(R.string.ok,  new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {                           
            }})
            .show();
    }

    /**
    * get the filename except the suffix and dot
    * 
    * 
    * @return String  the filename except suffix and dot
    */
    public static String getFilenameBase(String filename) {
        int dotindex = filename.lastIndexOf('.');
        if (dotindex != -1) {
            return filename.substring(0, dotindex); 
        } else {
            return "";
        }
    }

    /**
    * get the filename's extension from filename
    * 
    * @param filename the download filename, may be the user 
    *                 entered 
    * 
    * @return String the filename's extension
    */
    public static String getFilenameExtension(String filename) {
        int dotindex = filename.lastIndexOf('.');
        if (dotindex != -1) {
            return filename.substring(dotindex + 1); 
        } else {
            return "";
        }

    }

    /**
     * 
     * translate the directory name into a name which is easy to 
     * know for user 
     * 
     * @param activity 
     * @param downloadPath 
     * 
     * @return String 
     */
    public static String getDownloadPathForUser(Activity activity, String downloadPath) {
        if (downloadPath == null) {
            return downloadPath;
        }
        final String phoneStorageDir;
        final String sdCardDir = Environment.getExternalStorageDirectory().getPath();;
        if (DownloadHandler.isPhoneStorageSupported()) {
            phoneStorageDir = getPhoneStorageDirectory();
        } else {
            phoneStorageDir = null;       
        } 

        if (downloadPath.startsWith(sdCardDir)) {
            String sdCardLabel = activity.getResources().getString(R.string.download_path_sd_card_label);
            downloadPath = downloadPath.replace(sdCardDir, sdCardLabel);
        } else if ((phoneStorageDir != null) && downloadPath.startsWith(phoneStorageDir)) {
            String phoneStorageLabel = activity.getResources().getString(R.string.download_path_phone_stroage_label);
            downloadPath = downloadPath.replace(phoneStorageDir, phoneStorageLabel);
        }
        return  downloadPath;       
    }

    /**
       * get Phone Storage Directory
       * 
       * @return String  get Phone Storage Directory
       */
    public static String getPhoneStorageDirectory() {
        Method[] methods = Environment.class.getMethods();
        String phoneStorageDirectory = "";
        for (int idx = 0; idx < methods.length; idx++) {
            if (methods[idx].getName().equals("getInternalStorageDirectory")) {
                try {
                    File phoneFile = (File) methods[idx].invoke(Environment.class);
                    if (phoneFile != null) {
                        phoneStorageDirectory = phoneFile.getPath();
                    }
                } catch (Exception ex) {
                    Log.e(LOGTAG, " getPhoneStorageDirectory exception");
                } 
            }
        }
        return phoneStorageDirectory;
    }


    /**
     * get Phone Stroage State 
     * 
     * @return String true Phone Stroage State 
     */
    public static String getPhoneStorageState() {
        Method[] methods = Environment.class.getMethods();
        String phoneStorageState = "";
        for (int idx = 0; idx < methods.length; idx++) {
            if (methods[idx].getName().equals("getInternalStorageState")) {
                try {
                    phoneStorageState = (String) methods[idx].invoke(Environment.class);
                } catch (Exception ex) {
                    Log.e(LOGTAG, "getPhoneStorageState exception");
                } 
            }
        }
        return phoneStorageState;
    }

    public static String getDefaultDownloadPath(Context mContext) {
        String defaultDownloadPath;

        String defaultStorage; 
        if (isPhoneStorageSupported()) {
            defaultStorage = getPhoneStorageDirectory();
        } else {
            defaultStorage = Environment.getExternalStorageDirectory().getPath();               
        }        

        defaultDownloadPath = defaultStorage + mContext.getString(R.string.default_savepath_name);
        Log.e(LOGTAG,"defaultStorage directory is : " + defaultDownloadPath);
        return defaultDownloadPath;
    }
}
