package com.climbtheworld.app.tutorial;

import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.views.DataFragment;
import com.climbtheworld.app.storage.views.RemotePagerFragment;

public class DownloadRegionFragment extends TutorialFragment {

	private RemotePagerFragment downloadView;

	public DownloadRegionFragment(AppCompatActivity parent, int viewID) {
		super(parent, viewID);
	}

	@Override
	public void onCreate(ViewGroup view) {
		((TextView) view.findViewById(R.id.fragmentText))
				.setText(Html.fromHtml(parent.get().getResources().getString(R.string.tutorial_region_download_message)));
		((TextView) view.findViewById(R.id.fragmentText)).setMovementMethod(LinkMovementMethod.getInstance());

		downloadView = new RemotePagerFragment(parent.get(), R.layout.fragment_data_manager_remote_data, DataFragment.initCountryMap(parent.get()));
		downloadView.onCreate(view);
	}

	@Override
	public void onDestroy(ViewGroup view) {
		downloadView.onDestroy(view);
	}
}
