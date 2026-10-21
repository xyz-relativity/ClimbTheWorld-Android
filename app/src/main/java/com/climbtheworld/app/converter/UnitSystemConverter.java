package com.climbtheworld.app.converter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.UnitSystem;
import com.climbtheworld.app.utils.views.SpinnerUtils;
import com.climbtheworld.app.utils.views.TextWatchers;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * The converter page of one kind of unit: the value typed in the unit picked, in every other.
 *
 * @param <T> the units of the kind, like {@link com.climbtheworld.app.converter.tools.LengthSystem}
 */
public class UnitSystemConverter<T extends Enum<T> & UnitSystem> extends ConverterFragment {
	private static final double SCIENTIFIC_NOTATION_ABOVE = 1000000000;
	private static final double SCIENTIFIC_NOTATION_BELOW = 0.00001;

	private final Class<T> type;
	private final T[] units;
	/** Where the unit picked is kept between runs. */
	private final Configs.ConfigKey unitKey;

	private final BaseAdapter listAdapter = new BaseAdapter() {
		@Override
		public int getCount() {
			return units.length;
		}

		@Override
		public Object getItem(int i) {
			return units[i];
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

			T fromSystem = units[dropdownSystem.getSelectedItemPosition()];
			String result = "";
			try {
				double value = Double.parseDouble(inputValue.getText().toString());
				double converted = fromSystem.convertTo(units[i], value);
				if (Math.abs(converted) > SCIENTIFIC_NOTATION_ABOVE
						|| Math.abs(converted) < SCIENTIFIC_NOTATION_BELOW) {
					result = new DecimalFormat("##0.####E0").format(converted);
				} else {
					result = new DecimalFormat("#,###,###,##0.####").format(converted);
				}
			} catch (NumberFormatException ignore) {
				// Nothing, or no number, typed yet.
			}

			((TextView) view.findViewById(R.id.unitValue)).setText(result);
			((TextView) view.findViewById(R.id.systemValue)).setText(units[i].getLocaleName());
			return view;
		}
	};

	private LayoutInflater inflater;
	private Spinner dropdownSystem;
	private TextView inputValue;

	public UnitSystemConverter(AppCompatActivity parent, @LayoutRes int viewID, Class<T> type,
	                           Configs.ConfigKey unitKey) {
		super(parent, viewID);
		this.type = type;
		this.units = type.getEnumConstants();
		this.unitKey = unitKey;
	}

	@Override
	public void onCreate(ViewGroup view) {
		this.view = view;
		inflater = parent.getLayoutInflater();

		dropdownSystem = findViewById(R.id.lengthSystemSpinner);

		List<String> unitNames = new ArrayList<>();
		for (T unit : units) {
			unitNames.add(parent.getResources().getString(unit.getLocaleName()));
		}
		dropdownSystem.setAdapter(new ArrayAdapter<>(parent,
				android.R.layout.simple_spinner_dropdown_item, unitNames));
		dropdownSystem.setSelection(
				UnitSystem.fromString(type, configs.getString(unitKey), unitKey).ordinal(), false);
		dropdownSystem.setOnItemSelectedListener(SpinnerUtils.onItemSelected(position -> {
			configs.setString(unitKey, units[position].name());
			listAdapter.notifyDataSetChanged();
		}));

		inputValue = findViewById(R.id.textLengthSelector);
		inputValue.addTextChangedListener(
				TextWatchers.afterTextChanged(editable -> listAdapter.notifyDataSetChanged()));

		ListView resultsList = findViewById(R.id.listLengthConverter);
		resultsList.setAdapter(listAdapter);
	}

	@Override
	public void onViewSelected() {
		inputValue.requestFocus();
		showKeyboard(inputValue);
	}
}
