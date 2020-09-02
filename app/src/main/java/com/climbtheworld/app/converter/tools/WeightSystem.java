package com.climbtheworld.app.converter.tools;

import com.climbtheworld.app.R;

public enum WeightSystem {
    kiloGram(R.string.unit_system_kilogram, R.string.unit_system_kilogram_short, R.string.unit_system_kilogram_description, 1.0, 1.0),
    metricTon(R.string.unit_system_metric_ton, R.string.unit_system_metric_ton_short, R.string.unit_system_metric_ton_description, 1000.0, 0.001),
    gram(R.string.unit_system_gram, R.string.unit_system_gram_short, R.string.unit_system_gram_description, 0.001, 1000.0),
    milliGram(R.string.unit_system_milli_gram, R.string.unit_system_milli_meter_short, R.string.unit_system_milli_meter_description, 0.000001, 1000000.0),
    pound(R.string.unit_system_pound, R.string.unit_system_pound_short, R.string.unit_system_pound_description, 0.45359237, 2.20462262184877581),
    ounce(R.string.unit_system_ounce, R.string.unit_system_ounce_short, R.string.unit_system_ounce_description, 0.028349523125, 35.27396194958041291568),
    grain(R.string.unit_system_grain, R.string.unit_system_grain_short, R.string.unit_system_grain_description, 0.00006479891, 15432.35835294143065061),
    stone(R.string.unit_system_stone, R.string.unit_system_stone_short, R.string.unit_system_stone_description, 6.35029318, 0.15747304441777),
    shortTon(R.string.unit_system_short_tone, R.string.unit_system_short_tone_short, R.string.unit_system_short_tone_description, 907.18474, 0.0011023113109243879),
    longTon(R.string.unit_system_long_tone, R.string.unit_system_long_tone_short, R.string.unit_system_long_tone_description, 1016.0469088, 0.0009842065276110606282276);

    WeightSystem(int localeName, int shortName, int description, double conversionToBase, double conversionFromBase) {
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

    public double convertTo(WeightSystem toSystem, double value) {
        return value * conversionToBase * toSystem.getConversionFromBase();
    }

    public double convertFrom(WeightSystem fromSystem, double value) {
        return value * fromSystem.getConversionToBase() * conversionFromBase;
    }
}
