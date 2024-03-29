package com.climbtheworld.app.activities;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class LicenseActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_license);

		InputStream is = getResources().openRawResource(R.raw.licenses);

		if (is != null) {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
				String line = "";
				StringBuilder responseStrBuilder = new StringBuilder();
				while ((line = reader.readLine()) != null) {
					responseStrBuilder.append(line).append("\n");
				}

				((TextView) findViewById(R.id.licenseView)).setText(responseStrBuilder);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
