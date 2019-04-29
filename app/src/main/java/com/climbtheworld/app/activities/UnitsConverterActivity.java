package com.climbtheworld.app.activities;

import android.content.Context;
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
import com.climbtheworld.app.converter.GradeConverter;
import com.climbtheworld.app.storage.views.UploadPagerFragment;
import com.climbtheworld.app.utils.IPagerViewFragment;

import java.util.ArrayList;
import java.util.List;

public class UnitsConverterActivity extends AppCompatActivity {
    private LayoutInflater inflater;
    private ViewPager viewPager;

    private BottomNavigationView navigation;
    private List<IPagerViewFragment> views = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_units_converter);

        inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        navigation = findViewById(R.id.dataNavigationBar);
        navigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.converter_navigation_climbing_grades:
                        viewPager.setCurrentItem(0, true);
                        return true;
                    case R.id.converter_navigation_length_units:
                        viewPager.setCurrentItem(1, true);
                        return true;
                    case R.id.converter_navigation_weight_units:
                        viewPager.setCurrentItem(2, true);
                        return true;
                }
                return false;
            }
        });

        views.add(new GradeConverter(this, R.layout.fragment_units_converter_grade));
        views.add(new UploadPagerFragment(this, R.layout.fragment_data_manager_upload_data));
        views.add(new UploadPagerFragment(this, R.layout.fragment_data_manager_upload_data));

        viewPager = findViewById(R.id.converterContainerPager);
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
}
