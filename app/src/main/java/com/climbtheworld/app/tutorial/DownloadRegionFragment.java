package com.climbtheworld.app.tutorial;

import android.view.ViewGroup;

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
		setMessage(view, getString(R.string.tutorial_region_download_message));

		downloadView = new RemotePagerFragment(parent.get(), R.layout.fragment_data_manager_remote_data, DataFragment.initCountryMap(parent.get()));
		downloadView.onCreate(view);
	}

	@Override
	public void onDestroy(ViewGroup view) {
		downloadView.onDestroy(view);
	}
}
