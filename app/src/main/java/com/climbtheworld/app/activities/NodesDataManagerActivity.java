package com.climbtheworld.app.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.climbtheworld.app.R;
import com.climbtheworld.app.oauth.OAuthHelper;
import com.climbtheworld.app.storage.views.LocalPagerFragment;
import com.climbtheworld.app.storage.views.RemotePagerFragment;
import com.climbtheworld.app.storage.views.UploadPagerFragment;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.IPagerViewFragment;
import com.climbtheworld.app.utils.dialogs.DialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class NodesDataManagerActivity extends Activity {
    private LayoutInflater inflater;
    private ViewPager viewPager;

    private BottomNavigationView navigation;
    private List<IPagerViewFragment> views = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nodes_data_manager);

        inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        navigation = findViewById(R.id.dataNavigationBar);
        navigation.setItemIconTintList(null);
        navigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.navigation_download:
                        viewPager.setCurrentItem(1, true);
                        return true;
                    case R.id.navigation_upload:
                        viewPager.setCurrentItem(2, true);
                        return true;
                    case R.id.navigation_local:
                        viewPager.setCurrentItem(0, true);
                        return true;
                }
                return false;
            }
        });

        Globals.loadCountryList();

        views.add(new LocalPagerFragment(this, R.layout.fragment_data_manager_loca_data));
        views.add(new RemotePagerFragment(this, R.layout.fragment_data_manager_remote_data));
        views.add(new UploadPagerFragment(this, R.layout.fragment_data_manager_upload_data));

        viewPager = findViewById(R.id.dataContainerPager);
        viewPager.setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return views.size();
            }

            @NonNull
            @Override
            public Object instantiateItem(@NonNull ViewGroup collection, int position) {
                IPagerViewFragment fragment = views.get(position);
                ViewGroup layout = (ViewGroup) inflater.inflate(fragment.getViewId(), collection, false);
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
                navigation.getMenu().getItem(position).setChecked(true);
                views.get(position).onViewSelected();
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    @Override
    protected void onStart() {
        Uri data = getIntent().getData();
        if (data != null) {
            if (data.getQueryParameter("tabID").equalsIgnoreCase("download")) {
                navigation.postDelayed(new Runnable() {
                    public void run() {
                        navigation.setSelectedItemId(R.id.navigation_download);
                    }
                }, 500);
            }
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

    public void pushTab() {
        ((UploadPagerFragment)views.get(2)).pushTab();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.OPEN_OAUTH_ACTIVITY) {
            if (OAuthHelper.needsAuthentication()) {
                DialogBuilder.showErrorDialog(this, getString(R.string.oauth_failed), null);
            } else {
                ((UploadPagerFragment)views.get(2)).pushToOsm();
            }
        }
    }
}
