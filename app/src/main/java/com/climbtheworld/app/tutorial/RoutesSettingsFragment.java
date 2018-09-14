package com.climbtheworld.app.tutorial;

import android.app.Activity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.tools.GradeConverter;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import java.util.List;

public class RoutesSettingsFragment extends TutorialFragment implements CompoundButton.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {

    public RoutesSettingsFragment(Activity parent, int viewID) {
        super(parent, viewID);
    }

    @Override
    public void onCreate(ViewGroup view) {
        ((TextView)view.findViewById(R.id.fragmentText))
                .setText(Html.fromHtml(parent.getResources().getString(R.string.tutorial_routes_setup_message)));
        ((TextView)view.findViewById(R.id.fragmentText)).setMovementMethod(LinkMovementMethod.getInstance());

        ((Switch)view.findViewById(R.id.virtualHorizonSwitch)).setChecked(Globals.globalConfigs.getBoolean(Configs.ConfigKey.showVirtualHorizon));
        ((Switch)view.findViewById(R.id.virtualHorizonSwitch)).setOnCheckedChangeListener(this);

        //route settings
        Spinner dropdown = view.findViewById(R.id.gradeSpinner);
        dropdown.setOnItemSelectedListener(null);
        List<String> allGrades = GradeConverter.getConverter().cleanSystems;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(parent, android.R.layout.simple_spinner_dropdown_item, allGrades);
        dropdown.setAdapter(adapter);
        dropdown.setSelection(allGrades.indexOf(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)), false);
        dropdown.setOnItemSelectedListener(this);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == R.id.virtualHorizonSwitch) {
            Globals.globalConfigs.setBoolean(Configs.ConfigKey.showVirtualHorizon, isChecked);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        switch (parent.getId()) {
            case R.id.gradeSpinner:
                Globals.globalConfigs.setString(Configs.ConfigKey.usedGradeSystem, GradeConverter.getConverter().cleanSystems.get(position));
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}
