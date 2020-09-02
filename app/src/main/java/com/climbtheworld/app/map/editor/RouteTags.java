package com.climbtheworld.app.map.editor;

import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.SpinnerUtils;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

public class RouteTags extends Tags implements ITags {

    private final EditText editLength;
    private final EditText editPitches;
    private final EditText editBolts;
    private Spinner dropdownGrade;

    public RouteTags(GeoNode editNode, final AppCompatActivity parent, ViewGroup container) {
        super(parent, container, R.layout.fragment_edit_route);

        this.editLength = container.findViewById(R.id.editLength);
        this.editPitches = container.findViewById(R.id.editpitches);
        this.editBolts = container.findViewById(R.id.editbolts);
        this.dropdownGrade = container.findViewById(R.id.gradeSelectSpinner);

        ((TextView)container.findViewById(R.id.routeGrading)).setText(parent.getResources().getString(R.string.grade_system,
                parent.getResources().getString(GradeSystem.fromString(Configs.instance(parent).getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
        SpinnerUtils.updateGradeSpinner(parent, dropdownGrade, editNode, true);
        loadStyles(editNode);

        editLength.setText(editNode.getKey(GeoNode.KEY_LENGTH));
        editPitches.setText(editNode.getKey(GeoNode.KEY_PITCHES));
        editBolts.setText(editNode.getKey(GeoNode.KEY_BOLTS));
    }

    @Override
    public boolean saveToNode(GeoNode editNode) {
        editNode.setKey(GeoNode.KEY_LENGTH, editLength.getText().toString());
        editNode.setKey(GeoNode.KEY_PITCHES, editPitches.getText().toString());
        editNode.setKey(GeoNode.KEY_BOLTS, editBolts.getText().toString());

        saveStyles(editNode);
        editNode.setLevelFromID(SpinnerUtils.getGradeID(dropdownGrade, true), GeoNode.KEY_GRADE_TAG);
        return true;
    }

    @Override
    public void cancelNode(GeoNode editNode) {
        editNode.setKey(GeoNode.KEY_LENGTH, null);
        editNode.setKey(GeoNode.KEY_PITCHES, null);
        editNode.setKey(GeoNode.KEY_BOLTS, null);
        editNode.removeLevelTags(GeoNode.KEY_GRADE_TAG);

        List<GeoNode.ClimbingStyle> styles = new ArrayList<>();
        editNode.setClimbingStyles(styles);
    }
}
