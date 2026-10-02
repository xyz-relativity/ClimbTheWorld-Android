package com.climbtheworld.app.storage.offline.importer;

import java.io.IOException;

/** The user's settings do not allow data downloads on the current connection. */
public class DownloadsDisabledException extends IOException {
	public DownloadsDisabledException() {
		super("Data downloads are disabled");
	}
}
