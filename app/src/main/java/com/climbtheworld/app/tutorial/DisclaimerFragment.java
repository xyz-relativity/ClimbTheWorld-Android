package com.climbtheworld.app.tutorial;

import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;

public class DisclaimerFragment extends TutorialFragment {

	public DisclaimerFragment(AppCompatActivity parent, int viewID) {
		super(parent, viewID);
	}

	@Override
	public void onCreate(ViewGroup view) {
		super.onCreate(view);
		setMessage(view, getString(R.string.tutorial_disclaimer_message));
	}
}
