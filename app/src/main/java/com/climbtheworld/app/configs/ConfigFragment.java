package com.climbtheworld.app.configs;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;

import java.util.LinkedList;
import java.util.List;

public abstract class ConfigFragment {
	protected final AppCompatActivity parent;
	protected final View view;

	private final List<OnFilterChangeListener> listenerList = new LinkedList<>();

	public interface OnFilterChangeListener {
		void onFilterChange();
	}

	void notifyListeners() {
		for (OnFilterChangeListener listener : listenerList) {
			listener.onFilterChange();
		}
	}

	public void addListener(OnFilterChangeListener listener) {
		listenerList.add(listener);
	}

	ConfigFragment(AppCompatActivity parent, View view) {
		this.parent = parent;
		this.view = view;
	}

	public static void addSwitch(ViewGroup viewContainer, CompoundButton.OnCheckedChangeListener listener, Configs.ConfigKey config) {
		Context parent = viewContainer.getContext();
		View newView = ListViewItemBuilder.getPaddedBuilder(viewContainer.getContext())
				.setTitle(parent.getString(config.stringId))
				.setDescription(parent.getString(config.descriptionId))
				.setSwitchChecked(Configs.instance(parent).getBoolean(config))
				.setSwitchEvent(listener)
				.changeElementId(R.id.switchTypeEnabled, config.stringId)
				.build();
		viewContainer.addView(newView);
	}

	protected <T extends View> T findViewById(@IdRes int id) {
		return view.findViewById(id);
	}
}
