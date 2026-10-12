package com.climbtheworld.app.storage.views;

import android.content.res.Resources;
import android.icu.text.RelativeDateTimeFormatter;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.DownloadedCountry;

import java.text.NumberFormat;
import java.util.concurrent.TimeUnit;

/** Describes an installed country's download, e.g. "Updated 3 months ago · 1,234 items". */
final class CountryDownloadInfo {
	private static final long DAYS_PER_WEEK = 7;
	private static final long DAYS_PER_MONTH = 30;
	private static final long DAYS_PER_YEAR = 365;

	private CountryDownloadInfo() {
	}

	static String describe(Resources resources, DownloadedCountry download, long nowMillis) {
		int itemCount = (int) Math.min(download.elementCount, Integer.MAX_VALUE);
		String items = resources.getQuantityString(R.plurals.download_country_items, itemCount,
				NumberFormat.getIntegerInstance().format(download.elementCount));
		return resources.getString(R.string.download_country_info,
				age(download.downloadedAt, nowMillis), items);
	}

	/** Day resolution is enough for data that changes over weeks; a future time counts as today. */
	static String age(long downloadedAtMillis, long nowMillis) {
		RelativeDateTimeFormatter formatter = RelativeDateTimeFormatter.getInstance();
		long days = TimeUnit.MILLISECONDS.toDays(Math.max(0, nowMillis - downloadedAtMillis));
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
