package com.climbtheworld.app.tutorial;

import android.content.Context;
import android.content.Intent;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.SupportMeActivity;

public class SupportUsFragment extends TutorialFragment {

	public SupportUsFragment(AppCompatActivity parent, int viewID) {
		super(parent, viewID);
	}

	@Override
	public void onCreate(ViewGroup view) {
		InputMethodManager imm = (InputMethodManager) parent.get().getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

		((TextView) view.findViewById(R.id.fragmentText))
				.setText(Html.fromHtml(parent.get().getResources().getString(R.string.tutorial_contribute_message, parent.get().getResources().getString(R.string.app_name))));
		((TextView) view.findViewById(R.id.fragmentText)).setMovementMethod(LinkMovementMethod.getInstance());

		(view.findViewById(R.id.ButtonDonate)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(parent.get(), SupportMeActivity.class);
				parent.get().startActivity(intent);
			}
		});
	}
}
