package com.climbtheworld.app.utils;

/**
 * Created by xyz on 1/30/18.
 */

public class Quaternion {
    public double x = 0;
    public double y = 0;
    public double z = 0;
    public double w = 0;

    public Quaternion() {

    }

    public Quaternion(double px, double py, double pz, double pw) {
        this.x= px;
        this.y= py;
        this.z= pz;
        this.w= pw;
    }

    public String toString() {
        return "x=" + x + " y=" + y + " z=" + z + " w=" + w;
    }
}
