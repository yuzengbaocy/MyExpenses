package org.totschnig.myexpenses.util.ads.customevent;

import android.view.View;

import com.google.android.gms.ads.mediation.customevent.CustomEventBannerListener;
import com.google.android.gms.ads.mediation.customevent.CustomEventInterstitialListener;
import com.google.android.gms.ads.mediation.customevent.CustomEventListener;

public class CustomEventForwarder extends AdListener {
  private final CustomEventListener listener;

  public CustomEventForwarder(CustomEventListener listener) {
    this.listener = listener;
  }

  @Override
  public void onAdFailedToLoad(int errorCode) {
    listener.onAdFailedToLoad(errorCode);
  }

  @Override
  public void onAdOpened() {
    listener.onAdOpened();
  }

  @Override
  public void onAdClicked() {
    listener.onAdClicked();
  }

  @Override
  public void onAdClosed() {
    listener.onAdClosed();
  }

  @Override
  public void onAdLeftApplication() {
    listener.onAdLeftApplication();
  }

  @Override
  public void onBannerLoaded(View view) {
    if (listener instanceof CustomEventBannerListener) {
      ((CustomEventBannerListener) listener).onAdLoaded(view);
    }
  }

  @Override
  public void onInterstitialLoaded() {
    if (listener instanceof CustomEventInterstitialListener) {
      ((CustomEventInterstitialListener) listener).onAdLoaded();
    }
  }
}
