package com.climbtheworld.app.augmentedreality.ARCore;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.climbtheworld.app.augmentedreality.placement.HandoffOffer;
import com.climbtheworld.app.storage.database.GeoNode;
import com.google.ar.core.Anchor;
import com.google.ar.core.Session;

import java.util.List;
import java.util.Map;

/**
 * ARCore's world handed from one A.R. of the app to the next one to open, so that it need not be
 * aligned again, see ArCoreSession.handOver: the A.R. view hands it to the node editor's A.R.
 * placement, opened from the view to add a node, and the editor hands it back as it closes, to
 * the view, which starts over then. The session is paused meanwhile, and its anchors, which it
 * keeps, tie the world to the map again once it tracks, see ArCoreSession.refreshLandmarks.
 * <p>
 * One at a time, kept until an A.R. opens, see ArCoreSession.openSession, for a while only, see
 * KEPT_MILLIS: offering another, or discarding it, closes it, and so does its time running out.
 * To be used from the main thread.
 */
final class ArWorldHandoff {
	private static final String TAG = ArWorldHandoff.class.getSimpleName();
	// How long a world offered waits for an A.R. to take it. The A.R. it is meant for takes it
	// as it opens, within a second or so, so this only ends the wait when none does: the editor
	// closed to another app, the A.R. view left before ARCore started in it, the app's screens
	// swiped away. The session, though paused, would keep ARCore's memory for as long as the app
	// runs then. Long enough to come back from a look at the node in another app, opened from
	// the editor, as an A.R. that comes too late starts a new world, which the user has to align
	// again. A paused session holds memory, not the camera, so a few minutes of it cost little.
	private static final long KEPT_MILLIS = 5 * 60_000;
	private static final Handler MAIN_THREAD = new Handler(Looper.getMainLooper());
	// It lives as long as the app, so what it lets go of holds on to nothing of the screens: a
	// world is a session made with the application's context, its anchors and map data.
	private static final HandoffOffer<World> OFFER = new HandoffOffer<>(new HandoffOffer.Timer() {
		@Override
		public void schedule(Runnable task, long delayMillis) {
			MAIN_THREAD.postDelayed(task, delayMillis);
		}

		@Override
		public void cancel(Runnable task) {
			MAIN_THREAD.removeCallbacks(task);
		}
	}, KEPT_MILLIS, untaken -> {
		Log.i(TAG, "ARCore's world handed over goes untaken");
		// Off the main thread, as closing it can take seconds.
		ArSessionCloser.close(untaken.session());
	});

	private ArWorldHandoff() {
	}

	/**
	 * Offers a world to the next A.R. to open, in place of one still waiting, which is closed.
	 */
	static void offer(World world) {
		OFFER.offer(world);
		Log.i(TAG, "ARCore's world handed over, aligned on " + world.alignedRoutes().size()
				+ " route(s)");
	}

	/**
	 * @return the world offered, which is the taker's from now on; null without one
	 */
	static World take() {
		return OFFER.take();
	}

	/**
	 * Closes the world offered, if any, as no A.R. is to take it.
	 */
	static void discard() {
		OFFER.withdraw();
	}

	/**
	 * A paused session, with its anchors of the mapped routes aligned on.
	 *
	 * @param depth         whether the session measures depth
	 * @param anchors       the anchors of the routes aligned on, by ArCoreSession's anchor key
	 * @param alignedRoutes the routes aligned on, each with its anchor
	 */
	record World(Session session, boolean depth, Map<String, Anchor> anchors,
	             List<GeoNode> alignedRoutes) {
	}
}
