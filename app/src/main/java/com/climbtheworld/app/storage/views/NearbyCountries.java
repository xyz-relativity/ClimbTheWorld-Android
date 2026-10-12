package com.climbtheworld.app.storage.views;

import android.content.Context;
import android.telephony.TelephonyManager;

import com.climbtheworld.app.configs.Configs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The countries the user is likely in, found without asking for any permission: the mobile
 * network's country, then the countries around the last location the app saved, and only if
 * neither is known, the SIM's country.
 */
final class NearbyCountries {
	/** A border region is in its neighbours' bounding boxes too, so a few are kept. */
	static final int MAX_BOX_MATCHES = 3;

	private NearbyCountries() {
	}

	/** @return ISO codes of countries in the map, most likely first. */
	static List<String> find(Context context, Map<String, CountryViewState> countries) {
		Set<String> result = new LinkedHashSet<>();
		TelephonyManager telephony = context.getSystemService(TelephonyManager.class);
		if (telephony != null) {
			// The network the phone is on, so it follows the user abroad.
			addCountry(result, countries, telephony.getNetworkCountryIso());
		}

		Configs configs = Configs.instance(context);
		float latitude = configs.getFloat(Configs.ConfigKey.virtualCameraDegLat);
		float longitude = configs.getFloat(Configs.ConfigKey.virtualCameraDegLon);
		// Every screen saves the position as it pauses, so the default is saved before any fix.
		if (latitude != (float) Configs.ConfigKey.virtualCameraDegLat.defaultVal
				|| longitude != (float) Configs.ConfigKey.virtualCameraDegLon.defaultVal) {
			result.addAll(containing(countries.values(), latitude, longitude, MAX_BOX_MATCHES));
		}

		if (result.isEmpty() && telephony != null) {
			// The SIM's home country, for a phone out of signal.
			addCountry(result, countries, telephony.getSimCountryIso());
		}
		return new ArrayList<>(result);
	}

	private static void addCountry(Set<String> result, Map<String, CountryViewState> countries,
	                               String countryIso) {
		if (countryIso == null || countryIso.isEmpty()) {
			return;
		}
		String code = countryIso.toUpperCase(Locale.ROOT);
		if (countries.containsKey(code)) {
			result.add(code);
		}
	}

	/**
	 * The countries whose bounding box holds the point, smallest box first, as that is most likely
	 * the country the point is in. Boxes around the whole globe would hold every point in their
	 * latitudes, so they are left out.
	 */
	static List<String> containing(Collection<CountryViewState> countries, double latitude,
	                               double longitude, int limit) {
		List<CountryViewState> matches = new ArrayList<>();
		for (CountryViewState country : countries) {
			if (!country.boxSpansAllLongitudes() && country.boxContains(latitude, longitude)) {
				matches.add(country);
			}
		}
		matches.sort(Comparator.comparingDouble(CountryViewState::boxArea));

		List<String> result = new ArrayList<>();
		for (int index = 0; index < Math.min(limit, matches.size()); index++) {
			result.add(matches.get(index).countryISO);
		}
		return result;
	}
}
