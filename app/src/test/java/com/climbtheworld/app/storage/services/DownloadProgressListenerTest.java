package com.climbtheworld.app.storage.services;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DownloadProgressListenerTest {
	private int runs;
	private final DownloadProgressListener listener =
			DownloadProgressListener.onEachDone(() -> runs++);

	@Test
	public void itRunsOnceADownloadWroteItsCountry() {
		listener.onProgressChanged("FR", DownloadProgressListener.STATUS_DONE);

		assertEquals(1, runs);
	}

	@Test
	public void itRunsForEachDownloadDone() {
		listener.onProgressChanged("FR", DownloadProgressListener.STATUS_DONE);
		listener.onProgressChanged("RO", DownloadProgressListener.STATUS_DONE);

		assertEquals(2, runs);
	}

	@Test
	public void itDoesNotRunWhileADownloadIsUnderWay() {
		listener.onProgressChanged("FR", DownloadProgressListener.STATUS_WAITING);
		listener.onProgressChanged("FR", 0);
		listener.onProgressChanged("FR", 100);

		assertEquals(0, runs);
	}

	@Test
	public void itDoesNotRunForADownloadThatWroteNothing() {
		listener.onProgressChanged("FR", DownloadProgressListener.STATUS_ERROR);
		listener.onProgressChanged("RO", DownloadProgressListener.STATUS_CANCELLED);

		assertEquals(0, runs);
	}

	@Test
	public void itDoesNotRunForTheStateOfDownloadsWhenAdded() {
		listener.onProgressRestored("FR", DownloadProgressListener.STATUS_WAITING);
		listener.onProgressRestored("RO", 50);

		assertEquals(0, runs);
	}
}
