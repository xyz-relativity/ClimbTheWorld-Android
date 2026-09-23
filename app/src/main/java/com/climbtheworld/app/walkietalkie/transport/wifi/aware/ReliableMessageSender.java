package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import java.util.HashMap;
import java.util.Map;

final class ReliableMessageSender<P, K> {
	static final int MAX_SEND_ATTEMPTS = 4;
	static final long RETRY_DELAY_MS = 500;

	interface Scheduler {
		boolean schedule(Runnable task, long delayMillis);

		void cancel(Runnable task);
	}

	interface Sender<P> {
		void send(P peer, int messageId, byte[] payload);
	}

	interface ExhaustedListener<P, K> {
		void onAttemptsExhausted(P peer, K messageType, int attempts);
	}

	private final Scheduler scheduler;
	private final Sender<P> sender;
	private final ExhaustedListener<P, K> exhaustedListener;
	private final Map<Integer, PendingMessage<P, K>> pendingById = new HashMap<>();
	private final Map<P, Map<K, PendingMessage<P, K>>> pendingByPeer = new HashMap<>();
	private int nextMessageId = 1;
	private boolean closed;

	ReliableMessageSender(Scheduler scheduler, Sender<P> sender,
	                      ExhaustedListener<P, K> exhaustedListener) {
		this.scheduler = scheduler;
		this.sender = sender;
		this.exhaustedListener = exhaustedListener;
	}

	synchronized void send(P peer, K messageType, byte[] payload) {
		if (closed) {
			return;
		}

		Map<K, PendingMessage<P, K>> messages = pendingByPeer.computeIfAbsent(peer,
				ignored -> new HashMap<>());
		PendingMessage<P, K> previous = messages.remove(messageType);
		if (previous != null) {
			removePending(previous);
		}

		PendingMessage<P, K> pending = new PendingMessage<>(peer, messageType, payload);
		messages.put(messageType, pending);
		sendAttempt(pending);
	}

	synchronized void onSendSucceeded(int messageId) {
		PendingMessage<P, K> pending = pendingById.remove(messageId);
		if (pending == null || pending.messageId != messageId || !isCurrent(pending)) {
			return;
		}
		removePending(pending);
	}

	synchronized void onSendFailed(int messageId) {
		PendingMessage<P, K> pending = pendingById.remove(messageId);
		if (pending == null || pending.messageId != messageId || !isCurrent(pending)) {
			return;
		}
		scheduleRetry(pending);
	}

	synchronized void cancelPeer(P peer) {
		Map<K, PendingMessage<P, K>> messages = pendingByPeer.remove(peer);
		if (messages == null) {
			return;
		}
		for (PendingMessage<P, K> pending : messages.values()) {
			cancelAttempt(pending);
		}
	}

	synchronized void close() {
		closed = true;
		for (Map<K, PendingMessage<P, K>> messages : pendingByPeer.values()) {
			for (PendingMessage<P, K> pending : messages.values()) {
				cancelAttempt(pending);
			}
		}
		pendingByPeer.clear();
		pendingById.clear();
	}

	private void sendAttempt(PendingMessage<P, K> pending) {
		if (closed || !isCurrent(pending)) {
			return;
		}

		pending.retryTask = null;
		pending.attempts++;
		pending.messageId = allocateMessageId();
		pendingById.put(pending.messageId, pending);
		try {
			sender.send(pending.peer, pending.messageId, pending.payload);
		} catch (RuntimeException e) {
			pendingById.remove(pending.messageId);
			scheduleRetry(pending);
		}
	}

	private void scheduleRetry(PendingMessage<P, K> pending) {
		if (pending.attempts >= MAX_SEND_ATTEMPTS) {
			removePending(pending);
			exhaustedListener.onAttemptsExhausted(pending.peer, pending.messageType,
					pending.attempts);
			return;
		}

		Runnable retryTask = () -> retry(pending);
		pending.retryTask = retryTask;
		if (!scheduler.schedule(retryTask, RETRY_DELAY_MS)) {
			removePending(pending);
			exhaustedListener.onAttemptsExhausted(pending.peer, pending.messageType,
					pending.attempts);
		}
	}

	private synchronized void retry(PendingMessage<P, K> pending) {
		if (closed || !isCurrent(pending) || pending.retryTask == null) {
			return;
		}
		pending.retryTask = null;
		sendAttempt(pending);
	}

	private boolean isCurrent(PendingMessage<P, K> pending) {
		Map<K, PendingMessage<P, K>> messages = pendingByPeer.get(pending.peer);
		return messages != null && messages.get(pending.messageType) == pending;
	}

	private void removePending(PendingMessage<P, K> pending) {
		cancelAttempt(pending);
		Map<K, PendingMessage<P, K>> messages = pendingByPeer.get(pending.peer);
		if (messages != null && messages.get(pending.messageType) == pending) {
			messages.remove(pending.messageType);
			if (messages.isEmpty()) {
				pendingByPeer.remove(pending.peer);
			}
		}
	}

	private void cancelAttempt(PendingMessage<P, K> pending) {
		pendingById.remove(pending.messageId);
		if (pending.retryTask != null) {
			scheduler.cancel(pending.retryTask);
			pending.retryTask = null;
		}
	}

	private int allocateMessageId() {
		while (pendingById.containsKey(nextMessageId)) {
			advanceMessageId();
		}
		int messageId = nextMessageId;
		advanceMessageId();
		return messageId;
	}

	private void advanceMessageId() {
		nextMessageId = nextMessageId == Integer.MAX_VALUE ? 1 : nextMessageId + 1;
	}

	private static final class PendingMessage<P, K> {
		private final P peer;
		private final K messageType;
		private final byte[] payload;
		private int attempts;
		private int messageId;
		private Runnable retryTask;

		private PendingMessage(P peer, K messageType, byte[] payload) {
			this.peer = peer;
			this.messageType = messageType;
			this.payload = payload;
		}
	}
}
