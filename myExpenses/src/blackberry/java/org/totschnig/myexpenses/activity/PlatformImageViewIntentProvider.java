package org.totschnig.myexpenses.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class PlatformImageViewIntentProvider extends SystemImageViewIntentProvider {
  @Override
  public Intent getViewIntent(Context context, Uri pictureUri) {
    if (pictureUri.getScheme().equals("content")) {
      return new Intent(Intent.ACTION_VIEW, pictureUri, context, SimpleImageActivity.class);
    }
    return super.getViewIntent(context, pictureUri);
  }
}
