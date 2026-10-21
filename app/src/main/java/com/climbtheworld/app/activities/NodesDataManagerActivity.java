package com.climbtheworld.app.activities;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.views.CountryViewState;
import com.climbtheworld.app.storage.views.DataFragment;
import com.climbtheworld.app.storage.views.LocalPagerFragment;
import com.climbtheworld.app.storage.views.RemotePagerFragment;
import com.climbtheworld.app.storage.views.UploadPagerFragment;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.views.EdgeToEdge;
import com.climbtheworld.app.utils.views.IPagerViewFragment;
import com.climbtheworld.app.utils.views.PagerViewAdapter;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NodesDataManagerActivity extends AppCompatActivity {
	private static final String TAB_ID_PARAMETER = "tabID";
	private static final String DOWNLOAD_TAB_ID = "download";
	private ViewPager viewPager;

	private BottomNavigationView navigation;
	private final List<IPagerViewFragment> views = new ArrayList<>();

	/** Opens the activity on the tab that lists every country and its download progress. */
	public static Intent downloadTabIntent(Context context) {
		return new Intent(context, NodesDataManagerActivity.class)
				.setData(new Uri.Builder().scheme("climbtheworld").authority("data_manager")
						.appendQueryParameter(TAB_ID_PARAMETER, DOWNLOAD_TAB_ID).build());
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_nodes_data_manager);

		EdgeToEdge.padForSystemBars(findViewById(R.id.main));

		Map<String, CountryViewState> countryMap = DataFragment.initCountryMap(this);

		views.add(new LocalPagerFragment(this, R.layout.fragment_data_manager_loca_data, countryMap));
		views.add(new RemotePagerFragment(this, R.layout.fragment_data_manager_remote_data, countryMap));
		views.add(new UploadPagerFragment(this, R.layout.fragment_data_manager_upload_data));

		viewPager = findViewById(R.id.dataContainerPager);
		viewPager.setAdapter(new PagerViewAdapter(getLayoutInflater(), views));

		navigation = findViewById(R.id.convertersNavigationBar);
		navigation.setItemIconTintList(null);
		PagerViewAdapter.linkNavigation(navigation, viewPager, views);
	}

	@Override
	protected void onStart() {
		Uri data = getIntent().getData();
		if (data != null && DOWNLOAD_TAB_ID.equalsIgnoreCase(data.getQueryParameter(TAB_ID_PARAMETER))) {
			// Only once, so coming back to the activity keeps the tab the user chose since.
			getIntent().setData(null);
			navigation.postDelayed(new Runnable() {
				public void run() {
					navigation.setSelectedItemId(R.id.navigation_download);
				}
			}, 500);
		}
		super.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();

		Globals.onResume(this);
	}

	@Override
	protected void onPause() {
		Globals.onPause(this);

		super.onPause();
	}

	@Override
	protected void onDestroy() {
		for (IPagerViewFragment view: views) {
			view.onDestroy(null);
		}
		super.onDestroy();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		((DataFragment) views.get(viewPager.getCurrentItem())).onActivityResult(requestCode, resultCode, data);
	}

}
