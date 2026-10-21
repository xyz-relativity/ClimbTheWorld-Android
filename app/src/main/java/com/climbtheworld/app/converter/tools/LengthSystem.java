package com.climbtheworld.app.converter.tools;

import com.climbtheworld.app.R;

public enum LengthSystem implements UnitSystem {
	meter(R.string.unit_system_meter, R.string.unit_system_meter_short, R.string.unit_system_meter_description, UnitConverter.SI),
	mile(R.string.unit_system_mile, R.string.unit_system_mile_short, R.string.unit_system_mile_description, UnitConverter.scaled(1609.344)),
	yard(R.string.unit_system_yard, R.string.unit_system_yard_short, R.string.unit_system_yard_description, UnitConverter.scaled(0.9144)),
	feet(R.string.unit_system_feet, R.string.unit_system_feet_short, R.string.unit_system_feet_description, UnitConverter.scaled(0.3048)),
	inch(R.string.unit_system_inch, R.string.unit_system_inch_short, R.string.unit_system_inch_description, UnitConverter.scaled(0.0254)),
	nauticalMile(R.string.unit_system_nautical_mile, R.string.unit_system_nautical_mile_short, R.string.unit_system_meter, UnitConverter.scaled(1852.0)),
	furlong(R.string.unit_system_furlong, R.string.unit_system_furlong_short, R.string.unit_system_furlong_description, UnitConverter.scaled(201.168)),
	lightYear(R.string.unit_system_lightyear, R.string.unit_system_lightyear_short, R.string.unit_system_lightyear_description, UnitConverter.scaled(9460730472580800.0));

	private final int localeName;
	private final int shortName;
	private final int description;
	private final UnitConverter converter;

	LengthSystem(int localeName, int shortName, int description, UnitConverter converter) {
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
