package com.climbtheworld.app.configs;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.utils.constants.UIConstants;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;
import com.climbtheworld.app.utils.views.SpinnerUtils;
import com.climbtheworld.app.utils.views.StyleSwitches;
import com.climbtheworld.app.utils.views.TextWatchers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class DisplayFilterFragment extends ConfigFragment implements AdapterView.OnItemSelectedListener {
	private final Configs configs;
	private Spinner minSpinner;
	private Spinner maxSpinner;
	private EditText testFilter;

	public DisplayFilterFragment(AppCompatActivity parent, View view) {
		super(parent, view);
		configs = Configs.instance(parent);
		uiSetup();
	}

	private void uiSetup() {
		//route display filters
		minSpinner = findViewById(R.id.gradeFilterSpinnerMin);
		maxSpinner = findViewById(R.id.gradeFilterSpinnerMax);

		testFilter = findViewById(R.id.editFind);

		if (testFilter != null) {
			testFilter.setText(configs.getString(Configs.ConfigKey.filterString));
			testFilter.addTextChangedListener(TextWatchers.afterTextChanged(editable ->
					configs.setString(Configs.ConfigKey.filterString, editable.toString().toLowerCase())));
		}

		updateGradeSystemText();

		SpinnerUtils.updateLinkedGradeSpinners(parent,
				minSpinner,
				configs.getInt(Configs.ConfigKey.filterMinGrade),
				maxSpinner,
				configs.getInt(Configs.ConfigKey.filterMaxGrade),
				true);

		maxSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
				configs.setInt(Configs.ConfigKey.filterMaxGrade, SpinnerUtils.getGradeID(maxSpinner, true));
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
				configs.setInt(Configs.ConfigKey.filterMinGrade, SpinnerUtils.getGradeID(minSpinner, true));
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
		StyleSwitches.add(parent, findViewById(R.id.containerClimbingStyles),
				configs.getClimbingStyles(), (buttonView, isChecked) -> saveStyles());
	}

	private void saveStyles() {
		configs.setClimbingStyles(new TreeSet<>(
				StyleSwitches.checked(findViewById(R.id.containerClimbingStyles))));
	}

	private void loadNodeTypes() {
		Set<OsmEntity.EntityClimbingType> checked = configs.getNodeTypes();

		ViewGroup container = findViewById(R.id.containerClimbingTypes);

		for (OsmEntity.EntityClimbingType typeName : OsmEntity.EntityClimbingType.features()) {
			GeoNode poi = new GeoNode(0, 0, 0);
			poi.setClimbingType(typeName);

			View customSwitch = ListViewItemBuilder.getPaddedBuilder(parent)
					.setTitle(parent.getString(typeName.getNameId()))
					.setDescription(parent.getString(typeName.getDescriptionId()))
					.setSwitchChecked(checked.contains(typeName))
					.setIcon(new PoiMarkerDrawable(parent, new DisplayableGeoNode(poi)))
					.setIconSize(UIConstants.POI_TYPE_LIST_ICON_SIZE, UIConstants.POI_TYPE_LIST_ICON_SIZE)
					.changeElementId(R.id.switchTypeEnabled, typeName.getNameId())
					.setSwitchEvent(new CompoundButton.OnCheckedChangeListener() {
						@Override
						public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
							saveTypes();
						}
					})
					.build();

			container.addView(customSwitch);
		}
	}

	private void saveTypes() {
		Set<OsmEntity.EntityClimbingType> styles = new TreeSet<>();
		for (OsmEntity.EntityClimbingType style : OsmEntity.EntityClimbingType.features()) {
			SwitchCompat styleCheckBox = findViewById(style.getNameId());
			if (styleCheckBox != null && styleCheckBox.isChecked()) {
				styles.add(style);
			}
		}

		configs.setNodeTypes(styles);
	}

	private void updateGradeSystemText() {
		((TextView) findViewById(R.id.filterMinGradeText)).setText(parent.getResources().getString(R.string.min_grade,
				parent.getResources().getString(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
		((TextView) findViewById(R.id.filterMaxGradeText)).setText(parent.getResources().getString(R.string.max_grade,
				parent.getResources().getString(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
	}

	@Override
	public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
		if (parentView.getId() == R.id.gradeSelectSpinner) {
			configs.setString(Configs.ConfigKey.usedGradeSystem, GradeSystem.printableValues()[position].getMainKey());
			SpinnerUtils.updateLinkedGradeSpinners(parent, minSpinner, configs.getInt(Configs.ConfigKey.filterMinGrade), maxSpinner, configs.getInt(Configs.ConfigKey.filterMaxGrade), true);
			updateGradeSystemText();
		}
	}

	public void done() {
		notifyListeners();
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {
	}

	public void reset() {
		configs.setNodeTypes(new HashSet<>(Arrays.asList((OsmEntity.EntityClimbingType[]) Configs.ConfigKey.filterNodeTypes.defaultVal)));
		configs.setClimbingStyles(new HashSet<>(Arrays.asList((GeoNode.ClimbingStyle[]) Configs.ConfigKey.filterStyles.defaultVal)));
		configs.setInt(Configs.ConfigKey.filterMinGrade, (int) Configs.ConfigKey.filterMinGrade.defaultVal);
		configs.setInt(Configs.ConfigKey.filterMaxGrade, (int) Configs.ConfigKey.filterMaxGrade.defaultVal);
		configs.setString(Configs.ConfigKey.filterString, (String) Configs.ConfigKey.filterString.defaultVal);

		notifyListeners();
	}
}
