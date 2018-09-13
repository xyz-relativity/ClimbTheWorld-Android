package com.climbtheworld.app.tutorial;

import android.app.Activity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.ViewGroup;
import android.widget.TextView;

import com.climbtheworld.app.R;

public class WelcomeFragment extends TutorialFragment {

    public WelcomeFragment(Activity parent, int viewID) {
        super(parent, viewID);
    }

    @Override
    public void onCreate(ViewGroup view) {
        ((TextView)view.findViewById(R.id.titleText))
                .setText(Html.fromHtml(parent.getResources().getString(R.string.tutorial_welcome_title, parent.getResources().getString(R.string.app_name))));

        ((TextView)view.findViewById(R.id.fragmentText))
                .setText(Html.fromHtml(parent.getResources().getString(R.string.tutorial_welcome_message, parent.getResources().getString(R.string.app_name))));
        ((TextView)view.findViewById(R.id.fragmentText)).setMovementMethod(LinkMovementMethod.getInstance());
    }
}
