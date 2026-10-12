package com.climbtheworld.app.storage.offline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The country bounding boxes of res/raw/country_bbox.csv, whose lines are
 * "ISO, name, west, south, east, north" after a header line.
 */
public final class CountryBoxes {
	private static final int FIELD_COUNT = 6;

	private final List<Box> boxes;

	private CountryBoxes(List<Box> boxes) {
		this.boxes = boxes;
	}

	public static CountryBoxes read(Reader csv) throws IOException {
		List<Box> boxes = new ArrayList<>();
		BufferedReader reader = new BufferedReader(csv);
		reader.readLine(); // header
		String line;
		while ((line = reader.readLine()) != null) {
			String[] fields = line.split(",");
			if (fields.length != FIELD_COUNT) {
				continue;
			}
			try {
				boxes.add(new Box(fields[0].trim().toUpperCase(),
						Double.parseDouble(fields[2]), Double.parseDouble(fields[3]),
						Double.parseDouble(fields[4]), Double.parseDouble(fields[5])));
			} catch (NumberFormatException ignored) {
				// A country without a box can't be matched to a point.
			}
		}
		return new CountryBoxes(boxes);
	}

	/**
	 * ISO codes of the countries whose box holds the point, smallest box first, as that is most
	 * likely the country the point is in. Boxes around the whole globe would hold every point in
	 * their latitudes, so they are left out.
	 */
	public List<String> containing(double latitude, double longitude) {
		List<Box> matches = new ArrayList<>();
		for (Box box : boxes) {
			if (!box.spansAllLongitudes() && box.contains(latitude, longitude)) {
				matches.add(box);
			}
		}
		matches.sort(Comparator.comparingDouble(Box::area));

		List<String> result = new ArrayList<>();
		for (Box box : matches) {
			result.add(box.countryIso);
		}
		return result;
	}

	private static final class Box {
		final String countryIso;
		final double west;
		final double south;
		final double east;
		final double north;

		Box(String countryIso, double west, double south, double east, double north) {
			this.countryIso = countryIso;
			this.west = west;
			this.south = south;
			this.east = east;
			this.north = north;
		}

		boolean contains(double latitude, double longitude) {
			return latitude >= south && latitude <= north && longitude >= west && longitude <= east;
		}

		boolean spansAllLongitudes() {
			return west <= -180 && east >= 180;
		}

		double area() {
			return (north - south) * (east - west);
		}
	}
}
