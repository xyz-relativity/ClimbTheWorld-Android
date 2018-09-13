package com.climbtheworld.app.tutorial;

import android.app.Activity;
import android.content.Intent;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activitys.MainActivity;
import com.climbtheworld.app.activitys.SupportMeActivity;
import com.climbtheworld.app.storage.views.RemoteDataFragment;

public class SupportUsFragment extends TutorialFragment {

    public SupportUsFragment(Activity parent, int viewID) {
        super(parent, viewID);
    }

    @Override
    public void onCreate(ViewGroup view) {
        ((TextView)view.findViewById(R.id.fragmentText))
                .setText(Html.fromHtml(parent.getResources().getString(R.string.tutorial_contribute_message, parent.getResources().getString(R.string.app_name))));
        ((TextView)view.findViewById(R.id.fragmentText)).setMovementMethod(LinkMovementMethod.getInstance());

        (view.findViewById(R.id.ButtonDonate)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(parent, SupportMeActivity.class);
                parent.startActivity(intent);
            }
        });
    }
}
