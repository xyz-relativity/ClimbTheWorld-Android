package com.climbtheworld.app.tools;

public enum WeightSystem {
    kiloGram(-1, -1, -1, 1.0, 1.0),
    metricTon(-1, -1, -1, 1000.0, 0.001),
    gram(-1, -1, -1, 0.001, 1000.0),
    milliGram(-1, -1, -1, 0.000001, 1000000.0),
    pound(-1, -1, -1, 0.45359237, 2.20462262184877581),
    ounce(-1, -1, -1, 0.028349523125, 35.27396194958041291568),
    grain(-1, -1, -1, 0.00006479891, 15432.35835294143065061),
    stone(-1, -1, -1, 6.35029318, 0.15747304441777),
    shortTon(-1, -1, -1, 907.18474, 0.0011023113109243879),
    longTon(-1, -1, -1, 1016.0469088, 0.0009842065276110606282276);

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
