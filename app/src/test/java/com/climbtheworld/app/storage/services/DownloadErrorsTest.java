package com.climbtheworld.app.storage.services;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.util.MalformedJsonException;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.offline.importer.DownloadsDisabledException;
import com.climbtheworld.app.storage.offline.importer.OverpassHttpException;
import com.climbtheworld.app.storage.offline.importer.OverpassServerException;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.net.UnknownHostException;

@RunWith(RobolectricTestRunner.class)
public class DownloadErrorsTest {
	private final Context context = RuntimeEnvironment.getApplication();

	@Test
	public void classifiesFailures() {
		assertReason(R.string.download_error_disabled, new DownloadsDisabledException());
		assertReason(R.string.download_error_server_timeout,
				new OverpassServerException("runtime error: Query timed out"));
		assertReason(R.string.download_error_server_busy, new OverpassHttpException(429, "Too Many Requests"));
		assertReason(R.string.download_error_server_busy, new OverpassHttpException(504, "Gateway Timeout"));
		assertReason(R.string.download_error_invalid_response, new MalformedJsonException("bad"));
		assertReason(R.string.download_error_invalid_response, new JSONException("bad"));
		assertReason(R.string.download_error_invalid_response, new IllegalStateException("bad"));
		assertReason(R.string.download_error_network, new UnknownHostException("overpass-api.de"));
		assertReason(R.string.download_error_memory, new OutOfMemoryError());
	}

	@Test
	public void rejectedRequestIncludesStatusCode() {
		assertEquals(context.getString(R.string.download_error_server, 400),
				DownloadErrors.reason(context, new OverpassHttpException(400, "Bad Request")));
	}

	@Test
	public void messageNamesTheCountry() {
		assertEquals(context.getString(R.string.download_error, "Canada",
						context.getString(R.string.download_error_network)),
				DownloadErrors.message(context, "CA", new UnknownHostException()));
	}

	private void assertReason(int expected, Throwable failure) {
		assertEquals(context.getString(expected), DownloadErrors.reason(context, failure));
	}
}
