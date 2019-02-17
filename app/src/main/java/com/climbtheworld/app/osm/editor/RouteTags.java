package com.climbtheworld.app.osm.editor;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
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
        updateGradeSpinner(editNode);
        loadStyles(editNode);

        editLength.setText(editNode.getKey(GeoNode.KEY_LENGTH));
        editPitches.setText(editNode.getKey(GeoNode.KEY_PITCHES));
    }

    private void updateGradeSpinner(GeoNode editNode) {
        List<String> allGrades = GradeConverter.getConverter().getAllGrades(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(parent, android.R.layout.simple_spinner_dropdown_item, allGrades) {
            // Change color item
            @Override
            public View getDropDownView(int position, View convertView,
                                        ViewGroup itemParent) {
                View mView = super.getDropDownView(position, convertView, itemParent);
                TextView mTextView = (TextView) mView;

                mTextView.setBackgroundColor(Globals.gradeToColorState(position).getDefaultColor());
                return mView;
            }
        };

        dropdownGrade.setAdapter(adapter);
        dropdownGrade.setSelection(editNode.getLevelId(GeoNode.KEY_GRADE_TAG), false);
    }

    @Override
    public void saveToNode(GeoNode editNode) {
        editNode.setKey(GeoNode.KEY_LENGTH, editLength.getText().toString());
        editNode.setKey(GeoNode.KEY_PITCHES, editPitches.getText().toString());

        saveStyles(editNode);
        editNode.setLevelFromID(dropdownGrade.getSelectedItemPosition(), GeoNode.KEY_GRADE_TAG);
    }

    @Override
    public void cancelNode(GeoNode editNode) {
        editNode.setKey(GeoNode.KEY_LENGTH, null);
        editNode.setKey(GeoNode.KEY_PITCHES, null);
        editNode.removeLevelTags(GeoNode.KEY_GRADE_TAG);

        List<GeoNode.ClimbingStyle> styles = new ArrayList<>();
        editNode.setClimbingStyles(styles);
    }
}
