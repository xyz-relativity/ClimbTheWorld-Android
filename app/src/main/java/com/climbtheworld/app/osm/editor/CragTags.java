package com.climbtheworld.app.osm.editor;

import android.app.Activity;
import android.graphics.Color;
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

import java.util.List;

public class CragTags extends Tags implements ITags {
    private final Spinner minGrade;
    private final Spinner maxGrade;
    private final EditText editNumRoutes;
    private final EditText editMinLength;
    private final EditText editMaxLength;

    public CragTags(GeoNode poi, final Activity parent, ViewGroup container) {
        super(parent, container, R.layout.fragment_edit_crag);

        this.minGrade = container.findViewById(R.id.minGradeSpinner);
        this.maxGrade = container.findViewById(R.id.maxGradeSpinner);
        this.editNumRoutes = container.findViewById(R.id.editNumRoutes);
        this.editMinLength = container.findViewById(R.id.editMinLength);
        this.editMaxLength = container.findViewById(R.id.editMaxLength);

        updateMinSpinner(poi);
        updateMaxSpinner(poi);

        editNumRoutes.setText(poi.getKey(GeoNode.KEY_ROUTES));
        editMinLength.setText(poi.getKey(GeoNode.KEY_MIN_LENGTH));
        editMaxLength.setText(poi.getKey(GeoNode.KEY_MAX_LENGTH));

        loadStyles(poi);
    }

    private void updateMinSpinner(GeoNode poi) {
        List<String> allGrades = GradeConverter.getConverter().getAllGrades(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(parent, android.R.layout.simple_spinner_dropdown_item, allGrades) {
            // Disable click item < month current
            @Override
            public boolean isEnabled(int position) {
                return (maxGrade.getSelectedItemPosition() == 0)
                        || (position <= maxGrade.getSelectedItemPosition());
            }

            // Change color item
            @Override
            public View getDropDownView(int position, View convertView,
                                        ViewGroup itemParent) {
                View mView = super.getDropDownView(position, convertView, itemParent);
                TextView mTextView = (TextView) mView;
                if (isEnabled(position)) {
                    mTextView.setTextColor(Color.BLACK);
                } else {
                    mTextView.setTextColor(Color.GRAY);
                }
                return mView;
            }
        };
        minGrade.setAdapter(adapter);
//        minGrade.setSelection(Globals.globalConfigs.getInt(Configs.ConfigKey.filterMinGrade), false);
    }

    private void updateMaxSpinner(GeoNode poi) {
        List<String> allGrades = GradeConverter.getConverter().getAllGrades(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(parent, android.R.layout.simple_spinner_dropdown_item, allGrades) {
            // Disable click item < month current
            @Override
            public boolean isEnabled(int position) {
                return (minGrade.getSelectedItemPosition() == 0
                        || position >= minGrade.getSelectedItemPosition());
            }

            // Change color item
            @Override
            public View getDropDownView(int position, View convertView,
                                        ViewGroup itemParent) {
                View mView = super.getDropDownView(position, convertView, itemParent);
                TextView mTextView = (TextView) mView;
                if (isEnabled(position)) {
                    mTextView.setTextColor(Color.BLACK);
                } else {
                    mTextView.setTextColor(Color.GRAY);
                }
                return mView;
            }
        };
        maxGrade.setAdapter(adapter);
//        maxGrade.setSelection(Globals.globalConfigs.getInt(Configs.ConfigKey.filterMaxGrade), false);
    }

    @Override
    public void SaveToNode(GeoNode editNode) {
        if (isVisible()) {
            editNode.setKey(GeoNode.KEY_ROUTES, editNumRoutes.getText().toString());
            editNode.setKey(GeoNode.KEY_MIN_LENGTH, editMinLength.getText().toString());
            editNode.setKey(GeoNode.KEY_MAX_LENGTH, editMaxLength.getText().toString());
        }
    }
}
