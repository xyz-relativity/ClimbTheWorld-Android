package com.climbtheworld.app.converter;

import android.support.annotation.LayoutRes;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.climbtheworld.app.tools.WeightSystem;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

public class WeightConverter extends ConverterFragment {
    private BaseAdapter listAdapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return WeightSystem.values().length;
        }

        @Override
        public Object getItem(int i) {
            return WeightSystem.values()[i];
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

            WeightSystem fromSystem = (WeightSystem)dropdownSystem.getSelectedItem();
            double value;
            String result = "";

            try {
                value = Double.parseDouble(inputValue.getText().toString());
                double converted = fromSystem.convertTo(WeightSystem.values()[i], value);
                if (converted > 1000000000 || converted < 0.00001) {
                    result = new DecimalFormat("##0.####E0").format(converted);
                } else {
                    result = new DecimalFormat("#,###,###,##0.####").format(converted);
                }
            } catch (NumberFormatException ignore) {

            }

            ((TextView)view.findViewById(R.id.unitValue)).setText(result);
            ((TextView)view.findViewById(R.id.systemValue)).setText(WeightSystem.values()[i].getLocaleName());
            return view;
        }
    };

    private LayoutInflater inflater;
    private Spinner dropdownSystem;
    private TextView inputValue;

    public WeightConverter(AppCompatActivity parent, @LayoutRes int viewID) {
        super(parent, viewID);
    }

    @Override
    public void onCreate(ViewGroup view) {
        this.view = view;
        inflater = parent.getLayoutInflater();

        dropdownSystem = findViewById(R.id.lengthSystemSpinner);

        List<WeightSystem> allGrades = Arrays.asList(WeightSystem.values());
        ArrayAdapter<WeightSystem> adapter = new ArrayAdapter<WeightSystem>(parent, android.R.layout.simple_spinner_dropdown_item, allGrades) {
            // Change color item
            @Override
            public View getDropDownView(int position, View convertView,
                                        ViewGroup itemParent) {
                View mView = super.getDropDownView(position, convertView, itemParent);
                TextView mTextView = (TextView) mView;
                return mView;
            }
        };

        dropdownSystem.setAdapter(adapter);
        dropdownSystem.setSelection(Globals.globalConfigs.getInt(Configs.ConfigKey.converterGradeValue));
        dropdownSystem.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                listAdapter.notifyDataSetChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        inputValue = findViewById(R.id.textLengthSelector);

        ListView resultsList = findViewById(R.id.listLengthConverter);
        resultsList.setAdapter(listAdapter);

        inputValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                listAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onViewSelected() {

    }
}
