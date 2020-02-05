package com.climbtheworld.app.tutorial;

import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.views.RemotePagerFragment;

public class DownloadRegionFragment extends TutorialFragment {

    public DownloadRegionFragment(AppCompatActivity parent, int viewID) {
        super(parent, viewID);
    }

    @Override
    public void onCreate(ViewGroup view) {
        ((TextView)view.findViewById(R.id.fragmentText))
                .setText(Html.fromHtml(parent.getResources().getString(R.string.tutorial_region_download_message)));
        ((TextView)view.findViewById(R.id.fragmentText)).setMovementMethod(LinkMovementMethod.getInstance());

        RemotePagerFragment downloadView = new RemotePagerFragment(parent, R.layout.fragment_data_manager_remote_data);
        downloadView.onCreate(view);
    }
}
