package com.climbtheworld.app.storage.offline.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OverpassCountryDownloaderTest {
	@Test
	public void knownLengthMapsLinearlyOntoDownloadRange() {
		assertEquals(1, OverpassCountryDownloader.downloadPercent(0, 1000));
		assertEquals(40, OverpassCountryDownloader.downloadPercent(500, 1000));
		assertEquals(OverpassCountryDownloader.PROGRESS_DOWNLOADED,
				OverpassCountryDownloader.downloadPercent(1000, 1000));
		assertEquals(OverpassCountryDownloader.PROGRESS_DOWNLOADED,
				OverpassCountryDownloader.downloadPercent(2000, 1000));
	}

	@Test
	public void unknownLengthApproachesButNeverReachesDownloadEnd() {
		long half = OverpassCountryDownloader.HALF_PROGRESS_BYTES;
		assertEquals(1, OverpassCountryDownloader.downloadPercent(0, -1));
		assertEquals(40, OverpassCountryDownloader.downloadPercent(half, -1));
		int previous = 0;
		for (long bytes = 0; bytes <= 20 * half; bytes += half / 4) {
			int percent = OverpassCountryDownloader.downloadPercent(bytes, -1);
			assertTrue(percent >= previous);
			assertTrue(percent < OverpassCountryDownloader.PROGRESS_DOWNLOADED);
			previous = percent;
		}
	}
}
