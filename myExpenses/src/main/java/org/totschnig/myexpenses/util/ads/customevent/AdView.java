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
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.android.gms.ads.mediation.customevent.CustomEventBannerListener;

public class AdView extends WebView {
  private CustomEventBannerListener listener;

  /**
   * Create a new {@link AdView}.
   *
   * @param context An Android {@link Context}.
   */
  public AdView(Context context) {
    super(context);
    setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        listener.onAdClicked();
        listener.onAdOpened();
        listener.onAdLeftApplication();
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        context.startActivity(i);
        return true;
      }
    });
  }

  /**
   * Sets a {@link AdListener} to listen for ad events.
   *
   * @param listener The ad listener.
   */
  public void setAdListener(CustomEventBannerListener listener) {
    this.listener = listener;
  }

  /**
   * @param content The add content.
   */
  public void fetchAd(String content) {
    if (listener == null) {
      return;
    }
    this.loadData(String.format("<center>%s</center>", content), "text/html", "utf-8");
    listener.onAdLoaded(this);
  }

  /**
   * Destroy the banner.
   */
  public void destroy() {
    listener = null;
  }
}
