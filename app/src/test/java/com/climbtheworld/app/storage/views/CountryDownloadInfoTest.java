package com.climbtheworld.app.storage.views;

import static org.junit.Assert.assertEquals;

import android.content.res.Resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class CountryDownloadInfoTest {
	private static final long NOW = TimeUnit.DAYS.toMillis(20_000);
	private final Resources resources = RuntimeEnvironment.getApplication().getResources();

	@Test
	public void ageUsesTheLargestWholeUnit() {
		assertEquals("today", CountryDownloadInfo.age(NOW - TimeUnit.HOURS.toMillis(20), NOW));
		assertEquals("1 day ago", CountryDownloadInfo.age(NOW - daysMillis(1), NOW));
		assertEquals("6 days ago", CountryDownloadInfo.age(NOW - daysMillis(6), NOW));
		assertEquals("1 week ago", CountryDownloadInfo.age(NOW - daysMillis(10), NOW));
		assertEquals("3 months ago", CountryDownloadInfo.age(NOW - daysMillis(95), NOW));
		assertEquals("2 years ago", CountryDownloadInfo.age(NOW - daysMillis(800), NOW));
	}

	@Test
	public void futureDownloadCountsAsToday() {
		assertEquals("today", CountryDownloadInfo.age(NOW + daysMillis(2), NOW));
	}

	@Test
	public void describesAgeAndRouteCount() {
		assertEquals("Updated 3 months ago · 1,234 routes",
				CountryDownloadInfo.describe(resources, NOW - daysMillis(95), 1234, NOW));
		assertEquals("Updated today · 1 route",
				CountryDownloadInfo.describe(resources, NOW, 1, NOW));
		assertEquals("Updated today · 0 routes",
				CountryDownloadInfo.describe(resources, NOW, 0, NOW));
	}

	private static long daysMillis(long days) {
		return TimeUnit.DAYS.toMillis(days);
	}
}
