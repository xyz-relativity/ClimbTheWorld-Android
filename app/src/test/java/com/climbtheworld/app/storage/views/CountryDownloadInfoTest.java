package com.climbtheworld.app.storage.views;

import static org.junit.Assert.assertEquals;

import android.content.res.Resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class CountryDownloadInfoTest {
	/** Noon, UTC. */
	private static final long NOW = TimeUnit.DAYS.toMillis(20_000) + TimeUnit.HOURS.toMillis(12);
	private static final ZoneId UTC = ZoneOffset.UTC;
	private final Resources resources = RuntimeEnvironment.getApplication().getResources();

	@Test
	public void ageUsesTheLargestWholeUnit() {
		assertEquals("today", CountryDownloadInfo.age(NOW - hoursMillis(11), NOW, UTC));
		assertEquals("1 day ago", CountryDownloadInfo.age(NOW - daysMillis(1), NOW, UTC));
		assertEquals("6 days ago", CountryDownloadInfo.age(NOW - daysMillis(6), NOW, UTC));
		assertEquals("1 week ago", CountryDownloadInfo.age(NOW - daysMillis(10), NOW, UTC));
		assertEquals("3 months ago", CountryDownloadInfo.age(NOW - daysMillis(95), NOW, UTC));
		assertEquals("2 years ago", CountryDownloadInfo.age(NOW - daysMillis(800), NOW, UTC));
	}

	@Test
	public void ageCountsCalendarDays() {
		// Downloaded today at 10:00, looked at tomorrow at 09:00: less than 24 hours, but a day old.
		long downloadedAt = NOW - hoursMillis(2);
		assertEquals("1 day ago",
				CountryDownloadInfo.age(downloadedAt, downloadedAt + hoursMillis(23), UTC));
		// Downloaded yesterday at 23:00, looked at today at 01:00.
		assertEquals("1 day ago",
				CountryDownloadInfo.age(NOW - hoursMillis(13), NOW - hoursMillis(11), UTC));
		// Six and a half days, across seven midnights.
		assertEquals("1 week ago",
				CountryDownloadInfo.age(NOW - daysMillis(6) - hoursMillis(13), NOW, UTC));
	}

	@Test
	public void ageUsesTheGivenTimeZone() {
		// 11:00 and 13:00 UTC are the same day in UTC, but either side of midnight in UTC+12.
		long downloadedAt = NOW - hoursMillis(1);
		long now = NOW + hoursMillis(1);
		assertEquals("today", CountryDownloadInfo.age(downloadedAt, now, UTC));
		assertEquals("1 day ago",
				CountryDownloadInfo.age(downloadedAt, now, ZoneOffset.ofHours(12)));
	}

	@Test
	public void futureDownloadCountsAsToday() {
		assertEquals("today", CountryDownloadInfo.age(NOW + daysMillis(2), NOW, UTC));
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

	private static long hoursMillis(long hours) {
		return TimeUnit.HOURS.toMillis(hours);
	}
}
