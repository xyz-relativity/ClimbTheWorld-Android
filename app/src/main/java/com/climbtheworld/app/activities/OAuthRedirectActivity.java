package com.climbtheworld.app.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Takes OpenStreetMap's login answer at climbtheworld://oauth from the browser and passes it to
 * the OAuthActivity that opened the login, closing the browser tab above it.
 */
public class OAuthRedirectActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent login = new Intent(this, OAuthActivity.class);
		login.setData(getIntent().getData());
		login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		startActivity(login);
		finish();
	}
}
