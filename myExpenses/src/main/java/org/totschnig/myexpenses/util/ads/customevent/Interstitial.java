/*
 * Copyright (C) 2014 Google, Inc.
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

package org.totschnig.myexpenses.util.ads.customevent;

import android.content.Context;
import android.support.v4.util.Pair;

/**
 * A sample interstitial ad. This is an example of an interstitial class that most ad networks SDKs
 * have.
 */
public class Interstitial {
  private AdListener listener;
  private WebViewHelper webViewHelper;
  private Pair<PartnerProgram, String> contentProvider;

  /**
   * Create a new {@link Interstitial}.
   *
   * @param context An Android {@link Context}.
   */
  public Interstitial(Context context) {
    webViewHelper = new WebViewHelper(context);
  }

  /**
   * Sets a {@link AdListener} to listen for ad events.
   *
   * @param listener The ad listener.
   */
  public void setAdListener(AdListener listener) {
    this.listener = listener;
  }

  /**
   * Fetch an ad. Instead of doing an actual ad fetch, we will randomly decide to succeed, or
   * fail with different error codes.
   *
   */
  public void fetchAd() {
    if (listener == null) {
      return;
    }
    // If the publisher didn't set an ad unit, return a bad request.
    if (contentProvider == null) {
      listener.onAdFetchFailed(ErrorCode.BAD_REQUEST);
    }
    listener.onAdFetchSucceeded();
  }

  /**
   * Shows the interstitial.
   */
  public void show() {
    // Notify the developer that a full screen view will be presented.
    listener.onAdFullScreen();
    webViewHelper.openWebViewInOverlay(contentProvider.second);
  }

  /**
   * Destroy the interstitial.
   */
  public void destroy() {
    listener = null;
    webViewHelper = null;
  }

  public void setContentProvider(Pair<PartnerProgram, String> contentProvider) {
    this.contentProvider = contentProvider;
  }
}

