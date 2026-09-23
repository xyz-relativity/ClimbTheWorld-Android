package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import android.os.Handler;

final class HandlerRetryScheduler implements ReliableMessageSender.Scheduler {
	private final Handler handler;

	HandlerRetryScheduler(Handler handler) {
		this.handler = handler;
	}

	@Override
	public boolean schedule(Runnable task, long delayMillis) {
		return handler.postDelayed(task, delayMillis);
	}

	@Override
	public void cancel(Runnable task) {
		handler.removeCallbacks(task);
	}
}
