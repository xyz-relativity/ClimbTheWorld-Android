package com.climbtheworld.app.openstreetmap.editor;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.ListViewItemBuilder;
import com.climbtheworld.app.utils.marker.MarkerUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

public abstract class Tags {
    private ViewGroup container;
    private View tagsView;
    protected AppCompatActivity parent;

    Tags(AppCompatActivity parent, ViewGroup container, @LayoutRes int resource) {
        this.container = container;
        this.parent = parent;
        this.tagsView = parent.getLayoutInflater().inflate(resource, container, false);

        container.addView(tagsView);
        hideTags();
    }

    void loadStyles(GeoNode poi) {
        Map<String, GeoNode.ClimbingStyle> climbStyle = new TreeMap<>();
        for (GeoNode.ClimbingStyle style: GeoNode.ClimbingStyle.values())
        {
            climbStyle.put(style.name(), style);
        }

        Set<GeoNode.ClimbingStyle> checked = poi.getClimbingStyles();

        ViewGroup stylesContainer = tagsView.findViewById(R.id.containerClimbingStyles);

        for (GeoNode.ClimbingStyle styleName: climbStyle.values())
        {
            View customSwitch = ListViewItemBuilder.getBuilder(parent)
                    .setTitle(parent.getString(styleName.getNameId()))
                    .setDescription(parent.getString(styleName.getDescriptionId()))
                    .setIcon(MarkerUtils.getStyleIcon(parent, new HashSet<>(Collections.singletonList(styleName))))
                    .setPadding(0, ListViewItemBuilder.DEFAULT_PADDING,0,  ListViewItemBuilder.DEFAULT_PADDING)
                    .setSwitchChecked(checked.contains(styleName))
                    .changeElementId(R.id.switchTypeEnabled, styleName.getNameId())
                    .build();

            stylesContainer.addView(customSwitch);
        }
    }

    void saveStyles(GeoNode poi) {
        List<GeoNode.ClimbingStyle> styles = new ArrayList<>();
        ViewGroup stylesContainer = tagsView.findViewById(R.id.containerClimbingStyles);
        for (GeoNode.ClimbingStyle style : GeoNode.ClimbingStyle.values()) {
            Switch styleCheckBox = stylesContainer.findViewById(style.getNameId());
            if (styleCheckBox != null && styleCheckBox.isChecked()) {
                styles.add(style);
            }
        }
        poi.setClimbingStyles(styles);
    }

    public boolean isVisible() {
        return tagsView.getVisibility() == View.VISIBLE;
    }

    public void showTags() {
        tagsView.setVisibility(View.VISIBLE);
    }

    public void hideTags() {
        tagsView.setVisibility(View.GONE);
    }
}
