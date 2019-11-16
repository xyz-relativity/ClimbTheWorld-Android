package com.climbtheworld.app.filter;

import android.support.annotation.IdRes;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeSystem;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.SpinnerUtils;
import com.climbtheworld.app.utils.ViewUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class FilterFragment implements SeekBar.OnSeekBarChangeListener, AdapterView.OnItemSelectedListener {
    private final AppCompatActivity parent;
    private View view;

    private Spinner minSpinner;
    private Spinner maxSpinner;
    private int countMultiplier;
    private int distanceMultiplier;
    private List<OnFilterChangeListener> listenerList = new LinkedList<>();

    public interface OnFilterChangeListener {
        void onFilterChange();
    }

    private <T extends View> T findViewById(@IdRes int id){
        return view.findViewById(id);
    }

    private void notifyListeners() {
        for (OnFilterChangeListener listener: listenerList) {
            listener.onFilterChange();
        }
    }

    public FilterFragment (AppCompatActivity parent, View view) {
        this.parent = parent;
        this.view = view;

        countMultiplier = ((int)Configs.ConfigKey.maxNodesShowCountLimit.maxValue) / 10;
        distanceMultiplier = ((int)Configs.ConfigKey.maxNodesShowDistanceLimit.maxValue) / 10;

        uiSetup();
    }

    public void addListener(OnFilterChangeListener listener) {
        listenerList.add(listener);
    }

    private void uiSetup() {
        //route display filters
        ((SeekBar)findViewById(R.id.maxViewCountSeek)).setMax((int)Configs.ConfigKey.maxNodesShowCountLimit.maxValue / countMultiplier);
        ((SeekBar)findViewById(R.id.maxViewCountSeek)).setProgress(Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowCountLimit) / countMultiplier);
        ((SeekBar)findViewById(R.id.maxViewCountSeek)).setOnSeekBarChangeListener(this);
        ((TextView)findViewById(R.id.maxViewCountValue)).setText(String.valueOf(Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowCountLimit)));

        ((SeekBar)findViewById(R.id.maxViewDistanceSeek)).setMax((int)Configs.ConfigKey.maxNodesShowDistanceLimit.maxValue / distanceMultiplier);
        ((SeekBar)findViewById(R.id.maxViewDistanceSeek)).setProgress(Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit) / distanceMultiplier);
        ((SeekBar)findViewById(R.id.maxViewDistanceSeek)).setOnSeekBarChangeListener(this);
        ((TextView)findViewById(R.id.maxViewDistanceValue)).setText(String.valueOf(Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit)));

        minSpinner = findViewById(R.id.gradeFilterSpinnerMin);
        maxSpinner = findViewById(R.id.gradeFilterSpinnerMax);

        SpinnerUtils.updateLinkedGradeSpinners(parent,
                minSpinner,
                Globals.globalConfigs.getInt(Configs.ConfigKey.filterMinGrade),
                maxSpinner,
                Globals.globalConfigs.getInt(Configs.ConfigKey.filterMaxGrade),
                true, false);

        maxSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                Globals.globalConfigs.setInt(Configs.ConfigKey.filterMaxGrade, i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        minSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                Globals.globalConfigs.setInt(Configs.ConfigKey.filterMinGrade, i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

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
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            if (seekBar.getId() == R.id.maxViewCountSeek) {
                Globals.globalConfigs.setInt(Configs.ConfigKey.maxNodesShowCountLimit, progress * countMultiplier);
                ((TextView)findViewById(R.id.maxViewCountValue)).setText(String.valueOf(progress * countMultiplier));
            }

            if (seekBar.getId() == R.id.maxViewDistanceSeek) {
                Globals.globalConfigs.setInt(Configs.ConfigKey.maxNodesShowDistanceLimit, progress * distanceMultiplier);
                ((TextView)findViewById(R.id.maxViewDistanceValue)).setText(String.valueOf(progress * distanceMultiplier));
            }

            notifyListeners();
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

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
