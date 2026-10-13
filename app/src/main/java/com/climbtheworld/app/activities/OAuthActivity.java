package com.climbtheworld.app.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.oauth.OAuthHelper;
import com.climbtheworld.app.oauth.OsmOAuth2;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;

import org.json.JSONException;

import java.io.IOException;
import java.security.SecureRandom;

import needle.UiRelatedTask;

/**
 * Logs in to OpenStreetMap with OAuth 2. OSM's login and consent page opens in a browser tab,
 * where the user's own sign-in options work; OAuthRedirectActivity brings OSM's answer back
 * here, and its code is traded for the token the uploads use. Finishes with RESULT_OK once the
 * token is stored.
 */
public class OAuthActivity extends AppCompatActivity {
	private static final String STATE_BROWSER_OPENED = "browserOpened";

	private Configs configs;
	private boolean browserOpened;
	private boolean leftForBrowser;
	private boolean finishingLogin;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_oauth);
		configs = Configs.instance(this);
		if (savedInstanceState != null) {
			browserOpened = savedInstanceState.getBoolean(STATE_BROWSER_OPENED);
			leftForBrowser = browserOpened;
		}

		Uri answer = getIntent().getData();
		if (answer != null) {
			// The app was closed while the browser was open; the answer starts it again.
			finishLogin(answer);
		} else if (!browserOpened) {
			openLogin();
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		if (intent.getData() != null) {
			finishLogin(intent.getData());
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (browserOpened) {
			leftForBrowser = true;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (leftForBrowser && !finishingLogin) {
			// Back from the browser without an answer: the user closed it.
			setResult(RESULT_CANCELED);
			finish();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(STATE_BROWSER_OPENED, browserOpened);
	}

	private void openLogin() {
		String clientId = OAuthHelper.clientId(this);
		if (clientId.isEmpty()) {
			DialogBuilder.showErrorDialog(this, getString(R.string.oauth_no_client_id),
					(dialog, which) -> finish());
			return;
		}
		// Kept in the settings, as the app may be closed while the browser is open.
		SecureRandom random = new SecureRandom();
		String verifier = OsmOAuth2.newSecret(random);
		String state = OsmOAuth2.newSecret(random);
		configs.setString(Configs.ConfigKey.oauth2Verifier, verifier);
		configs.setString(Configs.ConfigKey.oauth2State, state);

		try {
			new CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(
					OsmOAuth2.authorizationUrl(OAuthHelper.API.oAuthUrl, clientId, verifier, state)));
			browserOpened = true;
		} catch (ActivityNotFoundException e) {
			DialogBuilder.showErrorDialog(this, getString(R.string.oauth_failed),
					(dialog, which) -> finish());
		}
	}

	private void finishLogin(Uri answer) {
		finishingLogin = true;
		String code = answer.getQueryParameter("code");
		String verifier = configs.getString(Configs.ConfigKey.oauth2Verifier);
		String expectedState = configs.getString(Configs.ConfigKey.oauth2State);
		configs.setString(Configs.ConfigKey.oauth2Verifier, null);
		configs.setString(Configs.ConfigKey.oauth2State, null);
		// The state shows the answer is for the login this app started.
		if (code == null || verifier == null || expectedState == null
				|| !expectedState.equals(answer.getQueryParameter("state"))) {
			failLogin(answer.getQueryParameter("error_description"));
			return;
		}

		Constants.WEB_EXECUTOR.execute(new UiRelatedTask<String>() {
			private String error;

			@Override
			protected String doWork() {
				try {
					return OAuthHelper.exchangeCode(OAuthActivity.this, code, verifier);
				} catch (IOException | JSONException e) {
					error = e.getMessage();
					return null;
				}
			}

			@Override
			protected void thenDoUiRelatedWork(String token) {
				if (token == null) {
					failLogin(error);
					return;
				}
				OAuthHelper.saveToken(configs, token);
				setResult(RESULT_OK);
				finish();
			}
		});
	}

	private void failLogin(String reason) {
		String message = getString(R.string.oauth_failed);
		if (reason != null && !reason.isEmpty()) {
			message += "<br>" + reason;
		}
		DialogBuilder.showErrorDialog(this, message, (dialog, which) -> {
			setResult(RESULT_CANCELED);
			finish();
		});
	}
}
