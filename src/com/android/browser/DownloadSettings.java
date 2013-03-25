/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (c) 2012, The Linux Foundation. All rights reserved.
 *
 * Not a Contribution. Apache license notifications and license are retained
 * for attribution purposes only.
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

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import java.lang.Thread;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.*;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.view.Window;
import android.widget.Toast;

public class DownloadSettings extends Activity {

    private EditText downloadFilenameET;
    private EditText downloadPathET;
    private TextView downloadEstimateSize;
    private TextView downloadEstimateTime;
    private Button downloadStart;
    private Button downloadCancel;
    private String url;
    private String userAgent;
    private String contentDisposition;
    private String mimetype;
    private String referer;
    private String filenameBase;
    private String filename;
    private String filenameExtension;
    private boolean privateBrowsing;
    private long contentLength;
    private String downloadPath;    
    private String downloadPathForUser;
    private static final int downloadRate = (1024*100*60);// Download Rate 100KB/s     
    private final static String LOGTAG = "DownloadSettings";
    private final static int DOWNLOAD_PATH = 0;
    private boolean isSelectPath = false;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //initial the DownloadSettings view
	     requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.download_settings);
        downloadFilenameET = (EditText) findViewById(R.id.download_filename_edit);
        downloadPathET = (EditText) findViewById(R.id.download_filepath_selected);
        downloadEstimateSize = (TextView) findViewById(R.id.download_estimate_size_content);
        downloadEstimateTime = (TextView) findViewById(R.id.download_estimate_time_content);
        downloadStart = (Button) findViewById(R.id.download_start);
        downloadCancel = (Button) findViewById(R.id.download_cancle);
        downloadPathET.setOnClickListener(downloadPathListener);
        downloadStart.setOnClickListener(downloadStartListener);
        downloadCancel.setOnClickListener(downloadCancelListener);

        //get the bundle from Intent
        Intent intent = getIntent();
        Bundle fileInfo = intent.getExtras();
        url = fileInfo.getString("url");
        userAgent = fileInfo.getString("userAgent");
        contentDisposition = fileInfo.getString("contentDisposition");
        mimetype = fileInfo.getString("mimetype");  
        referer = fileInfo.getString("referer");       
        contentLength = fileInfo.getLong("contentLength");
        privateBrowsing = fileInfo.getBoolean("privateBrowsing");
        filename = fileInfo.getString("filename");

        if (true) {
            Log.e(LOGTAG, "--------- url ---------" + url);
            Log.e(LOGTAG, "--------- userAgent ---------" + userAgent);
            Log.e(LOGTAG, "--------- contentDisposition ---------" + contentDisposition);
            Log.e(LOGTAG, "--------- mimetype ---------" + mimetype);
            Log.e(LOGTAG, "--------- contentLength ---------" + contentLength);
            Log.e(LOGTAG, "--------- privateBrowsing ---------" + privateBrowsing);
            Log.e(LOGTAG, "--------- filename ---------" + filename);
        }

        //download filenamebase's length is depended on filenameLength's values
        //if filenamebase.length >= flienameLength, destroy the last string!

        filenameBase = DownloadHandler.getFilenameBase(filename);
        if (filenameBase.length() >= (BrowserUtils.filenameMaxLength)) {
            filenameBase = filenameBase.substring(0, BrowserUtils.filenameMaxLength);
        }

        //warring when user enter more over letters into the EditText
        BrowserUtils.lengthFilter2(DownloadSettings.this, downloadFilenameET, BrowserUtils.filenameMaxLength);

        downloadFilenameET.setText(filenameBase);
        downloadPath = BrowserSettings.getInstance().getDownloadPathFromSettings();
        downloadPathForUser = DownloadHandler.getDownloadPathForUser(DownloadSettings.this, downloadPath);
        setDownloadPathForUserText(downloadPathForUser);
        setDownloadFileSizeText();
        setDownloadFileTimeText();

    }

    private OnClickListener downloadPathListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            isSelectPath = true;

            //start filemanager for getting download path
            try {
                Intent downloadPathIntent = new Intent("com.android.fileexplorer.action.DIR_SEL");
                DownloadSettings.this.startActivityForResult(downloadPathIntent, DOWNLOAD_PATH);
            } catch (Exception e) {
                String err_msg = getString(R.string.acivity_not_found, "com.android.fileexplorer.action.DIR_SEL");
                Toast.makeText(DownloadSettings.this , err_msg, Toast.LENGTH_LONG).show();
            }

        }
    };

    private OnClickListener downloadStartListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            filenameBase = getFilenameBaseFromUserEnter(); 
            //check the filename user enter is null or not           
            if (filenameBase.length() <= 0) {
                DownloadHandler.displayFilenameEmpty(DownloadSettings.this);
                return;
            }

            filenameExtension = DownloadHandler.getFilenameExtension(filename);
            filename = filenameBase + "." + filenameExtension;

            //check the storage status   
            if (!DownloadHandler.isStorageStatusOK(DownloadSettings.this, filename, downloadPath)) {
                return;
            }

            //check the storage memory enough or not
            try {
                DownloadHandler.setAppointedFolder(downloadPath);
            } catch (Exception e) {
                DownloadHandler.displayNoEnoughMemory(DownloadSettings.this);
                return;
            }
            boolean isNoEnoughMemory = DownloadHandler.manageNoEnoughMemory(DownloadSettings.this, contentLength, downloadPath);
            if (isNoEnoughMemory) {
                return;
            }

            //check the download file is exist or not 
            String fullFilename = downloadPath + "/" + filename;
            if (mimetype != null && new File(fullFilename).exists()) {
                DownloadHandler.fileExistQueryDialog(DownloadSettings.this);
                return;
            }

            //staring downloading 
            DownloadHandler.startingDownload(DownloadSettings.this,
                                             url, userAgent, contentDisposition,
                                             mimetype, referer, privateBrowsing, contentLength, filename, downloadPath);
        }
    };

    private OnClickListener downloadCancelListener = new OnClickListener() {

        @Override
        public void onClick(View v) {            
            finish();
        }
    };

    protected void onDestroy() {
        super.onDestroy();
    }

    protected void onPause() {
        super.onPause();
        if (!isSelectPath) {
            finish();
        }
    }

    protected void onResume() {
        super.onResume();     
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        isSelectPath = false;

        Log.e(LOGTAG,"isSelectPath value is : "+ isSelectPath);
        if (DOWNLOAD_PATH == requestCode) {
            if (resultCode == Activity.RESULT_OK && intent != null) {
                downloadPath = intent.getStringExtra("result_dir_sel");   
                if (downloadPath != null) {
                    downloadPathForUser = DownloadHandler.getDownloadPathForUser(DownloadSettings.this, downloadPath);
                    setDownloadPathForUserText(downloadPathForUser);
                    Log.e(LOGTAG,"downloadPath direcory is : "+ downloadPath);
                }
            }
        }
    }
    /**
     * show download path for user 
     * 
     * 
     * @param downloadPath the download path user can see
     */
    private void setDownloadPathForUserText(String downloadPathForUser) {
        downloadPathET.setText(downloadPathForUser);
    }

    /**
     * get the filename from user select the download path 
     * 
     * 
     * @return String  the filename from user selected
     */
    private String getFilenameBaseFromUserEnter() {
        return downloadFilenameET.getText().toString();
    }

    /**
     * set the download file size for user to be known
     * 
     */
    private void setDownloadFileSizeText() {
        String sizeText;
        if (contentLength <= 0) {
            sizeText = getString(R.string.unkown_length);
        } else {
            sizeText = getDownloadFileSize();
        }
        downloadEstimateSize.setText(sizeText);

    }    

    /**
     * set the time which downloaded this file will be estimately
     * use; 
     */
    private void setDownloadFileTimeText() {
        String neededTimeText;
        if (contentLength <= 0) {
            neededTimeText = getString(R.string.unkown_length);
        } else {
            neededTimeText = getNeededTime() + getString(R.string.time_min);
        }
        downloadEstimateTime.setText(neededTimeText);
    }

    /**
     * count the download file's size and format the values
     * 
     * 
     * @return String the format values
     */
    private String getDownloadFileSize() {
        String currentSizeText = "";
        if (contentLength >0) {
            currentSizeText = Formatter.formatFileSize(DownloadSettings.this, contentLength);
        }
        Log.e(LOGTAG, "fileSize:" + currentSizeText);
        return currentSizeText;
    }

    /**
     * get the time download this file will be use,and format this 
     * time values 
     * 
     * 
     * @return long the valses of time which download this file will 
     *         be use
     */
    private long getNeededTime() {
        long timeNeeded = contentLength/downloadRate;
        if (timeNeeded < 1) {
            timeNeeded = 1;
        }
        Log.e(LOGTAG, "TimeNeeded:" + timeNeeded  + "min");
        //return the time like 5 min, not 5 s;
        return timeNeeded;
    }
}
