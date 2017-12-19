package com.ar.openClimbAR.tools;

/**
 * Created by xyz on 11/30/17.
 */

public class PointOfInterest {
    enum POIType {observer, climbing, cardinal};
    private final POIType type;

    private float decimalLongitude;
    private float decimalLatitude;
    private float altitudeMeters;

    //climb topo
    private float lengthMeters = 0;
    private String name = "";
    private String description = "";
    private String protection = "";
    private String level = "";

    public PointOfInterest(POIType pType, float pDecimalLongitude, float pDecimalLatitude, float pMetersAltitude)
    {
        this.type = pType;
        this.updatePOILocation(pDecimalLongitude, pDecimalLatitude, pMetersAltitude);
    }

    public POIType getType() {
        return type;
    }

    public float getDecimalLongitude() {
        return decimalLongitude;
    }

    public float getDecimalLatitude() {
        return decimalLatitude;
    }

    public float getAltitudeMeters() {
        return altitudeMeters;
    }

    public float getLengthMeters() {
        return lengthMeters;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getProtection() {
        return protection;
    }

    public String getLevel() {
        return level;
    }

    public void updatePOILocation(float pDecimalLongitude, float pDecimalLatitude, float pMetersAltitude)
    {
        this.decimalLongitude = pDecimalLongitude;
        this.decimalLatitude = pDecimalLatitude;
        this.altitudeMeters = pMetersAltitude;
    }

    public void updatePOIInfo(float pLengthMeters, String pName, String pDescription, String pProtection, String pLevel)
    {
        this.lengthMeters = pLengthMeters;
        this.name = pName;
        this.description = pDescription;
        this.protection = pProtection;
        this.level = pLevel;
    }
}
