package com.climbtheworld.app.osm.editor;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeSystem;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import java.util.ArrayList;
import java.util.List;

public class EditUtils {
    private static final int NO_UNKNOWN_INDEX_OFFSET = 0;
    private static final int WITH_UNKNOWN_INDEX_OFFSET = 1;

    private EditUtils() {
        //hide
    }

    public static void updateGradeSpinner(Activity parent, Spinner dropdownGrade, GeoNode node, boolean addUnknown) {
        int idOffset = NO_UNKNOWN_INDEX_OFFSET;

        List<String> allGrades = new ArrayList<String>();
        if (addUnknown) {
            allGrades.add(GeoNode.UNKNOWN_GRADE_STRING);
            idOffset = WITH_UNKNOWN_INDEX_OFFSET;
        }
        allGrades.addAll(GradeSystem.fromString(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)).getAllGrades());
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
        dropdownGrade.setSelection(node.getLevelId(GeoNode.KEY_GRADE_TAG) + idOffset, false);
    }

    public static int getGradeID(Spinner dropdownGrade) {
        if (((String)dropdownGrade.getItemAtPosition(0)).equalsIgnoreCase(GeoNode.UNKNOWN_GRADE_STRING)) {
            return dropdownGrade.getSelectedItemPosition() - WITH_UNKNOWN_INDEX_OFFSET;
        } else {
            return dropdownGrade.getSelectedItemPosition() - NO_UNKNOWN_INDEX_OFFSET;
        }
    }
}
