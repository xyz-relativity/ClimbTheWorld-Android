package com.climbtheworld.app.tutorial;

import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;

public class DisclaimerFragment extends TutorialFragment {

    public DisclaimerFragment(AppCompatActivity parent, int viewID) {
        super(parent, viewID);
    }

    @Override
    public void onCreate(ViewGroup view) {
        InputMethodManager imm = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

        ((TextView)view.findViewById(R.id.fragmentText))
                .setText(Html.fromHtml(parent.getResources().getString(R.string.tutorial_disclaimer_message)));
        ((TextView)view.findViewById(R.id.fragmentText)).setMovementMethod(LinkMovementMethod.getInstance());
    }
}
