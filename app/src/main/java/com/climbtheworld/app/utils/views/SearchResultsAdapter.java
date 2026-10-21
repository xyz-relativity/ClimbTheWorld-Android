package com.climbtheworld.app.utils.views;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.views.dialogs.DialogueUtils;

import java.util.List;
import java.util.function.Consumer;

/** Lists the nodes a search found, each as its pin with its name, opening on a tap. */
public class SearchResultsAdapter extends BaseAdapter {
	private final AppCompatActivity activity;
	private final List<DisplayableGeoNode> results;
	private final Consumer<GeoNode> onSelected;

	/** @param results the nodes found, read again on every {@link #notifyDataSetChanged} */
	public SearchResultsAdapter(AppCompatActivity activity, List<DisplayableGeoNode> results,
	                            Consumer<GeoNode> onSelected) {
		this.activity = activity;
		this.results = results;
		this.onSelected = onSelected;
	}

	@Override
	public int getCount() {
		return results.size();
	}

	@Override
	public Object getItem(int i) {
		return results.get(i);
	}

	@Override
	public long getItemId(int i) {
		return i;
	}

	@Override
	public View getView(int i, View view, ViewGroup viewGroup) {
		final DisplayableGeoNode marker = results.get(i);
		view = ListViewItemBuilder.getPaddedBuilder(activity, view, true)
				.setTitle(marker.getGeoNode().getName())
				.setDescription(DialogueUtils.buildDescription(activity, marker.getGeoNode()))
				.setIcon(new PoiMarkerDrawable(activity, marker))
				.build();
		view.setOnClickListener(v -> onSelected.accept(marker.getGeoNode()));
		return view;
	}
}
