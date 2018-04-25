package com.ar.climbing.utils;

/**
 * Created by xyz on 1/29/18.
 */

public class Vector2d {
    public double x;
    public double y;

    public Vector2d() {

    }

    public Vector2d(double inX, double inY) {
        this.x = inX;
        this.y = inY;
    }

    @Override
    public String toString() {
        return "[" + x + "," + y + "]";
    }
}
