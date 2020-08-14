package com.climbtheworld.app.utils.marker;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

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
        return getCustomView(position, true);
    }

    @NotNull
    @Override
    public View getView(int position, View convertView, @NotNull ViewGroup parent) {
        return getCustomView(position, false);
    }

    private View getCustomView(int position, boolean selected) {
        GeoNode poi = new GeoNode(0, 0, 0);
        poi.setNodeType(getItem(position));
        poi.setLevelFromID(editPoi.getLevelId(GeoNode.KEY_GRADE_TAG), GeoNode.KEY_GRADE_TAG);

        View v = ListViewItemBuilder.getBuilder(context)
                .setTitle(context.getString(getItem(position).getNameId()))
                .setDescription(context.getString(getItem(position).getDescriptionId()))
                .setIcon(MarkerUtils.getPoiIcon(context, poi))
                .build();
        if (selected && editPoi.getNodeType() == getItem(position)) {
            v.setBackgroundColor(Color.parseColor("#eecccccc"));
        }
        return v;
    }
}
