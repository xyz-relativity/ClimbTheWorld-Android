package com.climbtheworld.app.map.editor;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.constants.UIConstants;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;

import org.jetbrains.annotations.NotNull;

public class SpinnerMarkerArrayAdapter extends ArrayAdapter<GeoNode.NodeTypes> {

	AppCompatActivity context;
	GeoNode editPoi;

	public SpinnerMarkerArrayAdapter(AppCompatActivity context, int resource, GeoNode.NodeTypes[] objects, GeoNode poi) {
		super(context, resource, objects);
		this.context = context;
		this.editPoi = poi;
	}

	@Override
	public View getDropDownView(int position, View convertView, @NotNull ViewGroup parent) {
		return getCustomView(position, convertView, true);
	}

	@NotNull
	@Override
	public View getView(int position, View convertView, @NotNull ViewGroup parent) {
		return getCustomView(position, convertView, false);
	}

	private View getCustomView(int position, View convertView, boolean selected) {
		GeoNode poi = new GeoNode(0, 0, 0);
		poi.setClimbingType(getItem(position));
//        poi.setLevelFromID(editPoi.getLevelId(ClimbingTags.KEY_GRADE_TAG), ClimbingTags.KEY_GRADE_TAG);

		View v = ListViewItemBuilder.getPaddedBuilder(context, convertView, false)
				.setTitle(context.getString(getItem(position).getNameId()))
				.setDescription(context.getString(getItem(position).getDescriptionId()))
				.setIcon(new PoiMarkerDrawable(context, null, new DisplayableGeoNode(poi), 0, 0))
				.setIconSize(UIConstants.POI_TYPE_LIST_ICON_SIZE, UIConstants.POI_TYPE_LIST_ICON_SIZE)
				.build();
		if (selected && editPoi.getNodeType() == getItem(position)) {
			v.setBackgroundColor(Color.parseColor("#eecccccc"));
		}
		return v;
	}
}
