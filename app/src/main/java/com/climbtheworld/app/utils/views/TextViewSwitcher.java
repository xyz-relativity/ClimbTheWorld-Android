package com.climbtheworld.app.utils.views;

import android.content.Context;
import android.text.InputFilter;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;

public class TextViewSwitcher {
	private final AppCompatActivity parentActivity;

	public interface ISwitcherCallback {
		void onChange(String value);
	}

	public TextViewSwitcher(AppCompatActivity parentActivity, final LinearLayout container, final String defaultValue, ISwitcherCallback callback) {
		this.parentActivity = parentActivity;
		final TextView switcherText = container.findViewById(R.id.textViewr);
		final EditText switcherEdit = container.findViewById(R.id.textEditor);
		final ImageView switcherEditDone = container.findViewById(R.id.textEditorDone);
		final ViewSwitcher switcher = container.findViewById(R.id.inputSwitcher);

		switcherText.setText(defaultValue);

		switcherEdit.setFilters(new InputFilter[] {new InputFilter.LengthFilter(10)});
		switcherEdit.setText(defaultValue);

		switcherText.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				switcher.showNext();
				switcherEdit.requestFocus();
				switcherEdit.setSelection(switcherEdit.getText().length());
				InputMethodManager imm = (InputMethodManager) parentActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.showSoftInput(switcherEdit, InputMethodManager.SHOW_FORCED);
			}
		});

		switcherEditDone.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				switcherText.setText(switcherEdit.getText());
				callback.onChange(switcherText.getText().toString());
				switcherEdit.clearFocus();
				InputMethodManager imm = (InputMethodManager) parentActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
				switcher.showPrevious();
			}
		});
	}
}
