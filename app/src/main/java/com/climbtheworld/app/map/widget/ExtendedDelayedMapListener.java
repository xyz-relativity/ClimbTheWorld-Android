package com.climbtheworld.app.map.widget;

import android.os.Handler;
import android.util.Log;

import org.osmdroid.api.IMapView;
import org.osmdroid.events.MapEvent;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;

public class ExtendedDelayedMapListener implements MapListener {
	protected static final int DEFAULT_DELAY = 100;
	protected static final int DEFAULT_DROP_LIMIT = -1; //-1 = drop all (old behaviour)

	MapListener wrappedListener;

	protected long delay;
	protected int dropLimit;
	protected int toDrop;

	protected Handler handler;
	protected CallbackTask callback;

	/*
	 * @param wrappedListener The wrapped MapListener
	 *
	 * @param delay Listening delay, in milliseconds
	 */
	public ExtendedDelayedMapListener(final MapListener wrappedListener, final long delay) {
		this(wrappedListener, delay, DEFAULT_DROP_LIMIT);
	}

	/*
	 * @param wrappedListener The wrapped MapListener
	 *
	 * @param delay Listening delay, in milliseconds
	 */
	public ExtendedDelayedMapListener(final MapListener wrappedListener, final long delay, final int dropLimit) {
		this.wrappedListener = wrappedListener;
		this.delay = delay;
		this.dropLimit = dropLimit;
		this.handler = new Handler();
		this.callback = null;
	}

	/*
	 * Constructor with default delay.
	 *
	 * @param wrappedListener The wrapped MapListener
	 */
	public ExtendedDelayedMapListener(final MapListener wrappedListener) {
		this(wrappedListener, DEFAULT_DELAY);
	}

	@Override
	public boolean onScroll(final ScrollEvent event) {
		dispatch(event);
		return true;
	}

	@Override
	public boolean onZoom(final ZoomEvent event) {
		dispatch(event);
		return true;
	}

	/*
	 * Process an incoming MapEvent.
	 */
	protected void dispatch(final MapEvent event) {
		if (toDrop == 0) {
			if (callback != null) {
				handler.removeCallbacks(callback);
			}

			callback = new ExtendedDelayedMapListener.CallbackTask(event);
			callback.run();
			return;
		}

		// cancel any pending callback
		if ((toDrop > 0 || toDrop == -1) && callback != null) {
			handler.removeCallbacks(callback);
			if (toDrop > 0) {
				toDrop -= 1;
			}
		}

		callback = new ExtendedDelayedMapListener.CallbackTask(event);

		// set timer
		handler.postDelayed(callback, delay);
	}

	// Callback tasks
	private class CallbackTask implements Runnable {
		private final MapEvent event;

		public CallbackTask(final MapEvent event) {
			this.event = event;
		}

		@Override
		public void run() {
			// do the callback
			if (event instanceof ScrollEvent) {
				wrappedListener.onScroll((ScrollEvent) event);
			} else if (event instanceof ZoomEvent) {
				wrappedListener.onZoom((ZoomEvent) event);
			} else {
				// unknown event; discard
				Log.d(IMapView.LOGTAG, "Unknown event received: " + event);
			}
			toDrop = dropLimit;
		}
	}
}
