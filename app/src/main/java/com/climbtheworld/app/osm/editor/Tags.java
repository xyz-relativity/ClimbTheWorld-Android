package com.climbtheworld.app.osm.editor;

import android.app.Activity;
import android.support.annotation.LayoutRes;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.ViewUtils;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public abstract class Tags {
    ViewGroup container;
    View tagsView;
    Activity parent;

    public Tags(Activity parent, ViewGroup container, @LayoutRes int resource) {
        this.container = container;
        this.parent = parent;
        this.tagsView = parent.getLayoutInflater().inflate(resource, container, false);

        container.addView(tagsView);
    }

    void loadStyles(GeoNode poi) {
        Map<String, GeoNode.ClimbingStyle> climbStyle = new TreeMap<>();
        for (GeoNode.ClimbingStyle style: GeoNode.ClimbingStyle.values())
        {
            climbStyle.put(style.name(), style);
        }

        Set<GeoNode.ClimbingStyle> checked = poi.getClimbingStyles();

        RadioGroup stylesContainer = tagsView.findViewById(R.id.radioGroupStyles);

        for (GeoNode.ClimbingStyle styleName: climbStyle.values())
        {
            View customSwitch = ViewUtils.buildCustomSwitch(parent, styleName.getNameId(), styleName.getDescriptionId(), checked.contains(styleName), null);
            customSwitch.setId(styleName.getNameId());

            stylesContainer.addView(customSwitch);
        }
    }

    public void showTags() {
        tagsView.setVisibility(View.VISIBLE);
    }

    public void hideTags() {
        tagsView.setVisibility(View.GONE);
    }

    public boolean isVisible() {
        return tagsView.getVisibility() == View.VISIBLE;
    }
}
