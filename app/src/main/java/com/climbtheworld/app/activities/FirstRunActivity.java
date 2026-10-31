package com.climbtheworld.app.activities;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.ArCoreFeatures;
import com.climbtheworld.app.tutorial.DisclaimerFragment;
import com.climbtheworld.app.tutorial.DownloadRegionFragment;
import com.climbtheworld.app.tutorial.RoutesSettingsFragment;
import com.climbtheworld.app.tutorial.SupportUsFragment;
import com.climbtheworld.app.tutorial.TutorialFragment;
import com.climbtheworld.app.tutorial.WelcomeFragment;
import com.climbtheworld.app.utils.views.EdgeToEdge;
import com.climbtheworld.app.utils.views.PagerViewAdapter;

import java.util.ArrayList;
import java.util.List;

public class FirstRunActivity extends AppCompatActivity implements View.OnClickListener {

	private final List<TutorialFragment> views = new ArrayList<>();
	private ViewPager viewPager;
	private SeekBar progressBar;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_first_run);

		EdgeToEdge.padForSystemBars(findViewById(R.id.main));

		views.add(new WelcomeFragment(this, R.layout.fragment_tutorial_welcome));
		views.add(new DownloadRegionFragment(this, R.layout.fragment_tutorial_download));
		views.add(new RoutesSettingsFragment(this, R.layout.fragment_tutorial_routes_settiongs));
		TutorialFragment arCorePage = ArCoreFeatures.tutorialPage(this);
		if (arCorePage != null) {
			views.add(arCorePage);
		}
		views.add(new SupportUsFragment(this, R.layout.fragment_tutorial_support_us));
		views.add(new DisclaimerFragment(this, R.layout.fragment_tutorial_disclaimer));

		viewPager = findViewById(R.id.firstRunPager);
		viewPager.setAdapter(new PagerViewAdapter(getLayoutInflater(), views));
		viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				progressBar.setProgress(position);
			}
		});

		progressBar = findViewById(R.id.pageProgress);
		progressBar.setMax(views.size() - 1);
		progressBar.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return true; //disable user action
			}
		});
	}

	@Override
	public void onClick(View v) {
		int nextPos = viewPager.getCurrentItem() + 1;
		if (nextPos < views.size()) {
			viewPager.setCurrentItem(nextPos, true);
			progressBar.setProgress(viewPager.getCurrentItem());
		} else {
			finish();
		}
	}
}
