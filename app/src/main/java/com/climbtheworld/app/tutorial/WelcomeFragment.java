package com.climbtheworld.app.tutorial;

import android.text.Html;
import android.text.method.LinkMovementMethod;
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
        String appName = parent.getResources().getString(R.string.app_name);
        ((TextView)view.findViewById(R.id.titleText))
                .setText(parent.getResources().getString(R.string.tutorial_welcome_title, appName));

        ((TextView)view.findViewById(R.id.fragmentText))
                .setText(Html.fromHtml(parent.getResources().getString(R.string.tutorial_welcome_message, appName, appName)));
        ((TextView)view.findViewById(R.id.fragmentText)).setMovementMethod(LinkMovementMethod.getInstance());
    }
}
