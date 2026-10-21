package com.climbtheworld.app.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.GradeConverter;
import com.climbtheworld.app.converter.UnitSystemConverter;
import com.climbtheworld.app.converter.tools.LengthSystem;
import com.climbtheworld.app.converter.tools.TemperatureSystem;
import com.climbtheworld.app.converter.tools.WeightSystem;
import com.climbtheworld.app.utils.views.EdgeToEdge;
import com.climbtheworld.app.utils.views.IPagerViewFragment;
import com.climbtheworld.app.utils.views.PagerViewAdapter;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class UnitsConverterActivity extends AppCompatActivity {
	private final List<IPagerViewFragment> views = new ArrayList<>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_units_converter);

		EdgeToEdge.padForSystemBars(findViewById(R.id.main));

		views.add(new GradeConverter(this, R.layout.fragment_units_converter_discrete_values));
		views.add(new UnitSystemConverter<>(this, R.layout.fragment_units_converter_continuous_values,
				LengthSystem.class, Configs.ConfigKey.converterLengthSystem));
		views.add(new UnitSystemConverter<>(this, R.layout.fragment_units_converter_continuous_values,
				WeightSystem.class, Configs.ConfigKey.converterWeightSystem));
		views.add(new UnitSystemConverter<>(this, R.layout.fragment_units_converter_continuous_values,
				TemperatureSystem.class, Configs.ConfigKey.converterTemperatureSystem));

		ViewPager viewPager = findViewById(R.id.converterContainerPager);
		viewPager.setAdapter(new PagerViewAdapter(getLayoutInflater(), views));

		BottomNavigationView navigation = findViewById(R.id.convertersNavigationBar);
		navigation.setItemIconTintList(null);
		PagerViewAdapter.linkNavigation(navigation, viewPager, views);
	}
}
