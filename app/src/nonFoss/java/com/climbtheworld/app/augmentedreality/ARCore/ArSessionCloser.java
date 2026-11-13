package com.climbtheworld.app.augmentedreality.ARCore;

import android.util.Log;

import com.google.ar.core.Session;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Closes ARCore's sessions off the UI thread. Closing one frees its memory, which ARCore says
 * can take seconds: on the UI thread, that holds up the screen that opens as the A.R. closes.
 * A session is paused before it is given here, and neither it nor its anchors are used again.
 */
final class ArSessionCloser {
	private static final String TAG = ArSessionCloser.class.getSimpleName();
	// One thread, so the sessions close one after the other, kept only while there are some.
	private static final ExecutorService CLOSING = new ThreadPoolExecutor(0, 1, 10,
			TimeUnit.SECONDS, new LinkedBlockingQueue<>());

	private ArSessionCloser() {
	}

	/**
	 * Closes the session, which is paused or was never resumed, some time soon.
	 */
	static void close(Session session) {
		CLOSING.execute(() -> {
			try {
				session.close();
			} catch (RuntimeException e) {
				Log.w(TAG, "ARCore's session did not close", e);
			}
		});
	}
}
