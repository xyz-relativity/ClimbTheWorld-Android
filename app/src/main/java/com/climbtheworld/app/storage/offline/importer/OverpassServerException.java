package com.climbtheworld.app.storage.offline.importer;

import java.io.IOException;

/** Overpass answered successfully but reported a runtime error, so the response is incomplete. */
public class OverpassServerException extends IOException {
	public OverpassServerException(String message) {
		super(message);
	}
}
