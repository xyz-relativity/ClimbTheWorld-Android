package com.climbtheworld.app.converter.tools;

import java.util.function.DoubleUnaryOperator;

/** Converts a unit's values to and from the SI unit of the same kind. */
public interface UnitConverter {
	/** The SI unit itself. */
	UnitConverter SI = of(value -> value, value -> value);

	double convertToSI(double value);

	double convertFromSI(double value);

	/** A unit worth factor SI units, like the mile of 1609.344 m. */
	static UnitConverter scaled(double factor) {
		return of(value -> value * factor, value -> value / factor);
	}

	static UnitConverter of(DoubleUnaryOperator toSI, DoubleUnaryOperator fromSI) {
		return new UnitConverter() {
			@Override
			public double convertToSI(double value) {
				return toSI.applyAsDouble(value);
			}

			@Override
			public double convertFromSI(double value) {
				return fromSI.applyAsDouble(value);
			}
		};
	}
}
