package com.climbtheworld.app.tutorial;

import android.content.Intent;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.SupportMeActivity;

public class SupportUsFragment extends TutorialFragment {

	public SupportUsFragment(AppCompatActivity parent, int viewID) {
		super(parent, viewID);
	}

	@Override
	public void onCreate(ViewGroup view) {
		super.onCreate(view);
		setMessage(view, getString(R.string.tutorial_contribute_message, getString(R.string.app_name)));

		view.findViewById(R.id.ButtonDonate).setOnClickListener(v ->
				parent.get().startActivity(new Intent(parent.get(), SupportMeActivity.class)));
	}
}
