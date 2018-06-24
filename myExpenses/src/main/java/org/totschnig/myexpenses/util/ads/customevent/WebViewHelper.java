package org.totschnig.myexpenses.util.ads.customevent;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;

import org.totschnig.myexpenses.R;

public class WebViewHelper implements View.OnClickListener {
  private WebView mWebView;
  private Context mContext;
  private Dialog mWebviewDialog = null;

  /**
   * Initializes helper class
   *
   * @param context Activity context.
   */
  public WebViewHelper(Context context) {
    mContext = context;
  }

  /***
   * Opens url in a webview in a full-screen overlay mode.
   * Overlay can be closed by clicking on "x" button in top right corner.
   * @param content html to load
   */
  public void openWebViewInOverlay(String content) {
    mWebviewDialog = new Dialog(mContext, android.R.style.Theme_Holo_Light_NoActionBar_Fullscreen);

    View view = View.inflate(mContext, R.layout.webview_modal, null);
    Button closeSurvey = view.findViewById(R.id.closeOverlay);
    closeSurvey.setOnClickListener(this);

    mWebView = view.findViewById(R.id.webviewoverlay);
    mWebView.setBackgroundColor(Color.TRANSPARENT);
    mWebView.loadData(String.format("<center>%s</center>", content), "text/html", "utf-8");

    mWebviewDialog.setContentView(view);
    mWebviewDialog.show();

    //set full-screen params
    /*WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
    lp.copyFrom(mWebviewDialog.getWindow().getAttributes());
    lp.width = WindowManager.LayoutParams.FILL_PARENT;
    lp.height = WindowManager.LayoutParams.FILL_PARENT;
    mWebviewDialog.getWindow().setAttributes(lp);*/
  }

  @Override
  public void onClick(View v) {
    if (mWebviewDialog != null) {
      mWebviewDialog.dismiss();
    }
  }
}