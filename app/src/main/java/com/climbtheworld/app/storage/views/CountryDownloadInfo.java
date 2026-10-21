package com.climbtheworld.app.storage.views;

import android.content.res.Resources;
import android.icu.text.RelativeDateTimeFormatter;

import com.climbtheworld.app.R;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Describes an installed country's download, e.g. "Updated 3 months ago · 1,234 routes". */
final class CountryDownloadInfo {
	private static final long DAYS_PER_WEEK = 7;
	private static final long DAYS_PER_MONTH = 30;
	private static final long DAYS_PER_YEAR = 365;

	private CountryDownloadInfo() {
	}

	static String describe(Resources resources, long downloadedAtMillis, int routeCount,
	                       long nowMillis) {
		String routes = resources.getQuantityString(R.plurals.download_country_routes, routeCount,
				NumberFormat.getIntegerInstance().format(routeCount));
		return resources.getString(R.string.download_country_info,
				age(downloadedAtMillis, nowMillis, ZoneId.systemDefault()), routes);
	}

	/**
	 * Day resolution is enough for data that changes over weeks. Days are calendar days in
	 * {@code zone}, so a download stops being "today" at midnight; a future time counts as today.
	 */
	static String age(long downloadedAtMillis, long nowMillis, ZoneId zone) {
		RelativeDateTimeFormatter formatter = RelativeDateTimeFormatter.getInstance();
		long days = Math.max(0, ChronoUnit.DAYS.between(
				Instant.ofEpochMilli(downloadedAtMillis).atZone(zone).toLocalDate(),
				Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()));
		if (days == 0) {
			return formatter.format(RelativeDateTimeFormatter.Direction.THIS,
					RelativeDateTimeFormatter.AbsoluteUnit.DAY);
		}
		if (days < DAYS_PER_WEEK) {
			return formatter.format(days, RelativeDateTimeFormatter.Direction.LAST,
					RelativeDateTimeFormatter.RelativeUnit.DAYS);
		}
		if (days < DAYS_PER_MONTH) {
			return formatter.format(days / DAYS_PER_WEEK, RelativeDateTimeFormatter.Direction.LAST,
					RelativeDateTimeFormatter.RelativeUnit.WEEKS);
		}
		if (days < DAYS_PER_YEAR) {
			return formatter.format(days / DAYS_PER_MONTH, RelativeDateTimeFormatter.Direction.LAST,
					RelativeDateTimeFormatter.RelativeUnit.MONTHS);
		}
		return formatter.format(days / DAYS_PER_YEAR, RelativeDateTimeFormatter.Direction.LAST,
				RelativeDateTimeFormatter.RelativeUnit.YEARS);
	}
}
