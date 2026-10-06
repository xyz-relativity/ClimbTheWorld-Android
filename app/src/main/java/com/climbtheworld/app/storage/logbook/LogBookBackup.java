package com.climbtheworld.app.storage.logbook;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.OsmEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Saves the log book to a JSON file and merges such a file back in. Must be called off the main
 * thread.
 *
 * <p>Restoring never deletes: an entry from the file is added when the log book does not have
 * it, and replaces the stored one only when it was edited more recently.</p>
 */
public final class LogBookBackup {
	public static final String MIME_TYPE = "application/json";
	private static final String FORMAT = "climbtheworld.logbook";
	// Bump when a change makes older versions of the app unable to read the file.
	private static final int VERSION = 1;

	private LogBookBackup() {
		//hide constructor
	}

	public static String getFileName(long time) {
		return "climbtheworld-logbook-"
				+ new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(time)) + ".json";
	}

	/**
	 * @return the number of entries written.
	 */
	public static int write(LogBookDatabase database, OutputStream output) throws IOException {
		List<LogBookEntry> entries = database.logBookDao().loadAll();
		try {
			output.write(toJson(entries, System.currentTimeMillis()).toString(1)
					.getBytes(StandardCharsets.UTF_8));
		} catch (JSONException exception) {
			throw new IOException(exception);
		}
		output.flush();
		return entries.size();
	}

	public static RestoreResult restore(LogBookDatabase database, InputStream input)
			throws IOException {
		List<LogBookEntry> entries = fromJson(readText(input));
		return database.runInTransaction(() -> merge(database, entries));
	}

	static JSONObject toJson(List<LogBookEntry> entries, long exportedAt) throws JSONException {
		JSONArray items = new JSONArray();
		for (LogBookEntry entry : entries) {
			items.put(new JSONObject()
					.put("osmType", entry.osmType.name())
					.put("osmID", entry.osmID)
					.put("note", entry.note)
					.put("attempt", entry.attempt.name())
					.put("createdAt", entry.createdAt)
					.put("updatedAt", entry.updatedAt)
					.put("name", entry.name)
					.put("nodeType", entry.nodeType)
					.put("decimalLatitude", entry.decimalLatitude)
					.put("decimalLongitude", entry.decimalLongitude));
		}
		return new JSONObject()
				.put("format", FORMAT)
				.put("version", VERSION)
				.put("exportedAt", exportedAt)
				.put("entries", items);
	}

	static List<LogBookEntry> fromJson(String text) throws InvalidBackupException {
		try {
			JSONObject root = new JSONObject(text);
			if (!FORMAT.equals(root.optString("format"))) {
				throw new InvalidBackupException(R.string.log_book_restore_not_a_backup);
			}
			if (root.getInt("version") > VERSION) {
				throw new InvalidBackupException(R.string.log_book_restore_newer_version);
			}

			JSONArray items = root.getJSONArray("entries");
			List<LogBookEntry> result = new ArrayList<>();
			for (int i = 0; i < items.length(); i++) {
				result.add(entryFromJson(items.getJSONObject(i)));
			}
			return result;
		} catch (JSONException | IllegalArgumentException exception) {
			throw new InvalidBackupException(R.string.log_book_restore_not_a_backup);
		}
	}

	private static LogBookEntry entryFromJson(JSONObject item) throws JSONException {
		LogBookEntry entry = new LogBookEntry(
				OsmEntity.EntityOsmType.valueOf(item.getString("osmType")),
				item.getLong("osmID"));
		entry.note = item.optString("note");
		entry.attempt = parseAttempt(item.optString("attempt"));
		entry.createdAt = item.optLong("createdAt");
		entry.updatedAt = item.optLong("updatedAt");
		entry.name = item.isNull("name") ? null : item.getString("name");
		entry.nodeType = item.isNull("nodeType") ? null : item.getString("nodeType");
		entry.decimalLatitude = item.optDouble("decimalLatitude", 0);
		entry.decimalLongitude = item.optDouble("decimalLongitude", 0);
		return entry;
	}

	/**
	 * Files from a newer version of the app may hold attempts this one does not know; the rest
	 * of such an entry is still worth keeping.
	 */
	private static LogBookEntry.Attempt parseAttempt(String name) {
		try {
			return LogBookEntry.Attempt.valueOf(name);
		} catch (IllegalArgumentException ignored) {
			return LogBookEntry.Attempt.none;
		}
	}

	private static RestoreResult merge(LogBookDatabase database, List<LogBookEntry> entries) {
		RestoreResult result = new RestoreResult();
		for (LogBookEntry entry : entries) {
			if (!LogBook.canLog(entry.osmID) || entry.isEmpty()) {
				result.unchanged++;
				continue;
			}

			LogBookEntry stored = database.logBookDao().find(entry.osmType, entry.osmID);
			if (stored == null) {
				result.added++;
			} else if (entry.updatedAt > stored.updatedAt) {
				result.updated++;
			} else {
				result.unchanged++;
				continue;
			}
			database.logBookDao().upsert(entry);
		}
		return result;
	}

	private static String readText(InputStream input) throws IOException {
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = input.read(buffer)) != -1) {
			result.write(buffer, 0, read);
		}
		return result.toString(StandardCharsets.UTF_8);
	}

	public static final class RestoreResult {
		public int added;
		public int updated;
		// Already up to date, or nothing worth keeping.
		public int unchanged;
	}

	/**
	 * The file is not a log book backup this version of the app can read.
	 */
	public static final class InvalidBackupException extends IOException {
		private final int messageId;

		InvalidBackupException(int messageId) {
			this.messageId = messageId;
		}

		public int getMessageId() {
			return messageId;
		}
	}
}
