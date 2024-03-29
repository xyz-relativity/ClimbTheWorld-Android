package com.climbtheworld.app.converter.tools;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;

public enum LengthSystem {
	meter(R.string.unit_system_meter, R.string.unit_system_meter_short, R.string.unit_system_meter_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value;
		}

		@Override
		public double convertFromSI(double value) {
			return value;
		}
	}),
	mile(R.string.unit_system_mile, R.string.unit_system_mile_short, R.string.unit_system_mile_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value * 1609.344;
		}

		@Override
		public double convertFromSI(double value) {
			return value * 0.00062137119223733397;
		}
	}),
	yard(R.string.unit_system_yard, R.string.unit_system_yard_short, R.string.unit_system_yard_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value * 0.9144;
		}

		@Override
		public double convertFromSI(double value) {
			return value * 1.09361329833770779;
		}
	}),
	feet(R.string.unit_system_feet, R.string.unit_system_feet_short, R.string.unit_system_feet_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value * 0.3048;
		}

		@Override
		public double convertFromSI(double value) {
			return value * 3.28083989501312336;
		}
	}),
	inch(R.string.unit_system_inch, R.string.unit_system_inch_short, R.string.unit_system_inch_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value * 0.0254;
		}

		@Override
		public double convertFromSI(double value) {
			return value * 39.3700787401574803;
		}
	}),
	nauticalMile(R.string.unit_system_nautical_mile, R.string.unit_system_nautical_mile_short, R.string.unit_system_meter, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value * 1852.0;
		}

		@Override
		public double convertFromSI(double value) {
			return value * 0.000539956803455723542;
		}
	}),
	furlong(R.string.unit_system_furlong, R.string.unit_system_furlong_short, R.string.unit_system_furlong_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value * 201.168;
		}

		@Override
		public double convertFromSI(double value) {
			return value * 0.0049709695379;
		}
	}),
	lightYear(R.string.unit_system_lightyear, R.string.unit_system_lightyear_short, R.string.unit_system_lightyear_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value * 9460730472580800.0;
		}

		@Override
		public double convertFromSI(double value) {
			return value * 0.0000000000000001057000834024615463709;
		}
	});

	LengthSystem(int localeName, int shortName, int description, UnitConverter converter) {
		this.localeName = localeName;
		this.shortName = shortName;
		this.description = description;
		this.converter = converter;
	}

	private final int localeName;
	private final int shortName;
	private final int description;
	private final UnitConverter converter;

	public static LengthSystem fromString(String value) {
		LengthSystem result = LengthSystem.valueOf(Configs.ConfigKey.converterLengthSystem.defaultVal.toString());
		for (LengthSystem entry : values()) {
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

	public double convertTo(LengthSystem toSystem, double value) {
		return toSystem.getConverter().convertFromSI(getConverter().convertToSI(value));
	}
}
