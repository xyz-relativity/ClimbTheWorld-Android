package com.climbtheworld.app.converter.tools;

import androidx.annotation.StringRes;

import com.climbtheworld.app.configs.Configs;

/** A unit of one kind of measure, like a length, that the units converter lists. */
public interface UnitSystem {
	@StringRes
	int getLocaleName();

	@StringRes
	int getShortName();

	@StringRes
	int getDescription();

	UnitConverter getConverter();

	default double convertTo(UnitSystem toSystem, double value) {
		return toSystem.getConverter().convertFromSI(getConverter().convertToSI(value));
	}

	/**
	 * @return the unit named value, whatever its case, or the default of defaultKey for a name
	 * that is none of type's
	 */
	static <T extends Enum<T> & UnitSystem> T fromString(Class<T> type, String value,
	                                                     Configs.ConfigKey defaultKey) {
		for (T entry : type.getEnumConstants()) {
			if (entry.name().equalsIgnoreCase(value)) {
				return entry;
			}
		}
		return Enum.valueOf(type, defaultKey.defaultVal.toString());
	}
}
