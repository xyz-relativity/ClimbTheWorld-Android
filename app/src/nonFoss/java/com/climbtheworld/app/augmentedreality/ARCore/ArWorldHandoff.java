package com.climbtheworld.app.augmentedreality.ARCore;

import android.util.Log;

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
 * One at a time, kept until an A.R. opens, see ArCoreSession.openSession: offering another, or
 * discarding it, closes it.
 */
final class ArWorldHandoff {
	private static final String TAG = ArWorldHandoff.class.getSimpleName();
	private static World offered;

	private ArWorldHandoff() {
	}

	/**
	 * Offers a world to the next A.R. to open.
	 */
	static void offer(World world) {
		discard();
		Log.i(TAG, "ARCore's world handed over, aligned on " + world.alignedRoutes().size()
				+ " route(s)");
		offered = world;
	}

	/**
	 * @return the world offered, which is the taker's from now on; null without one
	 */
	static World take() {
		World taken = offered;
		offered = null;
		return taken;
	}

	/**
	 * Closes the world offered, if any, as no A.R. is to take it.
	 */
	static void discard() {
		if (offered == null) {
			return;
		}
		Log.i(TAG, "ARCore's world handed over goes untaken");
		offered.session().close();
		offered = null;
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
