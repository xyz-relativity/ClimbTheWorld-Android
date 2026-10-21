package com.climbtheworld.app.tutorial;

import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.views.IPagerViewFragment;

import java.lang.ref.WeakReference;

/** A page of the tutorial shown on the first run. */
public abstract class TutorialFragment implements IPagerViewFragment {
	WeakReference<AppCompatActivity> parent;
	@LayoutRes
	private final int viewID;

	TutorialFragment(AppCompatActivity parent, @LayoutRes int viewID) {
		this.parent = new WeakReference<>(parent);
		this.viewID = viewID;
	}

	@Override
	public @LayoutRes
	int getViewId() {
		return this.viewID;
	}

	/** Puts the keyboard away, which a page with a text field leaves open. */
	@Override
	public void onCreate(final ViewGroup view) {
		InputMethodManager imm = (InputMethodManager) parent.get().getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
	}

	@Override
	public void onDestroy(final ViewGroup view) {

	}

	@Override
	public void onViewSelected() {

	}

	/** Shows the page's message, with its links tappable. */
	void setMessage(ViewGroup view, String html) {
		TextView text = view.findViewById(R.id.fragmentText);
		text.setText(Html.fromHtml(html));
		text.setMovementMethod(LinkMovementMethod.getInstance());
	}

	String getString(@StringRes int id, Object... formatArgs) {
		return parent.get().getResources().getString(id, formatArgs);
	}
}
