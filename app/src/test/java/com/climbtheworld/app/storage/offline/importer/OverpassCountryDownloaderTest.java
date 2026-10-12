package com.climbtheworld.app.storage.offline.importer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.util.MalformedJsonException;

import com.climbtheworld.app.utils.constants.Constants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.EOFException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class OverpassCountryDownloaderTest {
	private static final long CONNECT_ATTEMPT_NANOS =
			TimeUnit.SECONDS.toNanos(Constants.HTTP_CONNECT_TIMEOUT_SECONDS);
	private static final long FULL_QUERY_ATTEMPT_NANOS =
			TimeUnit.SECONDS.toNanos(Constants.OVERPASS_QUERY_TIMEOUT_SECONDS + 60);

	@Test
	public void retriesConnectionFailures() {
		assertTrue(OverpassCountryDownloader.isWorthRetrying(
				new UnknownHostException("overpass-api.de"), CONNECT_ATTEMPT_NANOS));
		assertTrue(OverpassCountryDownloader.isWorthRetrying(
				new ConnectException("Connection refused"), CONNECT_ATTEMPT_NANOS));
		assertTrue(OverpassCountryDownloader.isWorthRetrying(
				new SocketTimeoutException("connect timed out"), CONNECT_ATTEMPT_NANOS));
	}

	@Test
	public void retriesResponseCutShort() {
		assertTrue(OverpassCountryDownloader.isWorthRetrying(
				new EOFException("unexpected end of stream"), FULL_QUERY_ATTEMPT_NANOS));
	}

	@Test
	public void doesNotRetryFailuresThatWouldRepeat() {
		assertFalse(OverpassCountryDownloader.isWorthRetrying(
				new OverpassServerException("runtime error: Query timed out"), FULL_QUERY_ATTEMPT_NANOS));
		assertFalse(OverpassCountryDownloader.isWorthRetrying(
				new MalformedJsonException("bad"), CONNECT_ATTEMPT_NANOS));
		assertFalse(OverpassCountryDownloader.isWorthRetrying(
				new SocketTimeoutException("timeout"), FULL_QUERY_ATTEMPT_NANOS));
	}
}
