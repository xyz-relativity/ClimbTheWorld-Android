package com.climbtheworld.app.utils.views;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.NodeDisplayFilters;
import com.climbtheworld.app.map.widget.climbing.ClimbingParent;
import com.climbtheworld.app.map.widget.climbing.ClimbingRouteCounter;
import com.climbtheworld.app.storage.database.GeoNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Lists the nodes a search found, see ClimbingPoiRow, each opening on a tap, but those the
 * display filters leave out, which fade out and open nothing, as on the map.
 */
public class SearchResultsAdapter extends BaseAdapter {
	private final ClimbingPoiRow rows;
	private final List<Result> results;
	private final Consumer<GeoNode> onSelected;

	/**
	 * A node found, with what it is part of.
	 *
	 * @param pin faded out when the display filters leave it out, see
	 *            ClimbingRouteCounter.isFilteredOut
	 */
	public record Result(DisplayableGeoNode pin, String parentLabel) {
		/**
		 * What each node found is part of, loaded off the UI thread, and which the display
		 * filters leave out.
		 */
		public static List<Result> load(Context context, List<DisplayableGeoNode> found) {
			Predicate<GeoNode> routeFilter =
					NodeDisplayFilters.routeFilter(Configs.instance(context));
			List<Result> result = new ArrayList<>();
			for (DisplayableGeoNode pin : found) {
				pin.setGhost(ClimbingRouteCounter.isFilteredOut(pin.getGeoNode(), routeFilter));
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
		if (result.pin().isGhost()) {
			view.setOnClickListener(null);
			view.setClickable(false);
		} else {
			view.setOnClickListener(v -> onSelected.accept(node));
		}
		return view;
	}
}
