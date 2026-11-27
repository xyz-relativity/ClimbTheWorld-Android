package com.climbtheworld.app.storage.logbook;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

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
@Database(entities = {LogBookEntry.class}, version = 2, exportSchema = true)
public abstract class LogBookDatabase extends RoomDatabase {
	// Also named in the backup rules; renaming it drops the log book from Android's backup.
	private static final String LOG_BOOK_DB = "personalLogBook.db";
	// Adds the bookmarks, see LogBookEntry.bookmarked.
	static final Migration MIGRATION_1_2 = new Migration(1, 2) {
		@Override
		public void migrate(SupportSQLiteDatabase database) {
			database.execSQL("ALTER TABLE `LogBookEntry` ADD COLUMN `bookmarked` INTEGER NOT NULL"
					+ " DEFAULT 0");
		}
	};
	private static LogBookDatabase instance;

	public static synchronized LogBookDatabase getInstance(Context context) {
		if (instance == null) {
			instance = Room.databaseBuilder(context.getApplicationContext(),
					LogBookDatabase.class, LOG_BOOK_DB)
					.setJournalMode(JournalMode.TRUNCATE)
					.addMigrations(MIGRATION_1_2)
					.build();
		}

		return instance;
	}

	public abstract LogBookEntryDao logBookDao();
}
