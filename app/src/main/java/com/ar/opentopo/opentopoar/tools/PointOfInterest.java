package com.ar.opentopo.opentopoar.tools;

/**
 * Created by xyz on 11/30/17.
 */

public class PointOfInterest {
    enum POIType {observer, climbing};
    private final POIType type;

    private float decimalLongitude;
    private float decimalLatitude;
    private float metersAltitude;

    public PointOfInterest(POIType pType, float pDecimalLongitude, float pDecimalLatitude, float pMetersAltitude)
    {
        this.type = pType;
        this.updatePOI(pDecimalLongitude, pDecimalLatitude, pMetersAltitude);
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

    public float getMetersAltitude() {
        return metersAltitude;
    }

    public void updatePOI(float pDecimalLongitude, float pDecimalLatitude, float pMetersAltitude)
    {
        this.decimalLongitude = pDecimalLongitude;
        this.decimalLatitude = pDecimalLatitude;
        this.metersAltitude = pMetersAltitude;
    }
}
