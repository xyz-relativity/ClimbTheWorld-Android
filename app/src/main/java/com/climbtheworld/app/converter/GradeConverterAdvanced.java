package com.climbtheworld.app.converter;

import android.support.annotation.LayoutRes;
import android.support.v7.app.AppCompatActivity;
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

public class GradeConverterAdvanced extends ConverterFragment {

    int selectedGrade = 0;
    private Spinner dropdownSystem;
    private TextView textGrade;
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
        public View getView(int selected, View view, ViewGroup viewGroup) {
            if (view == null) {
                view = inflater.inflate(R.layout.list_item_converter_table_row, viewGroup, false);
            }

            TableRow row = view.findViewById(R.id.tableRow);
            int color = Globals.gradeToColorState(selected, 120).getDefaultColor();
//            row.setBackgroundColor(color);

            for (int i=0; i < GradeSystem.printableValues().length; ++i) {
                ((TextView)row.getChildAt(i)).setText(GradeSystem.printableValues()[i].getGrade(selected));
                ((TextView)row.getChildAt(i)).setBackgroundColor(color);
            }
            return view;
        }
    };
    private LayoutInflater inflater;
    private ListView resultsList;

    public GradeConverterAdvanced(AppCompatActivity parent, @LayoutRes int viewID) {
        super(parent, viewID);
    }

    @Override
    public void onCreate(ViewGroup view) {
        this.view = view;

        inflater = parent.getLayoutInflater();

        selectedGrade = Globals.globalConfigs.getInt(Configs.ConfigKey.converterGradeValue);

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
                textGrade.setText(GradeSystem.printableValues()[i].getGrade(selectedGrade));
                textGrade.setBackgroundColor(Globals.gradeToColorState(i).getDefaultColor());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        resultsList = findViewById(R.id.listGradesConverter);
        resultsList.setAdapter(listAdapter);

        View header = inflater.inflate(R.layout.list_item_converter_table_row, resultsList, false);
        TableRow row = header.findViewById(R.id.tableRow);

        for (int i=0; i < GradeSystem.printableValues().length; ++i) {
            ((TextView)row.getChildAt(i)).setText(GradeSystem.printableValues()[i].shortName);
        }

        ((LinearLayout)findViewById(R.id.tableHeader)).addView(header);

        resultsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                selectedGrade = i;
                Globals.globalConfigs.setInt(Configs.ConfigKey.converterGradeValue, selectedGrade);
                textGrade.setText(GradeSystem.printableValues()[dropdownSystem.getSelectedItemPosition()].getPureGrade(selectedGrade));
                textGrade.setBackgroundColor(Globals.gradeToColorState(selectedGrade).getDefaultColor());
            }
        });

        resultsList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                selectedGrade = i;
                Globals.globalConfigs.setInt(Configs.ConfigKey.converterGradeValue, selectedGrade);
                textGrade.setText(GradeSystem.printableValues()[dropdownSystem.getSelectedItemPosition()].getPureGrade(selectedGrade));
                textGrade.setBackgroundColor(Globals.gradeToColorState(selectedGrade).getDefaultColor());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        resultsList.setItemChecked(selectedGrade, true);
        resultsList.performItemClick(resultsList.getSelectedView(), selectedGrade, 0);
    }

    @Override
    public void onViewSelected() {

    }
}
