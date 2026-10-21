package com.climbtheworld.app.utils.views;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

/** Pages through views inflated from the fragments' layouts, which the fragments then fill. */
public class PagerViewAdapter extends PagerAdapter {
	private final LayoutInflater inflater;
	private final List<? extends IPagerViewFragment> views;

	public PagerViewAdapter(LayoutInflater inflater, List<? extends IPagerViewFragment> views) {
		this.inflater = inflater;
		this.views = views;
	}

	/**
	 * Ties a navigation bar to the pager: picking an item shows its page and swiping to a page
	 * checks its item, the bar's items being in the pages' order.
	 */
	public static void linkNavigation(BottomNavigationView navigation, ViewPager viewPager,
	                                  List<? extends IPagerViewFragment> views) {
		navigation.setOnItemSelectedListener(menuItem -> {
			Menu menu = navigation.getMenu();
			for (int index = 0; index < menu.size(); index++) {
				if (menu.getItem(index) == menuItem) {
					viewPager.setCurrentItem(index, true);
					return true;
				}
			}
			return false;
		});
		viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				navigation.getMenu().getItem(position).setChecked(true);
				views.get(position).onViewSelected();
			}
		});
	}

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
		views.get(position).onDestroy(collection);
		collection.removeView((View) view);
	}

	@Override
	public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
		return view == object;
	}
}
