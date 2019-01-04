package com.climbtheworld.app.osm.editor;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeConverter;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RouteTags extends Tags implements ITags {

    private final EditText editLength;
    private Spinner dropdownGrade;

    public RouteTags(GeoNode poi, final Activity parent, ViewGroup container) {
        super(parent, container, R.layout.fragment_edit_route);

        this.editLength = parent.findViewById(R.id.editLength);
        this.dropdownGrade = parent.findViewById(R.id.gradeSpinner);

        ((TextView)parent.findViewById(R.id.grading)).setText(parent.getResources().getString(R.string.grade_system, Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)));
        List<String> allGrades = GradeConverter.getConverter().getAllGrades(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem));
        dropdownGrade.setAdapter(new ArrayAdapter<>(parent, android.R.layout.simple_spinner_dropdown_item, allGrades));
        dropdownGrade.setSelection(poi.getLevelId());
        loadStyles(poi);

        editLength.setText(String.format(Locale.getDefault(), "%.2f", poi.getLengthMeters()));
    }

    @Override
    public void SaveToNode(GeoNode editNode) {
        if (isVisible()) {
            editNode.setLengthMeters(Double.parseDouble(editLength.getText().toString()));

            List<GeoNode.ClimbingStyle> styles = new ArrayList<>();
            for (GeoNode.ClimbingStyle style : GeoNode.ClimbingStyle.values()) {
                int id = parent.getResources().getIdentifier(style.name(), "id", parent.getPackageName());
                CheckBox styleCheckBox = container.findViewById(id);
                if (styleCheckBox != null && styleCheckBox.isChecked()) {
                    styles.add(style);
                }
            }
            editNode.setClimbingStyles(styles);
            editNode.setLevelFromID(dropdownGrade.getSelectedItemPosition());
        }
    }
}
