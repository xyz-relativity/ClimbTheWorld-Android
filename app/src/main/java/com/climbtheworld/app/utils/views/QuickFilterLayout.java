package com.climbtheworld.app.utils.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.climbtheworld.app.R;

/**
 * The row of a quick filter or search field, with a button that clears the field, shown while
 * the field has text. It wires itself up when inflated, so every layout including the row gets
 * the button; clearing goes through the field's own text listeners like typing does.
 */
public class QuickFilterLayout extends LinearLayout {

	public QuickFilterLayout(Context context) {
		super(context);
	}

	public QuickFilterLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public QuickFilterLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		EditText text = findViewById(R.id.editFind);
		View clear = findViewById(R.id.buttonClearFilter);
		clear.setOnClickListener(view -> text.setText(""));
		text.addTextChangedListener(TextWatchers.afterTextChanged(s -> clear.setVisibility(s.length() > 0 ? VISIBLE : GONE)));
	}
}
