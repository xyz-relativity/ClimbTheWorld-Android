package com.climbtheworld.app.tutorial;

import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;

public class WelcomeFragment extends TutorialFragment {

	public WelcomeFragment(AppCompatActivity parent, int viewID) {
		super(parent, viewID);
	}

	@Override
	public void onCreate(ViewGroup view) {
		super.onCreate(view);
		String appName = getString(R.string.app_name);
		((TextView) view.findViewById(R.id.titleText))
				.setText(getString(R.string.tutorial_welcome_title, appName));
		setMessage(view, getString(R.string.tutorial_welcome_message, appName, appName));
	}
}
