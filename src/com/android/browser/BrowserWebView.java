/*
 * Copyright (c) 2012, The Linux Foundation. All rights reserved.
 * Not a Contribution, Apache license notifications and license are retained
 * for attribution purposes only.
 *
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.browser;

import android.content.res.Resources;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClassic;

import java.util.Map;

/**
 * Manage WebView scroll events
 */
public class BrowserWebView extends WebView implements WebViewClassic.TitleBarDelegate {

    public interface OnScrollChangedListener {
        void onScrollChanged(int l, int t, int oldl, int oldt);
    }

    private boolean mBackgroundRemoved = false;
    private TitleBar mTitleBar;
    private OnScrollChangedListener mOnScrollChangedListener;

    // How close to the horizontal edge in pixels before a swipe gesture is registered.
    private int mSlop;

    /**
     * @param context
     * @param attrs
     * @param defStyle
     * @param javascriptInterfaces
     */
    public BrowserWebView(Context context, AttributeSet attrs, int defStyle,
            Map<String, Object> javascriptInterfaces, boolean privateBrowsing) {
        super(context, attrs, defStyle, javascriptInterfaces, privateBrowsing);
        Resources res = context.getResources();
        mSlop = (int) res.getDimension(R.dimen.qc_slop);
    }

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public BrowserWebView(
            Context context, AttributeSet attrs, int defStyle, boolean privateBrowsing) {
        super(context, attrs, defStyle, privateBrowsing);
        Resources res = context.getResources();
        mSlop = (int) res.getDimension(R.dimen.qc_slop);
    }

    /**
     * @param context
     * @param attrs
     */
    public BrowserWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources res = context.getResources();
        mSlop = (int) res.getDimension(R.dimen.qc_slop);
    }

    /**
     * @param context
     */
    public BrowserWebView(Context context) {
        super(context);
        Resources res = context.getResources();
        mSlop = (int) res.getDimension(R.dimen.qc_slop);
    }

    public void setTitleBar(TitleBar title) {
        mTitleBar = title;
    }

    // From TitleBarDelegate
    @Override
    public int getTitleHeight() {
        return (mTitleBar != null) ? mTitleBar.getEmbeddedHeight() : 0;
    }

    // From TitleBarDelegate
    @Override
    public void onSetEmbeddedTitleBar(final View title) {
    }

    public boolean hasTitleBar() {
        return (mTitleBar != null);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (!mBackgroundRemoved && getRootView().getBackground() != null) {
            mBackgroundRemoved = true;
            post(new Runnable() {
                public void run() {
                    getRootView().setBackgroundDrawable(null);
                }
            });
        }
    }

    public void drawContent(Canvas c) {
        onDraw(c);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (mTitleBar != null) {
            mTitleBar.onScrollChanged();
        }
        if (mOnScrollChangedListener != null) {
            mOnScrollChangedListener.onScrollChanged(l, t, oldl, oldt);
        }
    }

    public void setOnScrollChangedListener(OnScrollChangedListener listener) {
        mOnScrollChangedListener = listener;
    }

    @Override
    public boolean showContextMenuForChild(View originalView) {
        return false;
    }

    @Override
    public void destroy() {
        BrowserSettings.getInstance().stopManagingSettings(getSettings());
        super.destroy();
    }

    /* Detect a swipe from the edge. */
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!BrowserSettings.getInstance().useSlideTabTransitions())
            return super.onTouchEvent(e);

        float x = e.getX();
        int action = e.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            // Let the RVS handle the swipe gesture.
            if ((x > getWidth() - mSlop) || (x < mSlop))
                return false;
        }
        return super.onTouchEvent(e);
    }
}
