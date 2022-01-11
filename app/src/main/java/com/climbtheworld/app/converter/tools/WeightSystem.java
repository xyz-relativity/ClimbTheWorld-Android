package com.climbtheworld.app.converter.tools;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;

public enum WeightSystem {
	kiloGram(R.string.unit_system_kilogram, R.string.unit_system_kilogram_short, R.string.unit_system_kilogram_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value;
		}

		@Override
		public double convertFromSI(double value) {
			return value;
		}
	}),
	pound(R.string.unit_system_pound, R.string.unit_system_pound_short, R.string.unit_system_pound_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value * 0.45359237;
		}

		@Override
		public double convertFromSI(double value) {
			return value * 2.20462262184877581;
		}
	}),
	ounce(R.string.unit_system_ounce, R.string.unit_system_ounce_short, R.string.unit_system_ounce_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value * 0.028349523125;
		}

		@Override
		public double convertFromSI(double value) {
			return value * 35.27396194958041291568;
		}
	}),
	grain(R.string.unit_system_grain, R.string.unit_system_grain_short, R.string.unit_system_grain_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value * 0.00006479891;
		}

		@Override
		public double convertFromSI(double value) {
			return value * 15432.35835294143065061;
		}
	}),
	stone(R.string.unit_system_stone, R.string.unit_system_stone_short, R.string.unit_system_stone_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value * 6.35029318;
		}

		@Override
		public double convertFromSI(double value) {
			return value * 0.15747304441777;
		}
	}),
	shortTon(R.string.unit_system_short_tone, R.string.unit_system_short_tone_short, R.string.unit_system_short_tone_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value * 907.18474;
		}

		@Override
		public double convertFromSI(double value) {
			return value * 0.0011023113109243879;
		}
	}),
	longTon(R.string.unit_system_long_tone, R.string.unit_system_long_tone_short, R.string.unit_system_long_tone_description, new UnitConverter() {
		@Override
		public double convertToSI(double value) {
			return value * 1016.0469088;
		}

		@Override
		public double convertFromSI(double value) {
			return value * 0.0009842065276110606282276;
		}
	});

	WeightSystem(int localeName, int shortName, int description, UnitConverter converter) {
		this.localeName = localeName;
		this.shortName = shortName;
		this.description = description;
		this.converter = converter;
	}

	private final int localeName;
	private final int shortName;
	private final int description;
	private final UnitConverter converter;

	public static WeightSystem fromString(String value) {
		WeightSystem result = WeightSystem.valueOf(Configs.ConfigKey.converterWeightSystem.defaultVal.toString());
		for (WeightSystem entry : values()) {
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

	public double convertTo(WeightSystem toSystem, double value) {
		return toSystem.getConverter().convertFromSI(getConverter().convertToSI(value));
	}
}
