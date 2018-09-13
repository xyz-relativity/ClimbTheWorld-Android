package com.climbtheworld.app.activitys;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.views.IDataViewFragment;
import com.climbtheworld.app.storage.views.LocalDataFragment;
import com.climbtheworld.app.storage.views.RemoteDataFragment;
import com.climbtheworld.app.storage.views.UploadDataFragment;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.DialogBuilder;
import com.climbtheworld.app.utils.Globals;

import java.util.ArrayList;
import java.util.List;

public class NodesDataManagerActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {
    private LayoutInflater inflater;
    private ViewPager viewPager;

    private BottomNavigationView navigation;
    private List<IDataViewFragment> views = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nodes_data_manager);

        inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        navigation = findViewById(R.id.dataNavigationBar);
        navigation.setOnNavigationItemSelectedListener(this);

        Globals.loadCountryList();

        views.add(new LocalDataFragment(this, R.layout.fragment_data_manager_loca_data));
        views.add(new RemoteDataFragment(this, R.layout.fragment_data_manager_remote_data));
        views.add(new UploadDataFragment(this, R.layout.fragment_data_manager_upload_data));

        viewPager = findViewById(R.id.dataContainerPager);
        viewPager.setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return views.size();
            }

            @Override
            public Object instantiateItem(ViewGroup collection, int position) {
                IDataViewFragment fragment = views.get(position);
                ViewGroup layout = (ViewGroup) inflater.inflate(fragment.getViewId(), collection, false);
                collection.addView(layout);
                fragment.onCreate(layout);
                return layout;
            }

            @Override
            public void destroyItem(ViewGroup collection, int position, Object view) {
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

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
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

    public void pushTab() {
        ((UploadDataFragment)views.get(2)).pushTab();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.OPEN_OAUTH_ACTIVITY) {
            if (Globals.oauthToken == null) {
                DialogBuilder.showErrorDialog(this, getString(R.string.oauth_failed), null);
            } else {
                ((UploadDataFragment)views.get(2)).pushToOsm();
            }
        }
    }
}
