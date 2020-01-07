package com.climbtheworld.app.activities;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import com.climbtheworld.app.R;
import com.climbtheworld.app.tutorial.DataUsageFragment;
import com.climbtheworld.app.tutorial.DisclaimerFragment;
import com.climbtheworld.app.tutorial.DownloadRegionFragment;
import com.climbtheworld.app.tutorial.RoutesSettingsFragment;
import com.climbtheworld.app.tutorial.SupportUsFragment;
import com.climbtheworld.app.tutorial.TutorialFragment;
import com.climbtheworld.app.tutorial.WelcomeFragment;
import com.climbtheworld.app.utils.Globals;

import java.util.ArrayList;
import java.util.List;

public class FirstRunActivity extends AppCompatActivity implements View.OnClickListener {

    private List<TutorialFragment> views = new ArrayList<>();
    private ViewPager viewPager;
    private SeekBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_run);

        Globals.loadCountryList();

        views.add(new WelcomeFragment(this, R.layout.fragment_tutorial_welcome));
        views.add(new DataUsageFragment(this, R.layout.fragment_tutorial_data_usage));
        views.add(new DownloadRegionFragment(this, R.layout.fragment_tutorial_download));
        views.add(new RoutesSettingsFragment(this, R.layout.fragment_tutorial_routes_settiongs));
        views.add(new SupportUsFragment(this, R.layout.fragment_tutorial_support_us));
        views.add(new DisclaimerFragment(this, R.layout.fragment_tutorial_disclaimer));

        viewPager = findViewById(R.id.firstRunPager);
        viewPager.setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return views.size();
            }

            @NonNull
            @Override
            public Object instantiateItem(@NonNull ViewGroup collection, int position) {
                TutorialFragment fragment = views.get(position);
                ViewGroup layout = (ViewGroup) fragment.inflater.inflate(fragment.getViewId(), collection, false);
                collection.addView(layout);
                fragment.onCreate(layout);
                return layout;
            }

            @Override
            public void destroyItem(@NonNull ViewGroup collection, int position, @NonNull Object view) {
                collection.removeView((View) view);
            }

            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return view == object;
            }
        });

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                progressBar.setProgress(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        progressBar = findViewById(R.id.pageProgress);
        progressBar.setMax(views.size()-1);
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
