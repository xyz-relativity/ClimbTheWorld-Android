package com.climbtheworld.app.converter.tools;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;

public enum TemperatureSystem {
	kelvin(R.string.unit_system_kelvin, R.string.unit_system_kelvin_short, R.string.unit_system_kelvin_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value;
		}

		@Override
		public double convertFromSI(double value) {
			return value;
		}
	}),
	celsius(R.string.unit_system_celsius, R.string.unit_system_celsius_short, R.string.unit_system_celsius_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value + 273.15;
		}

		@Override
		public double convertFromSI(double value) {
			return value - 273.15;
		}
	}),
	fahrenheit(R.string.unit_system_fahrenheit, R.string.unit_system_fahrenheit_short, R.string.unit_system_fahrenheit_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return (value + 459.67)/1.8;
		}

		@Override
		public double convertFromSI(double value) {
			return value * 1.8 - 459.67;
		}
	}),
	rankine(R.string.unit_system_rankine, R.string.unit_system_rankine_short, R.string.unit_system_rankine_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value - 459.67;
		}

		@Override
		public double convertFromSI(double value) {
			return value + 459.67;
		}
	});

	TemperatureSystem(int localeName, int shortName, int description, UnitConverter converter) {
		this.localeName = localeName;
		this.shortName = shortName;
		this.description = description;
		this.converter = converter;
	}

	private final int localeName;
	private final int shortName;
	private final int description;
	private final UnitConverter converter;

	public static TemperatureSystem fromString(String value) {
		TemperatureSystem result = TemperatureSystem.valueOf(Configs.ConfigKey.converterTemperatureSystem.defaultVal.toString());
		for (TemperatureSystem entry : values()) {
			if (entry.name().equalsIgnoreCase(value)) {
				result = entry;
				break;
			}
		}
		return result;
	}


	public int getLocaleName() {
		return localeName;
	}

	public int getShortName() {
		return shortName;
	}

	public int getDescription() {
		return description;
	}

	public UnitConverter getConverter() {
		return converter;
	}

	public double convertTo(TemperatureSystem toSystem, double value) {
		return toSystem.getConverter().convertFromSI(getConverter().convertToSI(value));
	}
}
