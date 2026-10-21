package com.climbtheworld.app.converter.tools;

import com.climbtheworld.app.R;

public enum WeightSystem implements UnitSystem {
	kiloGram(R.string.unit_system_kilogram, R.string.unit_system_kilogram_short, R.string.unit_system_kilogram_description, UnitConverter.SI),
	pound(R.string.unit_system_pound, R.string.unit_system_pound_short, R.string.unit_system_pound_description, UnitConverter.scaled(0.45359237)),
	ounce(R.string.unit_system_ounce, R.string.unit_system_ounce_short, R.string.unit_system_ounce_description, UnitConverter.scaled(0.028349523125)),
	grain(R.string.unit_system_grain, R.string.unit_system_grain_short, R.string.unit_system_grain_description, UnitConverter.scaled(0.00006479891)),
	stone(R.string.unit_system_stone, R.string.unit_system_stone_short, R.string.unit_system_stone_description, UnitConverter.scaled(6.35029318)),
	shortTon(R.string.unit_system_short_tone, R.string.unit_system_short_tone_short, R.string.unit_system_short_tone_description, UnitConverter.scaled(907.18474)),
	longTon(R.string.unit_system_long_tone, R.string.unit_system_long_tone_short, R.string.unit_system_long_tone_description, UnitConverter.scaled(1016.0469088));

	private final int localeName;
	private final int shortName;
	private final int description;
	private final UnitConverter converter;

	WeightSystem(int localeName, int shortName, int description, UnitConverter converter) {
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
