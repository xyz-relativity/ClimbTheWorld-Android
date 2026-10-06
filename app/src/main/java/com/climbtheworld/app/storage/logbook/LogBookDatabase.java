package com.climbtheworld.app.storage.logbook;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.climbtheworld.app.storage.database.AppDatabase;

/**
 * Data written by the user, kept apart from {@link AppDatabase}.
 *
 * <p>AppDatabase is a cache of OSM data and is rebuilt destructively on schema changes. This one
 * holds data that cannot be downloaded again, so it has no destructive fallback: every version
 * bump needs a migration, based on the schema exported to app/schemas.</p>
 *
 * <p>It is the only data included in Android's cloud backup (see res/xml/backup_rules.xml and
 * res/xml/data_extraction_rules.xml), so it does not use write-ahead logging: that would keep
 * recent changes in a separate -wal file, missing from a backup of the database file.</p>
 */
@Database(entities = {LogBookEntry.class}, version = 1, exportSchema = true)
public abstract class LogBookDatabase extends RoomDatabase {
	// Also named in the backup rules; renaming it drops the log book from Android's backup.
	private static final String LOG_BOOK_DB = "personalLogBook.db";
	private static LogBookDatabase instance;

	public static synchronized LogBookDatabase getInstance(Context context) {
		if (instance == null) {
			instance = Room.databaseBuilder(context.getApplicationContext(),
					LogBookDatabase.class, LOG_BOOK_DB)
					.setJournalMode(JournalMode.TRUNCATE)
					.build();
		}

		return instance;
	}

	public abstract LogBookEntryDao logBookDao();
}
