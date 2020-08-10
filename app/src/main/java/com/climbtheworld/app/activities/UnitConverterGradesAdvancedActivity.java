package com.climbtheworld.app.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TableRow;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.tools.GradeSystem;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import java.util.HashSet;
import java.util.Set;

import androidx.appcompat.app.AppCompatActivity;

public class UnitConverterGradesAdvancedActivity extends AppCompatActivity {

    private static final int TABLE_ALPHA = 120;
    private static final int TEXT_SIZE = 12;
    private static final int SELECTED_TEXT_SIZE = 24;

    private Set<GradeSystem> selectedHeader = new HashSet<>(2);
    private BaseAdapter listAdapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return GradeSystem.maxGrades;
        }

        @Override
        public Object getItem(int i) {
            return GradeSystem.uiaa.getGrade(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(final int selected, View view, ViewGroup viewGroup) {
            if (view == null) {
                view = inflater.inflate(R.layout.list_item_converter_table_row, viewGroup, false);
            }

            TableRow row = view.findViewById(R.id.tableRow);
            int color = Globals.gradeToColorState(selected, TABLE_ALPHA).getDefaultColor();

            for (int i=0; i < GradeSystem.printableValues().length; ++i) {
                TextView element = (TextView)row.getChildAt(i);
                final GradeSystem crSystem = GradeSystem.printableValues()[i];
                element.setText(crSystem.getGrade(selected));
                element.setBackgroundColor(color);
                if (selectedHeader.size() == 2) {
                    if (selectedHeader.contains(crSystem)) {
                        element.setVisibility(View.VISIBLE);
                        element.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SELECTED_TEXT_SIZE);
                    } else {
                        element.setVisibility(View.GONE);
                    }
                } else {
                    element.setVisibility(View.VISIBLE);
                    element.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE);
                }
            }
            return view;
        }
    };
    private LayoutInflater inflater;
    private ListView resultsList;
    private View header;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unit_converter_grades_advanced);

        inflater = getLayoutInflater();

        resultsList = findViewById(R.id.listGradesConverter);
        resultsList.setAdapter(listAdapter);

        header = inflater.inflate(R.layout.list_item_converter_table_row, resultsList, false);
        ((LinearLayout)findViewById(R.id.tableHeader)).addView(header);
        for (int i=0; i < GradeSystem.printableValues().length; ++i) {
            final GradeSystem crSystem = GradeSystem.printableValues()[i];
            ((TableRow)header.findViewById(R.id.tableRow)).getChildAt(i).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (selectedHeader.size() == 2 || selectedHeader.contains(crSystem)) {
                        selectedHeader.remove(crSystem);
                    } else {
                        selectedHeader.add(crSystem);
                    }

                    updateHeader();
                    listAdapter.notifyDataSetChanged();
                }
            });
        }
        updateHeader();

        int selectedGrade = Configs.instance(this).getInt(Configs.ConfigKey.converterGradeValue);
        resultsList.setItemChecked(selectedGrade, true);
        resultsList.performItemClick(resultsList.getSelectedView(), selectedGrade, 0);
    }

    private void updateHeader () {
        TableRow row = header.findViewById(R.id.tableRow);

        for (int i=0; i < GradeSystem.printableValues().length; ++i) {
            final GradeSystem crSystem = GradeSystem.printableValues()[i];
            TextView element = (TextView) row.getChildAt(i);
            element.setText(GradeSystem.printableValues()[i].shortName);
            if (selectedHeader.size() == 2) {
                if (selectedHeader.contains(crSystem)) {
                    element.setVisibility(View.VISIBLE);
                    element.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SELECTED_TEXT_SIZE);
                } else {
                    element.setVisibility(View.GONE);
                }
            } else {
                element.setVisibility(View.VISIBLE);
                element.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE);
            }

            if (selectedHeader.contains(crSystem)) {
                element.setBackgroundColor(Color.parseColor("#eecccccc"));
            } else {
                element.setBackgroundColor(Color.parseColor("#eeFFFFFF"));
            }
        }
    }
}
