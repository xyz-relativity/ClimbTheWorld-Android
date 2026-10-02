package com.climbtheworld.app.storage.offline.importer;

import java.io.Closeable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Turns the phases of one Overpass download into a single percentage. Most of the time is spent
 * while the server evaluates the query, so that phase advances with the elapsed share of the query
 * timeout, and the streamed bytes only fill the remaining range.
 */
final class DownloadProgressTracker implements Closeable {
	static final int PROGRESS_START = 10;
	static final int PROGRESS_SERVER_DONE = 75;
	static final int PROGRESS_DOWNLOADED = 95;
	static final int PROGRESS_WRITING = 98;
	/**
	 * Overpass can send its response header and a short JSON preamble before the query finishes,
	 * so bytes only count as streamed data once they exceed it.
	 */
	static final long STREAMING_THRESHOLD_BYTES = 4L * 1024;
	/** Bytes after which an unknown-length download reports half of the streaming range. */
	static final long HALF_PROGRESS_BYTES = 16L * 1024 * 1024;
	private static final long TICK_MILLIS = 1_000L;

	private enum Phase {WAITING, STREAMING, WRITING}

	private final OverpassCountryDownloader.ProgressListener listener;
	private final long timeoutMillis;
	private final LongSupplier clockMillis;
	private Timer timer;
	private Phase phase = Phase.WAITING;
	private long attemptStartMillis;
	private int lastPercent = -1;
	private boolean closed;

	DownloadProgressTracker(OverpassCountryDownloader.ProgressListener listener, long timeoutMillis,
	                        LongSupplier clockMillis) {
		this.listener = listener;
		this.timeoutMillis = timeoutMillis;
		this.clockMillis = clockMillis;
	}

	/** Creates a tracker whose waiting phase advances once per second. */
	static DownloadProgressTracker start(OverpassCountryDownloader.ProgressListener listener,
	                                     long timeoutMillis) {
		DownloadProgressTracker tracker = new DownloadProgressTracker(listener, timeoutMillis,
				() -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
		tracker.timer = new Timer("OverpassProgress", true);
		tracker.timer.schedule(new TimerTask() {
			@Override
			public void run() {
				tracker.tick();
			}
		}, TICK_MILLIS, TICK_MILLIS);
		return tracker;
	}

	/** Restarts the waiting phase for a new request, including retries. */
	synchronized void startAttempt() {
		phase = Phase.WAITING;
		attemptStartMillis = clockMillis.getAsLong();
		lastPercent = -1;
		report(waitingPercent(0, timeoutMillis));
	}

	synchronized void tick() {
		if (phase == Phase.WAITING) {
			report(waitingPercent(clockMillis.getAsLong() - attemptStartMillis, timeoutMillis));
		}
	}

	synchronized void onBytesRead(long totalBytes, long contentLength) {
		if (phase == Phase.WRITING || (phase == Phase.WAITING && totalBytes < STREAMING_THRESHOLD_BYTES)) {
			return;
		}
		phase = Phase.STREAMING;
		report(downloadPercent(totalBytes, contentLength));
	}

	synchronized void onWriting() {
		phase = Phase.WRITING;
		report(PROGRESS_WRITING);
	}

	/** Stops the timer; nothing is reported afterwards, so the caller's final state comes last. */
	@Override
	public synchronized void close() {
		closed = true;
		if (timer != null) {
			timer.cancel();
		}
	}

	private void report(int percent) {
		if (closed || percent == lastPercent) {
			return;
		}
		lastPercent = percent;
		listener.onProgress(percent);
	}

	/**
	 * Maps the elapsed share of the query timeout onto
	 * {@link #PROGRESS_START}..{@link #PROGRESS_SERVER_DONE}.
	 */
	static int waitingPercent(long elapsedMillis, long timeoutMillis) {
		double fraction = timeoutMillis > 0
				? Math.min(1.0, Math.max(0.0, (double) elapsedMillis / timeoutMillis))
				: 1.0;
		return PROGRESS_START + (int) ((PROGRESS_SERVER_DONE - PROGRESS_START) * fraction);
	}

	/**
	 * Maps streamed bytes onto {@link #PROGRESS_SERVER_DONE}..{@link #PROGRESS_DOWNLOADED}.
	 * Overpass rarely sends a length, so unknown lengths approach the end without reaching it.
	 */
	static int downloadPercent(long bytesRead, long contentLength) {
		double fraction;
		if (contentLength > 0) {
			fraction = Math.min(1.0, (double) bytesRead / contentLength);
		} else {
			fraction = 1.0 - Math.pow(2.0, -(double) bytesRead / HALF_PROGRESS_BYTES);
		}
		return PROGRESS_SERVER_DONE + (int) ((PROGRESS_DOWNLOADED - PROGRESS_SERVER_DONE) * fraction);
	}
}
