package com.climbtheworld.app.converter;

import android.graphics.Color;
import android.support.annotation.LayoutRes;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TableRow;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.tools.GradeSystem;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import java.util.HashSet;
import java.util.Set;

public class GradeConverterAdvanced extends ConverterFragment {

    private static final int TABLE_ALPHA = 120;
    private static final int TEXT_SIZE = 12;
    private static final int SELECTED_TEXT_SIZE = 24;

    private Spinner dropdownSystem;
    private TextView textGrade;
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

    public GradeConverterAdvanced(AppCompatActivity parent, @LayoutRes int viewID) {
        super(parent, viewID);
    }

    @Override
    public void onCreate(ViewGroup view) {
        this.view = view;

        inflater = parent.getLayoutInflater();

        dropdownSystem = findViewById(R.id.gradeSystemSpinner);
        textGrade = findViewById(R.id.gradeConvertedText);

        dropdownSystem.setOnItemSelectedListener(null);
        dropdownSystem.setAdapter(new GradeSystem.GradeSystemArrayAdapter(parent, android.R.layout.simple_spinner_dropdown_item, GradeSystem.printableValues()));

        dropdownSystem.setSelection(GradeSystem.systemToPrintableIndex(GradeSystem.fromString(Globals.globalConfigs.getString(Configs.ConfigKey.converterGradeSystem))), false);
        ((TextView)findViewById(R.id.gradingSelectLabel)).setText(parent.getResources().getString(R.string.grade_system,
                parent.getResources().getString(GradeSystem.fromString(Globals.globalConfigs.getString(Configs.ConfigKey.converterGradeSystem)).shortName)));
        dropdownSystem.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                Globals.globalConfigs.setString(Configs.ConfigKey.converterGradeSystem, GradeSystem.printableValues()[i].getMainKey());
                ((TextView)findViewById(R.id.gradingSelectLabel)).setText(parent.getResources().getString(R.string.grade_system,
                        parent.getResources().getString(GradeSystem.printableValues()[i].shortName)));
                int selectedGrade = Globals.globalConfigs.getInt(Configs.ConfigKey.converterGradeValue);
                textGrade.setText(GradeSystem.printableValues()[i].getGrade(selectedGrade));
                textGrade.setBackgroundColor(Globals.gradeToColorState(i).getDefaultColor());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        resultsList = findViewById(R.id.listGradesConverter);
        resultsList.setAdapter(listAdapter);

        header = inflater.inflate(R.layout.list_item_converter_table_row, resultsList, false);
        ((LinearLayout)findViewById(R.id.tableHeader)).addView(header);
        updateHeader();

        resultsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int selected, long l) {
                Globals.globalConfigs.setInt(Configs.ConfigKey.converterGradeValue, selected);
                textGrade.setText(GradeSystem.printableValues()[dropdownSystem.getSelectedItemPosition()].getPureGrade(selected));
                textGrade.setBackgroundColor(Globals.gradeToColorState(selected).getDefaultColor());
            }
        });

        resultsList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int selected, long l) {
                Globals.globalConfigs.setInt(Configs.ConfigKey.converterGradeValue, selected);
                textGrade.setText(GradeSystem.printableValues()[dropdownSystem.getSelectedItemPosition()].getPureGrade(selected));
                textGrade.setBackgroundColor(Globals.gradeToColorState(selected).getDefaultColor());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        int selectedGrade = Globals.globalConfigs.getInt(Configs.ConfigKey.converterGradeValue);
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

            element.setOnClickListener(new View.OnClickListener() {
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
    }

    @Override
    public void onViewSelected() {
    }
}
