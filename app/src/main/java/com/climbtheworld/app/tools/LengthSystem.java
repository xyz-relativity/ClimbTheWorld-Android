package com.climbtheworld.app.tools;

import com.climbtheworld.app.R;

public enum LengthSystem {
    meter(R.string.unit_system_meter, R.string.unit_system_kilo_meter_short, R.string.unit_system_meter_description, 1.0, 1.0),
    kiloMeter(R.string.unit_system_kilo_meter, R.string.unit_system_kilo_meter_short, R.string.unit_system_kilo_meter_description, 1000.0, 0.001),
    centiMeter(R.string.unit_system_centi_meter, R.string.unit_system_centi_meter_short, R.string.unit_system_centi_meter_description, 0.01, 100.0),
    milliMeter(R.string.unit_system_milli_meter, R.string.unit_system_milli_meter_short, R.string.unit_system_milli_meter_description, 0.001, 1000.0),
    microMeter(R.string.unit_system_micro_meter, R.string.unit_system_micro_meter_short, R.string.unit_system_micro_meter_description, 0.000001, 1000000.0),
    nanoMeter(R.string.unit_system_nano_meter, R.string.unit_system_nano_meter_short, R.string.unit_system_nano_meter_description, 0.000000001, 1000000000.0),
    mile(R.string.unit_system_mile, R.string.unit_system_mile_short, R.string.unit_system_mile_description, 1609.344, 0.00062137119223733397),
    yard(R.string.unit_system_yard, R.string.unit_system_yard_short, R.string.unit_system_yard_description, 0.9144, 1.09361329833770779),
    feet(R.string.unit_system_feet, R.string.unit_system_feet_short, R.string.unit_system_feet_description, 0.3048, 3.28083989501312336),
    inch(R.string.unit_system_inch, R.string.unit_system_inch_short, R.string.unit_system_inch_description, 0.0254, 39.3700787401574803),
    nauticalMile(R.string.unit_system_nautical_mile, R.string.unit_system_nautical_mile_short, R.string.unit_system_meter, 1852.0, 0.000539956803455723542),
    furlong(R.string.unit_system_furlong, R.string.unit_system_furlong_short, R.string.unit_system_furlong_description, 201.168, 0.0049709695379),
    lightYear(R.string.unit_system_lightyear, R.string.unit_system_lightyear_short, R.string.unit_system_lightyear_description, 9460730472580800.0, 0.0000000000000001057000834024615463709);

    LengthSystem(int localeName, int shortName, int description, double conversionToBase, double conversionFromBase) {
        this.localeName = localeName;
        this.shortName = shortName;
        this.description = description;
        this.conversionFromBase = conversionFromBase;
        this.conversionToBase = conversionToBase;
    }

    private int localeName;
    private int shortName;
    private int description;
    private double conversionToBase;
    private double conversionFromBase;

    public int getLocaleName() {
        return localeName;
    }

    public int getShortName() {
        return shortName;
    }

    public int getDescription() {
        return description;
    }

    public double getConversionToBase() {
        return conversionToBase;
    }

    public double getConversionFromBase() {
        return conversionFromBase;
    }

    public double convertTo(LengthSystem toSystem, double value) {
        return value * conversionToBase * toSystem.getConversionFromBase();
    }

    public double convertFrom(LengthSystem fromSystem, double value) {
        return value * fromSystem.getConversionToBase() * conversionFromBase;
    }
}
