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

import com.android.browser.autocomplete.SuggestedTextController.TextChangeWatcher;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AbsoluteLayout;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import java.util.List;

/**
 * tabbed title bar for xlarge screen browser
 */
public class TitleBarXLarge extends TitleBarBase
        implements OnClickListener, OnFocusChangeListener, TextChangeWatcher,
        DeviceAccountLogin.AutoLoginCallback {

    private XLargeUi mUi;

    private Drawable mStopDrawable;
    private Drawable mReloadDrawable;

    private View mUrlContainer;
    private ImageButton mBackButton;
    private ImageButton mForwardButton;
    private ImageView mStar;
    private ImageView mUrlIcon;
    private ImageView mSearchButton;
    private View mContainer;
    private View mGoButton;
    private ImageView mStopButton;
    private View mAllButton;
    private View mClearButton;
    private ImageView mVoiceSearch;
    private PageProgressView mProgressView;
    private Drawable mFocusDrawable;
    private Drawable mUnfocusDrawable;

    private boolean mInLoad;

    public TitleBarXLarge(Activity activity, UiController controller,
            XLargeUi ui) {
        super(activity, controller, ui);
        mUi = ui;
        Resources resources = activity.getResources();
        mStopDrawable = resources.getDrawable(R.drawable.ic_stop_holo_dark);
        mReloadDrawable = resources.getDrawable(R.drawable.ic_refresh_holo_dark);
        mFocusDrawable = resources.getDrawable(
                R.drawable.textfield_active_holo_dark);
        mUnfocusDrawable = resources.getDrawable(
                R.drawable.textfield_default_holo_dark);
        mInVoiceMode = false;
        initLayout(activity, R.layout.url_bar);
    }

    @Override
    void setTitleGravity(int gravity) {
        if (mUseQuickControls) {
            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) getLayoutParams();
            lp.gravity = gravity;
            setLayoutParams(lp);
        } else {
            super.setTitleGravity(gravity);
        }
    }

    @Override
    protected void initLayout(Context context, int layoutId) {
        super.initLayout(context, layoutId);

        mContainer = findViewById(R.id.taburlbar);
        mAllButton = findViewById(R.id.all_btn);
        // TODO: Change enabled states based on whether you can go
        // back/forward.  Probably should be done inside onPageStarted.
        mBackButton = (ImageButton) findViewById(R.id.back);
        mForwardButton = (ImageButton) findViewById(R.id.forward);
        mUrlIcon = (ImageView) findViewById(R.id.url_icon);
        mStar = (ImageView) findViewById(R.id.star);
        mStopButton = (ImageView) findViewById(R.id.stop);
        mSearchButton = (ImageView) findViewById(R.id.search);
        mLockIcon = (ImageView) findViewById(R.id.lock);
        mGoButton = findViewById(R.id.go);
        mClearButton = findViewById(R.id.clear);
        mVoiceSearch = (ImageView) findViewById(R.id.voicesearch);
        mProgressView = (PageProgressView) findViewById(R.id.progress);
        mUrlContainer = findViewById(R.id.urlbar_focused);
        mBackButton.setOnClickListener(this);
        mForwardButton.setOnClickListener(this);
        mStar.setOnClickListener(this);
        mAllButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
        mSearchButton.setOnClickListener(this);
        mGoButton.setOnClickListener(this);
        mClearButton.setOnClickListener(this);
        mVoiceSearch.setOnClickListener(this);
        mUrlInput.setContainer(mUrlContainer);
        setFocusState(false);
    }

    void updateNavigationState(Tab tab) {
        WebView web = tab.getWebView();
        if (web != null) {
            mBackButton.setImageResource(web.canGoBack()
                    ? R.drawable.ic_back_holo_dark
                    : R.drawable.ic_back_disabled_holo_dark);
            mForwardButton.setImageResource(web.canGoForward()
                    ? R.drawable.ic_forward_holo_dark
                    : R.drawable.ic_forward_disabled_holo_dark);
        }
    }

    private ViewGroup.LayoutParams makeLayoutParams() {
        if (mUseQuickControls) {
            return new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT);
        } else {
            return new AbsoluteLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                    0, 0);
        }
    }

    @Override
    public int getEmbeddedHeight() {
        int height = mContainer.getHeight();
        if (mAutoLogin.getVisibility() == View.VISIBLE) {
            height += mAutoLogin.getHeight();
        }
        return height;
    }

    @Override
    protected void setUseQuickControls(boolean useQuickControls) {
        mUseQuickControls = useQuickControls;
        setLayoutParams(makeLayoutParams());
    }

    void setShowProgressOnly(boolean progress) {
        if (progress && !inAutoLogin()) {
            mContainer.setVisibility(View.GONE);
        } else {
            mContainer.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        // if losing focus and not in touch mode, leave as is
        if (hasFocus || view.isInTouchMode() || mUrlInput.needsUpdate()) {
            setFocusState(hasFocus);
            mUrlContainer.setBackgroundDrawable(hasFocus
                    ? mFocusDrawable : mUnfocusDrawable);
        }
        if (hasFocus) {
            mUrlInput.forceIme();
            if (mInVoiceMode) {
                mUrlInput.forceFilter();
            }
        } else if (!mUrlInput.needsUpdate()) {
            mUrlInput.dismissDropDown();
            mUrlInput.hideIME();
            if (mUseQuickControls) {
                mUi.hideTitleBar();
            }

            if (mUrlInput.getText().length() == 0) {
                Tab currentTab = mUiController.getTabControl().getCurrentTab();
                if (currentTab != null) {
                    mUrlInput.setText(currentTab.getUrl(), false);
                }
            }
        }
        mUrlInput.clearNeedsUpdate();
    }

    @Override
    public void setCurrentUrlIsBookmark(boolean isBookmark) {
        mStar.setActivated(isBookmark);
    }

    /**
     * called from the Ui when the user wants to edit
     * @param clearInput clear the input field
     */
    @Override
    void startEditingUrl(boolean clearInput) {
        // editing takes preference of progress
        mContainer.setVisibility(View.VISIBLE);
        if (mUseQuickControls) {
            mProgressView.setVisibility(View.GONE);
        }
        if (!mUrlInput.hasFocus()) {
            mUrlInput.requestFocus();
        }
        if (clearInput) {
            mUrlInput.setText("");
        } else if (mInVoiceMode) {
            mUrlInput.showDropDown();
        }
    }

    @Override
    protected void showAutoLogin(boolean animate) {
        if (mUseQuickControls) {
            mUi.showTitleBar();
        }
        super.showAutoLogin(animate);
    }

    @Override
    protected void hideAutoLogin(boolean animate) {
        mAutoLoginHandler = null;
        if (mUseQuickControls) {
            mUi.hideTitleBar();
            mAutoLogin.setVisibility(View.GONE);
            mUi.refreshWebView();
        } else {
            super.hideAutoLogin(animate);
        }
    }

    @Override
    public void onClick(View v) {
        if (mBackButton == v) {
            mUiController.getCurrentTopWebView().goBack();
        } else if (mForwardButton == v) {
            mUiController.getCurrentTopWebView().goForward();
        } else if (mStar == v) {
            mUiController.bookmarkCurrentPage(
                    AddBookmarkPage.DEFAULT_FOLDER_ID, true);
        } else if (mAllButton == v) {
            mUiController.bookmarksOrHistoryPicker(false);
        } else if (mSearchButton == v) {
            mUi.editUrl(true);
        } else if (mStopButton == v) {
            stopOrRefresh();
        } else if (mGoButton == v) {
            if (!TextUtils.isEmpty(mUrlInput.getText())) {
                onAction(mUrlInput.getText().toString(), null,
                        UrlInputView.TYPED);
            }
        } else if (mClearButton == v) {
            clearOrClose();
        } else if (mVoiceSearch == v) {
            mUiController.startVoiceSearch();
        } else {
            super.onClick(v);
        }
    }

    @Override
    void setFavicon(Bitmap icon) { }

    private void clearOrClose() {
        if (TextUtils.isEmpty(mUrlInput.getUserText())) {
            // close
            mUrlInput.clearFocus();
        } else {
            // clear
            mUrlInput.setText("");
        }
    }

    @Override
    protected void setFocusState(boolean focus) {
        super.setFocusState(focus);
        if (focus) {
            mSearchButton.setVisibility(View.GONE);
            mStar.setVisibility(View.GONE);
            mClearButton.setVisibility(View.VISIBLE);
            mUrlIcon.setImageResource(R.drawable.ic_search_holo_dark);
            updateSearchMode(false);
        } else {
            mGoButton.setVisibility(View.GONE);
            mVoiceSearch.setVisibility(View.GONE);
            mStar.setVisibility(View.VISIBLE);
            mClearButton.setVisibility(View.GONE);
            if (mUseQuickControls) {
                mSearchButton.setVisibility(View.GONE);
            } else {
                mSearchButton.setVisibility(View.VISIBLE);
            }
            mUrlIcon.setImageResource(mInVoiceMode ?
                    R.drawable.ic_search_holo_dark
                    : R.drawable.ic_web_holo_dark);
        }
    }

    private void stopOrRefresh() {
        if (mInLoad) {
            mUiController.stopLoading();
        } else {
            mUiController.getCurrentTopWebView().reload();
        }
    }

    /**
     * Update the progress, from 0 to 100.
     */
    @Override
    void setProgress(int newProgress) {
        boolean blockvisuals = mUseQuickControls && isEditingUrl();
        if (newProgress >= PROGRESS_MAX) {
            if (!blockvisuals) {
                mProgressView.setProgress(PageProgressView.MAX_PROGRESS);
                mProgressView.setVisibility(View.GONE);
                mStopButton.setImageDrawable(mReloadDrawable);
            }
            mInLoad = false;
        } else {
            if (!mInLoad) {
                if (!blockvisuals) {
                    mProgressView.setVisibility(View.VISIBLE);
                    mStopButton.setImageDrawable(mStopDrawable);
                }
                mInLoad = true;
            }
            mProgressView.setProgress(newProgress * PageProgressView.MAX_PROGRESS
                    / PROGRESS_MAX);
        }
    }

    @Override
    protected void updateSearchMode(boolean userEdited) {
        setSearchMode(!userEdited || TextUtils.isEmpty(mUrlInput.getUserText()));
    }

    @Override
    protected void setSearchMode(boolean voiceSearchEnabled) {
        boolean showvoicebutton = voiceSearchEnabled &&
                mUiController.supportsVoiceSearch();
        mVoiceSearch.setVisibility(showvoicebutton ? View.VISIBLE :
                View.GONE);
        mGoButton.setVisibility(voiceSearchEnabled ? View.GONE :
                View.VISIBLE);
    }

    @Override
    public void setInVoiceMode(boolean voicemode, List<String> voiceResults) {
        super.setInVoiceMode(voicemode, voiceResults);
        if (voicemode) {
            mUrlIcon.setImageDrawable(mSearchButton.getDrawable());
        }
    }

    @Override
    public View focusSearch(View focused, int dir) {
        if (FOCUS_DOWN == dir && hasFocus()) {
            return getCurrentWebView();
        }
        return super.focusSearch(focused, dir);
    }

}