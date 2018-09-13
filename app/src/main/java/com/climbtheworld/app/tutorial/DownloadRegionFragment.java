package com.climbtheworld.app.tutorial;

import android.app.Activity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.views.RemoteDataFragment;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

public class DownloadRegionFragment extends TutorialFragment {

    public DownloadRegionFragment(Activity parent, int viewID) {
        super(parent, viewID);
    }

    @Override
    public void onCreate(ViewGroup view) {
        ((TextView)view.findViewById(R.id.fragmentText))
                .setText(Html.fromHtml(parent.getResources().getString(R.string.tutorial_region_download_message)));
        ((TextView)view.findViewById(R.id.fragmentText)).setMovementMethod(LinkMovementMethod.getInstance());

        RemoteDataFragment downloadView = new RemoteDataFragment(parent, R.layout.fragment_data_manager_remote_data);
        downloadView.onCreate(view);
    }
}
