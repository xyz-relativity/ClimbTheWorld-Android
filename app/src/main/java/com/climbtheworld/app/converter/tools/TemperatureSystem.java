package com.climbtheworld.app.converter.tools;

import com.climbtheworld.app.R;

public enum TemperatureSystem implements UnitSystem {
	kelvin(R.string.unit_system_kelvin, R.string.unit_system_kelvin_short, R.string.unit_system_kelvin_description, UnitConverter.SI),
	celsius(R.string.unit_system_celsius, R.string.unit_system_celsius_short, R.string.unit_system_celsius_description,
			UnitConverter.of(value -> value + 273.15, value -> value - 273.15)),
	fahrenheit(R.string.unit_system_fahrenheit, R.string.unit_system_fahrenheit_short, R.string.unit_system_fahrenheit_description,
			UnitConverter.of(value -> (value + 459.67) / 1.8, value -> value * 1.8 - 459.67)),
	rankine(R.string.unit_system_rankine, R.string.unit_system_rankine_short, R.string.unit_system_rankine_description,
			UnitConverter.of(value -> value - 459.67, value -> value + 459.67)),
	delisle(R.string.unit_system_delisle, R.string.unit_system_delisle_short, R.string.unit_system_delisle_description,
			UnitConverter.of(value -> 373.15 - value * 2 / 3, value -> (373.15 - value) * 3 / 2)),
	newton(R.string.unit_system_newton, R.string.unit_system_newton_short, R.string.unit_system_newton_description,
			UnitConverter.of(value -> value * 100 / 33 + 273.15, value -> (value - 273.15) * 33 / 100));

	private final int localeName;
	private final int shortName;
	private final int description;
	private final UnitConverter converter;

	TemperatureSystem(int localeName, int shortName, int description, UnitConverter converter) {
		this.localeName = localeName;
		this.shortName = shortName;
		this.description = description;
		this.converter = converter;
	}

	@Override
	public int getLocaleName() {
		return localeName;
	}

	@Override
	public int getShortName() {
		return shortName;
	}

	@Override
	public int getDescription() {
		return description;
	}

	@Override
	public UnitConverter getConverter() {
		return converter;
	}
}
