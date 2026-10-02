package com.climbtheworld.app.storage.services;

import android.content.Context;
import android.util.MalformedJsonException;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.offline.importer.DownloadsDisabledException;
import com.climbtheworld.app.storage.offline.importer.OverpassHttpException;
import com.climbtheworld.app.storage.offline.importer.OverpassServerException;

import org.json.JSONException;

import java.io.IOException;
import java.util.Locale;

/** Turns a failed country download into a message the user can act on. */
final class DownloadErrors {
	private DownloadErrors() {
	}

	static String message(Context context, String countryIso, Throwable failure) {
		String countryName = new Locale("", countryIso).getDisplayCountry();
		if (countryName.isEmpty()) {
			countryName = countryIso;
		}
		return context.getString(R.string.download_error, countryName, reason(context, failure));
	}

	static String reason(Context context, Throwable failure) {
		if (failure instanceof DownloadsDisabledException) {
			return context.getString(R.string.download_error_disabled);
		}
		if (failure instanceof OverpassServerException) {
			return context.getString(R.string.download_error_server_timeout);
		}
		if (failure instanceof OverpassHttpException) {
			OverpassHttpException httpFailure = (OverpassHttpException) failure;
			return httpFailure.isServerBusy()
					? context.getString(R.string.download_error_server_busy)
					: context.getString(R.string.download_error_server, httpFailure.getStatusCode());
		}
		// JsonReader reports unexpected tokens as IllegalStateException and malformed input as an
		// IOException subclass, so both are checked before treating IOException as a network fault.
		if (failure instanceof MalformedJsonException || failure instanceof JSONException
				|| failure instanceof IllegalStateException || failure instanceof NumberFormatException) {
			return context.getString(R.string.download_error_invalid_response);
		}
		if (failure instanceof IOException) {
			return context.getString(R.string.download_error_network);
		}
		if (failure instanceof OutOfMemoryError) {
			return context.getString(R.string.download_error_memory);
		}
		return context.getString(R.string.exception_message, String.valueOf(failure.getMessage()));
	}
}
