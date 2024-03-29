package com.climbtheworld.app.configs;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.constants.UIConstants;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;
import com.climbtheworld.app.utils.views.Sorters;
import com.climbtheworld.app.utils.views.SpinnerUtils;

import java.util.Arrays;
import java.util.Collections;
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
			testFilter.addTextChangedListener(new TextWatcher() {
				final Handler handler = new Handler(Looper.getMainLooper() /*UI thread*/);
				Runnable workRunnable;

				@Override
				public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				}

				@Override
				public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				}

				@Override
				public void afterTextChanged(Editable editable) {
					configs.setString(Configs.ConfigKey.filterString, editable.toString().toLowerCase());
				}
			});
		}

		updateGradeSystemText();

		SpinnerUtils.updateLinkedGradeSpinners(parent,
				minSpinner,
				configs.getInt(Configs.ConfigKey.filterMinGrade),
				maxSpinner,
				configs.getInt(Configs.ConfigKey.filterMaxGrade),
				true, true);

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
		Set<GeoNode.ClimbingStyle> checked = configs.getClimbingStyles();

		ViewGroup container = findViewById(R.id.containerClimbingStyles);

		for (GeoNode.ClimbingStyle styleName : Sorters.sortStyles(parent, GeoNode.ClimbingStyle.values())) {
			View customSwitch = ListViewItemBuilder.getPaddedBuilder(parent)
					.setTitle(parent.getString(styleName.getNameId()))
					.setDescription(parent.getString(styleName.getDescriptionId()))
					.setSwitchChecked(checked.contains(styleName))
					.setIcon(MarkerUtils.getStyleIcon(parent, Collections.singletonList(styleName)))
					.changeElementId(R.id.switchTypeEnabled, styleName.getNameId())
					.setSwitchEvent(new CompoundButton.OnCheckedChangeListener() {
						@Override
						public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
							saveStyles();
						}
					})
					.build();

			container.addView(customSwitch);
		}
	}

	private void saveStyles() {
		Set<GeoNode.ClimbingStyle> styles = new TreeSet<>();
		for (GeoNode.ClimbingStyle style : GeoNode.ClimbingStyle.values()) {
			SwitchCompat styleCheckBox = findViewById(style.getNameId());
			if (styleCheckBox != null && styleCheckBox.isChecked()) {
				styles.add(style);
			}
		}

		configs.setClimbingStyles(styles);
	}

	private void loadNodeTypes() {
		Set<GeoNode.NodeTypes> checked = configs.getNodeTypes();

		ViewGroup container = findViewById(R.id.containerClimbingTypes);

		for (GeoNode.NodeTypes typeName : GeoNode.NodeTypes.values()) {
			GeoNode poi = new GeoNode(0, 0, 0);
			poi.setClimbingType(typeName);

			View customSwitch = ListViewItemBuilder.getPaddedBuilder(parent)
					.setTitle(parent.getString(typeName.getNameId()))
					.setDescription(parent.getString(typeName.getDescriptionId()))
					.setSwitchChecked(checked.contains(typeName))
					.setIcon(new PoiMarkerDrawable(parent, null, new DisplayableGeoNode(poi), 0, 0))
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
		Set<GeoNode.NodeTypes> styles = new TreeSet<>();
		for (GeoNode.NodeTypes style : GeoNode.NodeTypes.values()) {
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
		switch (parentView.getId()) {
			case R.id.gradeSelectSpinner:
				configs.setString(Configs.ConfigKey.usedGradeSystem, GradeSystem.printableValues()[position].getMainKey());
				SpinnerUtils.updateLinkedGradeSpinners(parent, minSpinner, configs.getInt(Configs.ConfigKey.filterMinGrade), maxSpinner, configs.getInt(Configs.ConfigKey.filterMaxGrade), true, false);
				updateGradeSystemText();
				break;
		}
	}

	public void done() {
		notifyListeners();
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {
	}

	public void reset() {
		configs.setNodeTypes(new HashSet<>(Arrays.asList((GeoNode.NodeTypes[]) Configs.ConfigKey.filterNodeTypes.defaultVal)));
		configs.setClimbingStyles(new HashSet<>(Arrays.asList((GeoNode.ClimbingStyle[]) Configs.ConfigKey.filterStyles.defaultVal)));
		configs.setInt(Configs.ConfigKey.filterMinGrade, (int) Configs.ConfigKey.filterMinGrade.defaultVal);
		configs.setInt(Configs.ConfigKey.filterMaxGrade, (int) Configs.ConfigKey.filterMaxGrade.defaultVal);
		configs.setString(Configs.ConfigKey.filterString, (String) Configs.ConfigKey.filterString.defaultVal);

		notifyListeners();
	}
}
