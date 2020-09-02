package com.climbtheworld.app.tutorial;

import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;

import androidx.appcompat.app.AppCompatActivity;

public class RoutesSettingsFragment extends TutorialFragment implements AdapterView.OnItemSelectedListener {

    private Configs configs;

    public RoutesSettingsFragment(AppCompatActivity parent, int viewID) {
        super(parent, viewID);
    }

    @Override
    public void onCreate(ViewGroup view) {
        configs = Configs.instance(parent);
        InputMethodManager imm = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

        ((TextView)view.findViewById(R.id.fragmentText))
                .setText(Html.fromHtml(parent.getResources().getString(R.string.tutorial_routes_setup_message)));
        ((TextView)view.findViewById(R.id.fragmentText)).setMovementMethod(LinkMovementMethod.getInstance());

        //route settings
        Spinner dropdown = view.findViewById(R.id.gradeSelectSpinner);
        dropdown.setOnItemSelectedListener(null);
        dropdown.setAdapter(new GradeSystem.GradeSystemArrayAdapter(parent, android.R.layout.simple_spinner_dropdown_item, GradeSystem.printableValues()));
        dropdown.setSelection(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).ordinal(), false);
        dropdown.setOnItemSelectedListener(this);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        switch (parent.getId()) {
            case R.id.gradeSelectSpinner:
                configs.setString(Configs.ConfigKey.usedGradeSystem, GradeSystem.printableValues()[position].getMainKey());
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}
