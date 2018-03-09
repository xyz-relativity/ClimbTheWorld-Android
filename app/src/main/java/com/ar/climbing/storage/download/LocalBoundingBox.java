package com.ar.climbing.storage.download;

/**
 * Created by xyz on 1/29/18.
 */

public class LocalBoundingBox {
    public double s;
    public double w;
    public double n;
    public double e;

    public LocalBoundingBox(double inS, double inW, double inN, double inE) {
        this.s = inS;
        this.w = inW;
        this.n = inN;
        this.e = inE;
    }
}
