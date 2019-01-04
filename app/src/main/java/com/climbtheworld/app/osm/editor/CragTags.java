package com.climbtheworld.app.osm.editor;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeConverter;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import java.util.List;

public class CragTags extends Tags implements ITags {
    private final Spinner minGrade;
    private final Spinner maxGrade;

    public CragTags(GeoNode poi, final Activity parent, ViewGroup container) {
        super(parent, container, R.layout.fragment_edit_crag);

        this.minGrade = container.findViewById(R.id.minGradeSpinner);
        this.maxGrade = container.findViewById(R.id.maxGradeSpinner);

        ((TextView)container.findViewById(R.id.grading)).setText(parent.getResources().getString(R.string.grade_system, Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)));
        List<String> allGrades = GradeConverter.getConverter().getAllGrades(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem));
        minGrade.setAdapter(new ArrayAdapter<>(parent, android.R.layout.simple_spinner_dropdown_item, allGrades));
        minGrade.setSelection(poi.getLevelId());

        maxGrade.setAdapter(new ArrayAdapter<>(parent, android.R.layout.simple_spinner_dropdown_item, allGrades));
        maxGrade.setSelection(poi.getLevelId());

        loadStyles(poi);
    }

    @Override
    public void SaveToNode(GeoNode poi) {

    }
}
