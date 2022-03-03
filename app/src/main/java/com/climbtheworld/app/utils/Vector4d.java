package com.climbtheworld.app.utils;

/**
 * Created by xyz on 1/30/18.
 */

public class Vector4d {
	public double x = 0;
	public double y = 0;
	public double z = 0;
	public double w = 0;

	public Vector4d() {

	}

	public Vector4d(double px, double py, double pz, double pw) {
		this.x = px;
		this.y = py;
		this.z = pz;
		this.w = pw;
	}

	public String toString() {
		return "x=" + x + " y=" + y + " z=" + z + " w=" + w;
	}
}
