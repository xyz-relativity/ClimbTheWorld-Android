package com.climbtheworld.app.utils.marker;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.climbtheworld.app.openstreetmap.ui.DisplayableGeoNode;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.ListViewItemBuilder;

import org.jetbrains.annotations.NotNull;

import androidx.appcompat.app.AppCompatActivity;

public class SpinnerMarkerArrayAdapter extends ArrayAdapter<GeoNode.NodeTypes> {

    AppCompatActivity context;
    GeoNode editPoi;

    public SpinnerMarkerArrayAdapter(AppCompatActivity context, int resource, GeoNode.NodeTypes[] objects, GeoNode poi) {
        super(context, resource, objects);
        this.context = context;
        this.editPoi = poi;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getCustomView(position, convertView, true);
    }

    @NotNull
    @Override
    public View getView(int position, View convertView, @NotNull ViewGroup parent) {
        return getCustomView(position, convertView, false);
    }

    private View getCustomView(int position, View convertView, boolean selected) {
        GeoNode poi = new GeoNode(0, 0, 0);
        poi.setNodeType(getItem(position));
        poi.setLevelFromID(editPoi.getLevelId(GeoNode.KEY_GRADE_TAG), GeoNode.KEY_GRADE_TAG);

        View v = ListViewItemBuilder.getBuilder(context, convertView)
                .setTitle(context.getString(getItem(position).getNameId()))
                .setDescription(context.getString(getItem(position).getDescriptionId()))
                .setIcon(new LazyMarkerDrawable(context, null, new DisplayableGeoNode(poi), 0, 0))
                .build();
        if (selected && editPoi.getNodeType() == getItem(position)) {
            v.setBackgroundColor(Color.parseColor("#eecccccc"));
        }
        return v;
    }
}
