package com.climbtheworld.app.osm.editor;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeConverter;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.ViewUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class RouteTags extends Tags implements ITags {

    public RouteTags(GeoNode poi, final Activity parent, ViewGroup container) {
        this.container = container;

        container.addView(parent.getLayoutInflater().inflate(R.layout.fragment_edit_route, container, false));

        Spinner dropdownGrade = parent.findViewById(R.id.gradeSpinner);

        ((TextView)parent.findViewById(R.id.grading)).setText(parent.getResources().getString(R.string.grade_system, Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)));
        List<String> allGrades = GradeConverter.getConverter().getAllGrades(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem));
        dropdownGrade.setAdapter(new ArrayAdapter<>(parent, android.R.layout.simple_spinner_dropdown_item, allGrades));
        dropdownGrade.setSelection(poi.getLevelId());
        loadStyles(poi, parent);

        hideTags();
    }

    private void loadStyles(GeoNode poi, final Activity parent) {
        Map<String, GeoNode.ClimbingStyle> climbStyle = new TreeMap<>();
        for (GeoNode.ClimbingStyle style: GeoNode.ClimbingStyle.values())
        {
            climbStyle.put(style.name(), style);
        }

        Set<GeoNode.ClimbingStyle> checked = poi.getClimbingStyles();

        RadioGroup container = parent.findViewById(R.id.radioGroupStyles);

        for (GeoNode.ClimbingStyle styleName: climbStyle.values())
        {
            View customSwitch = ViewUtils.buildCustomSwitch(parent, styleName.getNameId(), styleName.getDescriptionId(), checked.contains(styleName), null);
            Switch styleCheckBox = customSwitch.findViewById(R.id.switchTypeEnabled);
            styleCheckBox.setId(styleName.getNameId());

            container.addView(customSwitch);
        }
    }

    @Override
    public void SaveToNode(GeoNode poi) {

    }
}
