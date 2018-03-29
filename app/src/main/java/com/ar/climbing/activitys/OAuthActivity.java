package com.ar.climbing.activitys;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RelativeLayout;

import com.ar.climbing.R;
import com.ar.climbing.oauth.OAuthHelper;
import com.ar.climbing.oauth.OAuthException;
import com.ar.climbing.utils.Constants;
import com.ar.climbing.utils.Globals;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class OAuthActivity extends AppCompatActivity {

    private WebView oAuthWebView;
    private RelativeLayout webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oauth);

        this.webView = findViewById(R.id.webView);

        webView.post(new Runnable() {
            public void run() {
                oAuthHandshake();
            }
        });
    }

    public void oAuthHandshake() {

        String url = getBaseUrl(Constants.DEFAULT_API);
        OAuthHelper oAuth;
        try {
            oAuth = new OAuthHelper(url);
        } catch (OAuthException oe) {
            Globals.oauthToken = null;
            Globals.oauthSecret = null;
            return;
        }

        String authUrl = null;
        String errorMessage = null;
        try {
            authUrl = oAuth.getRequestToken();
        } catch (oauth.signpost.exception.OAuthException | ExecutionException | TimeoutException e) {
            errorMessage = e.getLocalizedMessage();
        }

        if (authUrl == null) {
            Globals.showErrorDialog(this, getString(R.string.exception_message, errorMessage), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            return;
        }

        oAuthWebView = new WebView(this);
        webView.addView(oAuthWebView);
        oAuthWebView.getSettings().setJavaScriptEnabled(true);
        oAuthWebView.getSettings().setAllowContentAccess(true);
        oAuthWebView.getLayoutParams().height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        oAuthWebView.getLayoutParams().width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        oAuthWebView.requestFocus(View.FOCUS_DOWN);
        class OAuthWebViewClient extends WebViewClient {
            private final Object progressLock = new Object();
            private boolean progressShown = false;
            private Runnable dismiss = new Runnable() {
                @Override
                public void run() {
                }
            };

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.contains("google.com")) {
                    Globals.showErrorDialog(OAuthActivity.this, url, null);
                } else if (!url.contains(OAuthHelper.oAuthCallbackPath)) {
                    // load in in this webview
                    view.loadUrl(url);
                } else {
                    Globals.oauthToken = Uri.parse(url).getQueryParameter("oauth_token");
                    Globals.oauthSecret = Uri.parse(url).getQueryParameter("oauth_verifier");

                    finishOAuth();
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                synchronized (progressLock) {
                    if (!progressShown) {
                        progressShown = true;
                    }
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                synchronized (progressLock) {
                    if (progressShown && oAuthWebView != null) {
                        oAuthWebView.removeCallbacks(dismiss);
                        oAuthWebView.postDelayed(dismiss, 500);
                    }
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Globals.showErrorDialog(view.getContext(), description, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finishOAuth();
                    }
                });
            }

            @TargetApi(android.os.Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError rerr) {
                // Redirect to deprecated method, so you can use it in all
                // SDK versions
                onReceivedError(view, rerr.getErrorCode(), rerr.getDescription().toString(), req.getUrl().toString());
            }
        }
        oAuthWebView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (oAuthWebView.canGoBack()) {
                        oAuthWebView.goBack();
                    } else {
                        finishOAuth();
                    }
                    return true;
                }
                return false;
            }
        });
        oAuthWebView.setWebViewClient(new OAuthWebViewClient());
        oAuthWebView.loadUrl(authUrl);
    }

    public static String getBaseUrl(String url) {
        return url.replaceAll("/api/[0-9]+(?:\\.[0-9]+)+/?$", "/");
    }

    private void finishOAuth() {
        if (oAuthWebView != null) {
            webView.removeView(oAuthWebView);
            try {
                // the below loadUrl, even though the "official" way to do
                // it,
                // seems to be prone to crash on some devices.
                oAuthWebView.loadUrl("about:blank"); // workaround clearView
                // issues
                oAuthWebView.setVisibility(View.GONE);
                oAuthWebView.removeAllViews();
                oAuthWebView.destroy();
                oAuthWebView = null;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            finish();
        }
    }
}
