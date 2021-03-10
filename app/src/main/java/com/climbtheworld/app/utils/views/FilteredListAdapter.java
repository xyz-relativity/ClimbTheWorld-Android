package com.climbtheworld.app.utils.views;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class FilteredListAdapter < T > extends BaseAdapter {
	protected List<T> visibleList = new ArrayList<>();
	protected List<T> initialList;

	public FilteredListAdapter(List<T> initialList) {
		this.initialList = initialList;
		this.visibleList.addAll(initialList);
	}

	public void applyFilter(String charText) {
		charText = charText.toLowerCase(Locale.getDefault());
		visibleList.clear();
		if (charText.length() == 0) {
			visibleList.addAll(initialList);
		} else {
			for (int i = 0; i<initialList.size(); ++i) {
				if (isVisible(i, charText.toUpperCase())) {
					visibleList.add(initialList.get(i));
				}
			}
		}
		notifyDataSetChanged();
	}

	protected abstract boolean isVisible(int i, String filter);

	@Override
	public int getCount() {
		return visibleList.size();
	}

	@Override
	public Object getItem(int i) {
		return visibleList.get(i);
	}

	@Override
	public long getItemId(int i) {
		return i;
	}

	@Override
	public abstract View getView(int i, View view, ViewGroup viewGroup);
}
