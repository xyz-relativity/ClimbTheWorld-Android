package com.climbtheworld.app.osm.editor;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeConverter;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import java.util.ArrayList;
import java.util.List;

public class RouteTags extends Tags implements ITags {

    private final EditText editLength;
    private final EditText editPitches;
    private Spinner dropdownGrade;

    public RouteTags(GeoNode editNode, final Activity parent, ViewGroup container) {
        super(parent, container, R.layout.fragment_edit_route);

        this.editLength = container.findViewById(R.id.editLength);
        this.editPitches = container.findViewById(R.id.editpitches);
        this.dropdownGrade = container.findViewById(R.id.gradeSpinner);

        ((TextView)container.findViewById(R.id.routeGrading)).setText(parent.getResources().getString(R.string.grade_system, Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)));
        List<String> allGrades = GradeConverter.getConverter().getAllGrades(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem));
        dropdownGrade.setAdapter(new ArrayAdapter<>(parent, android.R.layout.simple_spinner_dropdown_item, allGrades));
        dropdownGrade.setSelection(editNode.getLevelId());
        loadStyles(editNode);

        editLength.setText(editNode.getKey(GeoNode.KEY_LENGTH));
        editPitches.setText(editNode.getKey(GeoNode.KEY_PITCHES));
    }

    @Override
    public void SaveToNode(GeoNode editNode) {
        if (isVisible()) {
            editNode.setKey(GeoNode.KEY_LENGTH, editLength.getText().toString());
            editNode.setKey(GeoNode.KEY_PITCHES, editPitches.getText().toString());

            List<GeoNode.ClimbingStyle> styles = new ArrayList<>();
            for (GeoNode.ClimbingStyle style : GeoNode.ClimbingStyle.values()) {
                ViewGroup styleCheckBox = container.findViewById(style.getNameId());
                if (styleCheckBox != null) {
                    if (((Switch)styleCheckBox.findViewById(R.id.switchTypeEnabled)).isChecked()) {
                        styles.add(style);
                    }
                }
            }
            editNode.setClimbingStyles(styles);
            editNode.setLevelFromID(dropdownGrade.getSelectedItemPosition());
        }
    }
}
