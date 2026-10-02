package com.climbtheworld.app.storage.offline.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DownloadProgressTrackerTest {
	private static final long TIMEOUT_MILLIS = 800_000L;

	@Test
	public void waitingAdvancesWithElapsedShareOfTimeout() {
		assertEquals(DownloadProgressTracker.PROGRESS_START,
				DownloadProgressTracker.waitingPercent(0, TIMEOUT_MILLIS));
		assertEquals(42, DownloadProgressTracker.waitingPercent(TIMEOUT_MILLIS / 2, TIMEOUT_MILLIS));
		assertEquals(DownloadProgressTracker.PROGRESS_SERVER_DONE,
				DownloadProgressTracker.waitingPercent(TIMEOUT_MILLIS, TIMEOUT_MILLIS));
		assertEquals(DownloadProgressTracker.PROGRESS_SERVER_DONE,
				DownloadProgressTracker.waitingPercent(2 * TIMEOUT_MILLIS, TIMEOUT_MILLIS));
	}

	@Test
	public void knownLengthFillsStreamingRange() {
		assertEquals(DownloadProgressTracker.PROGRESS_SERVER_DONE,
				DownloadProgressTracker.downloadPercent(0, 1000));
		assertEquals(85, DownloadProgressTracker.downloadPercent(500, 1000));
		assertEquals(DownloadProgressTracker.PROGRESS_DOWNLOADED,
				DownloadProgressTracker.downloadPercent(1000, 1000));
	}

	@Test
	public void unknownLengthApproachesButNeverReachesDownloadEnd() {
		long half = DownloadProgressTracker.HALF_PROGRESS_BYTES;
		assertEquals(85, DownloadProgressTracker.downloadPercent(half, -1));
		int previous = 0;
		for (long bytes = 0; bytes <= 20 * half; bytes += half / 4) {
			int percent = DownloadProgressTracker.downloadPercent(bytes, -1);
			assertTrue(percent >= previous);
			assertTrue(percent < DownloadProgressTracker.PROGRESS_DOWNLOADED);
			previous = percent;
		}
	}

	@Test
	public void phasesReportInOrderAndStopAfterClose() {
		long[] now = {0};
		List<Integer> reported = new ArrayList<>();
		DownloadProgressTracker tracker = new DownloadProgressTracker(reported::add, TIMEOUT_MILLIS,
				() -> now[0]);

		tracker.startAttempt();
		now[0] = TIMEOUT_MILLIS / 2;
		tracker.tick();
		// The JSON preamble arrives without moving progress out of the waiting phase.
		tracker.onBytesRead(300, -1);
		tracker.tick();
		tracker.onBytesRead(DownloadProgressTracker.STREAMING_THRESHOLD_BYTES, 1000_000);
		now[0] = TIMEOUT_MILLIS;
		tracker.tick();
		tracker.onBytesRead(1000_000, 1000_000);
		tracker.onWriting();
		tracker.close();
		tracker.tick();
		tracker.onBytesRead(2000_000, 1000_000);

		assertEquals(Arrays.asList(10, 42, 75, DownloadProgressTracker.PROGRESS_DOWNLOADED,
				DownloadProgressTracker.PROGRESS_WRITING), reported);
	}

	@Test
	public void retryRestartsWaitingPhase() {
		long[] now = {0};
		List<Integer> reported = new ArrayList<>();
		DownloadProgressTracker tracker = new DownloadProgressTracker(reported::add, TIMEOUT_MILLIS,
				() -> now[0]);

		tracker.startAttempt();
		tracker.onBytesRead(DownloadProgressTracker.STREAMING_THRESHOLD_BYTES, -1);
		now[0] = 10_000;
		tracker.startAttempt();

		assertEquals(Arrays.asList(10, 75, 10), reported);
	}
}
