package com.climbtheworld.app.utils.views;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.widget.climbing.ClimbingParent;
import com.climbtheworld.app.storage.database.GeoNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Lists the nodes a search found, see ClimbingPoiRow, each opening on a tap. */
public class SearchResultsAdapter extends BaseAdapter {
	private final ClimbingPoiRow rows;
	private final List<Result> results;
	private final Consumer<GeoNode> onSelected;

	/** A node found, with what it is part of. */
	public record Result(DisplayableGeoNode pin, String parentLabel) {
		/** What each node found is part of, loaded off the UI thread. */
		public static List<Result> load(Context context, List<DisplayableGeoNode> found) {
			List<Result> result = new ArrayList<>();
			for (DisplayableGeoNode pin : found) {
				result.add(new Result(pin, ClimbingParent.labelOf(context, pin.getGeoNode())));
			}
			return result;
		}
	}

	/**
	 * @param results   the nodes found, read again on every {@link #notifyDataSetChanged}
	 * @param closeList closes the results before the map shows one, see ClimbingPoiRow
	 */
	public SearchResultsAdapter(AppCompatActivity activity, List<Result> results,
	                            Consumer<GeoNode> onSelected, Runnable closeList) {
		this.rows = new ClimbingPoiRow(activity, closeList);
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
		final Result result = results.get(i);
		GeoNode node = result.pin().getGeoNode();
		view = rows.build(view, result.pin(), node.getName(), result.parentLabel());
		view.setOnClickListener(v -> onSelected.accept(node));
		return view;
	}
}
