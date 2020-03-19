package com.climbtheworld.app.openstreetmap.editor;

import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.Switch;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.ListViewItemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public abstract class Tags {
    private ViewGroup container;
    private View tagsView;
    private AppCompatActivity parent;

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

        RadioGroup stylesContainer = tagsView.findViewById(R.id.radioGroupStyles);

        for (GeoNode.ClimbingStyle styleName: climbStyle.values())
        {
            View customSwitch = ListViewItemBuilder.getBuilder(parent)
                    .setTitle(parent.getString(styleName.getNameId()))
                    .setDescription(parent.getString(styleName.getDescriptionId()))
                    .setSwitchChecked(checked.contains(styleName))
                    .build();
            customSwitch.setId(styleName.getNameId());

            stylesContainer.addView(customSwitch);
        }
    }

    void saveStyles(GeoNode poi) {
        List<GeoNode.ClimbingStyle> styles = new ArrayList<>();
        RadioGroup stylesContainer = tagsView.findViewById(R.id.radioGroupStyles);
        for (GeoNode.ClimbingStyle style : GeoNode.ClimbingStyle.values()) {
            ViewGroup styleCheckBox = stylesContainer.findViewById(style.getNameId());
            if (styleCheckBox != null) {
                if (((Switch)styleCheckBox.findViewById(R.id.switchTypeEnabled)).isChecked()) {
                    styles.add(style);
                }
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
