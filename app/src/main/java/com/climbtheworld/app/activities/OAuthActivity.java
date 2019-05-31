package com.climbtheworld.app.activities;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.climbtheworld.app.R;
import com.climbtheworld.app.oauth.OAuthHelper;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.dialogs.DialogBuilder;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import oauth.signpost.exception.OAuthException;

public class OAuthActivity extends AppCompatActivity {

    private WebView oAuthWebView;
    private RelativeLayout webView;
    private Dialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oauth);

        loadingDialog = DialogBuilder.buildLoadDialog(this, getResources().getString(R.string.loading_message), null);

        this.webView = findViewById(R.id.webView);

        webView.post(new Runnable() {
            public void run() {
                oAuthHandshake();
            }
        });
    }

    public void oAuthHandshake() {
        OAuthHelper oAuth;
        try {
            oAuth = OAuthHelper.getInstance();
        } catch (OAuthException oe) {
            OAuthHelper.resetOauth();
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
            DialogBuilder.showErrorDialog(this, getString(R.string.exception_message, errorMessage), new DialogInterface.OnClickListener() {
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
            private Runnable dismiss = new Runnable() {
                @Override
                public void run() {
                    loadingDialog.dismiss();
                }
            };

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                String lowercaseURL = url.toLowerCase();

//                if (lowercaseURL.contains("google.com")) {
//                    DialogBuilder.showErrorDialog(OAuthActivity.this, "Google authentication not supported", null);
//                } else
                if (!lowercaseURL.contains(OAuthHelper.OAUTH_PATH.toLowerCase())) {
                    view.loadUrl(url);
                    return false;
                } else {
                    boolean returnValue = true;
                    Uri uri = Uri.parse(url);
                    Globals.globalConfigs.setString(Configs.ConfigKey.oauthToken, uri.getQueryParameter("oauth_token"));
                    Globals.globalConfigs.setString(Configs.ConfigKey.oauthVerifier, uri.getQueryParameter("oauth_verifier"));

                    try {
                        oAuthTokenHandshake(Globals.globalConfigs.getString(Configs.ConfigKey.oauthVerifier));
                    } catch (oauth.signpost.exception.OAuthException | ExecutionException | TimeoutException e) {
                        returnValue = false;
                        OAuthHelper.resetOauth();

                        Toast.makeText(OAuthActivity.this, e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                    loadingDialog.dismiss();
                    finishOAuth();
                    return returnValue;
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                synchronized (progressLock) {
                    if (!loadingDialog.isShowing()) {
                        loadingDialog.show();
                    }
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                synchronized (progressLock) {
                    if (loadingDialog.isShowing() && oAuthWebView != null) {
                        oAuthWebView.removeCallbacks(dismiss);
                        oAuthWebView.postDelayed(dismiss, 500);
                    }
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                DialogBuilder.showErrorDialog(OAuthActivity.this, description, new DialogInterface.OnClickListener() {
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

    private void oAuthTokenHandshake(String verifier) throws oauth.signpost.exception.OAuthException, TimeoutException, ExecutionException {
        String[] s = { verifier };
        class OAuthAccessTokenTask extends AsyncTask<String, Void, Boolean> {
            private oauth.signpost.exception.OAuthException ex = null;

            @Override
            protected Boolean doInBackground(String... s) {
                try {
                    OAuthHelper oa = OAuthHelper.getInstance(); // if we got here it has already been initialized once
                    String access[] = oa.getAccessToken(s[0]);
                    Globals.globalConfigs.setString(Configs.ConfigKey.oauthToken, access[0]);
                    Globals.globalConfigs.setString(Configs.ConfigKey.oauthVerifier, access[1]);
                } catch (oauth.signpost.exception.OAuthException e) {
                    ex = e;
                    return false;
                }
                return true;
            }

            @Override
            protected void onPostExecute(Boolean success) {
            }

            /**
             * Get the any OAuthException that was thrown
             *
             * @return the exception
             */
            oauth.signpost.exception.OAuthException getException() {
                return ex;
            }
        }

        OAuthAccessTokenTask requester = new OAuthAccessTokenTask();
        requester.execute(s);
        try {
            if (!requester.get(60, TimeUnit.SECONDS)) {
                oauth.signpost.exception.OAuthException ex = requester.getException();
                if (ex != null) {
                    throw ex;
                }
            }
        } catch (InterruptedException e) { // NOSONAR cancel does interrupt the thread in question
            requester.cancel(true);
            throw new TimeoutException(e.getMessage());
        }
    }
}
