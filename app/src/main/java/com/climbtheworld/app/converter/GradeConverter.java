package com.climbtheworld.app.converter;

import android.support.annotation.LayoutRes;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.tools.GradeSystem;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import java.util.List;

public class GradeConverter extends ConverterFragment {

    private Spinner dropdownSystem;
    private Spinner dropdownGrade;
    private BaseAdapter listAdapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return GradeSystem.printableValues().length;
        }

        @Override
        public Object getItem(int i) {
            return GradeSystem.printableValues()[i];
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null) {
                view = inflater.inflate(R.layout.list_item_converter, viewGroup, false);
            }
            ((TextView)view.findViewById(R.id.unitValue)).setText(GradeSystem.printableValues()[i].getGrade(dropdownGrade.getSelectedItemPosition()));
            ((TextView)view.findViewById(R.id.itemTitle)).setText(parent.getString(GradeSystem.printableValues()[i].shortName));
            ((TextView)view.findViewById(R.id.itemDescription)).setText(parent.getString(GradeSystem.printableValues()[i].localeName));
            return view;
        }
    };
    private LayoutInflater inflater;

    public GradeConverter(AppCompatActivity parent, @LayoutRes int viewID) {
        super(parent, viewID);
    }

    @Override
    public void onCreate(ViewGroup view) {
        this.view = view;

        inflater = parent.getLayoutInflater();

        //route settings
        dropdownSystem = findViewById(R.id.gradeSystemSpinner);
        dropdownGrade = findViewById(R.id.gradeSelectSpinner);

        dropdownSystem.setOnItemSelectedListener(null);

        dropdownSystem.setAdapter(new GradeSystem.GradeSystemArrayAdapter(parent, android.R.layout.simple_spinner_dropdown_item, GradeSystem.printableValues()));

        dropdownSystem.setSelection(GradeSystem.systemToPrintableIndex(GradeSystem.fromString(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem))), false);
        dropdownSystem.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                buildGradeDropdown(GradeSystem.printableValues()[i]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        buildGradeDropdown(GradeSystem.printableValues()[dropdownSystem.getSelectedItemPosition()]);

        ListView resultsList = findViewById(R.id.listGradesConverter);
        resultsList.setAdapter(listAdapter);
    }

    private void buildGradeDropdown(GradeSystem system) {
        Spinner dropdownGrade = findViewById(R.id.gradeSelectSpinner);
        int selected = (dropdownGrade.getSelectedItemPosition()  >= 0 ? dropdownGrade.getSelectedItemPosition(): 0);
        List<String> allGrades = system.getPureAllGrades();
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
        dropdownGrade.setSelection(selected);
        dropdownGrade.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                findViewById(R.id.gradeSelectSpinnerBackground).setBackgroundColor(Globals.gradeToColorState(i).getDefaultColor());
                listAdapter.notifyDataSetChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    private void updateList() {

    }

    @Override
    public void onViewSelected() {

    }
}
