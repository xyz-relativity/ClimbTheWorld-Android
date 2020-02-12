package com.climbtheworld.app.configs;

import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeSystem;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.SpinnerUtils;
import com.climbtheworld.app.utils.ViewUtils;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class DisplayFilterFragment extends ConfigFragment implements AdapterView.OnItemSelectedListener {
    private Spinner minSpinner;
    private Spinner maxSpinner;

    public DisplayFilterFragment(AppCompatActivity parent, View view) {
        super(parent, view);
        uiSetup();
    }

    private void uiSetup() {
        //route display filters
        minSpinner = findViewById(R.id.gradeFilterSpinnerMin);
        maxSpinner = findViewById(R.id.gradeFilterSpinnerMax);

        ((TextView)findViewById(R.id.filterMinGradeText)).setText(parent.getResources().getString(R.string.min_grade,
                parent.getResources().getString(GradeSystem.fromString(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
        ((TextView)findViewById(R.id.filterMaxGradeText)).setText(parent.getResources().getString(R.string.max_grade,
                parent.getResources().getString(GradeSystem.fromString(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));

        SpinnerUtils.updateLinkedGradeSpinners(parent,
                minSpinner,
                Globals.globalConfigs.getInt(Configs.ConfigKey.filterMinGrade),
                maxSpinner,
                Globals.globalConfigs.getInt(Configs.ConfigKey.filterMaxGrade),
                true, true);

        maxSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                Globals.globalConfigs.setInt(Configs.ConfigKey.filterMaxGrade, SpinnerUtils.getGradeID(maxSpinner, true));
                notifyListeners();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                notifyListeners();
            }
        });

        minSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                Globals.globalConfigs.setInt(Configs.ConfigKey.filterMinGrade, SpinnerUtils.getGradeID(minSpinner, true));
                notifyListeners();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                notifyListeners();
            }
        });

        loadStyles();
        loadNodeTypes();
    }

    private void loadStyles() {
        Map<String, GeoNode.ClimbingStyle> climbStyle = new TreeMap<>();
        for (GeoNode.ClimbingStyle style: GeoNode.ClimbingStyle.values())
        {
            climbStyle.put(style.name(), style);
        }

        Set<GeoNode.ClimbingStyle> checked = Globals.globalConfigs.getClimbingStyles();

        RadioGroup container = findViewById(R.id.radioGroupStyles);

        for (GeoNode.ClimbingStyle styleName: climbStyle.values())
        {
            View customSwitch = ViewUtils.buildCustomSwitch(parent, styleName.getNameId(), styleName.getDescriptionId(), checked.contains(styleName), null);
            Switch styleCheckBox = customSwitch.findViewById(R.id.switchTypeEnabled);
            styleCheckBox.setId(styleName.getNameId());

            styleCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    saveStyles();
                }
            });

            container.addView(customSwitch);
        }
    }

    private void saveStyles() {
        Set<GeoNode.ClimbingStyle> styles = new TreeSet<>();
        for (GeoNode.ClimbingStyle style: GeoNode.ClimbingStyle.values())
        {
            Switch styleCheckBox = findViewById(style.getNameId());
            if (styleCheckBox != null && styleCheckBox.isChecked()) {
                styles.add(style);
            }
        }

        Globals.globalConfigs.setClimbingStyles(styles);

        notifyListeners();
    }

    private void loadNodeTypes() {
        Map<String, GeoNode.NodeTypes> climbStyle = new TreeMap<>();
        for (GeoNode.NodeTypes style: GeoNode.NodeTypes.values())
        {
            climbStyle.put(style.name(), style);
        }

        Set<GeoNode.NodeTypes> checked = Globals.globalConfigs.getNodeTypes();

        RadioGroup container = findViewById(R.id.radioGroupTypes);

        for (GeoNode.NodeTypes styleName: climbStyle.values())
        {
            View customSwitch = ViewUtils.buildCustomSwitch(parent, styleName.getNameId(), styleName.getDescriptionId(), checked.contains(styleName), null);
            Switch styleCheckBox = customSwitch.findViewById(R.id.switchTypeEnabled);
            styleCheckBox.setId(styleName.getNameId());

            styleCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    saveTypes();
                }
            });

            container.addView(customSwitch);
        }
    }

    private void saveTypes() {
        Set<GeoNode.NodeTypes> styles = new TreeSet<>();
        for (GeoNode.NodeTypes style: GeoNode.NodeTypes.values())
        {
            Switch styleCheckBox = findViewById(style.getNameId());
            if (styleCheckBox != null && styleCheckBox.isChecked()) {
                styles.add(style);
            }
        }

        Globals.globalConfigs.setNodeTypes(styles);

        notifyListeners();
    }

    @Override
    public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
        switch (parentView.getId()) {
            case R.id.gradeSelectSpinner:
                Globals.globalConfigs.setString(Configs.ConfigKey.usedGradeSystem, GradeSystem.printableValues()[position].getMainKey());
                SpinnerUtils.updateLinkedGradeSpinners(parent, minSpinner, Globals.globalConfigs.getInt(Configs.ConfigKey.filterMinGrade), maxSpinner, Globals.globalConfigs.getInt(Configs.ConfigKey.filterMaxGrade), true, false);
                break;
            case R.id.gradeFilterSpinnerMax:
                Globals.globalConfigs.setInt(Configs.ConfigKey.filterMaxGrade, position);
                break;
        }

        notifyListeners();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}
