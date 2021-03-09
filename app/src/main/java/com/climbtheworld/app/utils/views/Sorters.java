package com.climbtheworld.app.utils.views;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.storage.database.GeoNode;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Sorters {
	private Sorters() {
		//hide constructor.
	}

	public static List<GeoNode.ClimbingStyle> sortStyles(AppCompatActivity parent, GeoNode.ClimbingStyle[] data) {
		return sortStyles(parent, Arrays.asList(data));
	}

	public static List<GeoNode.ClimbingStyle> sortStyles(AppCompatActivity parent, List<GeoNode.ClimbingStyle> data) {
		Collections.sort(data, new Comparator<GeoNode.ClimbingStyle>() {
			@Override
			public int compare(GeoNode.ClimbingStyle climbingStyle, GeoNode.ClimbingStyle t1) {
				return parent.getString(climbingStyle.getNameId()).compareTo(parent.getString(t1.getNameId()));
			}
		});

		return data;
	}
}
