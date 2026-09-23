package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

public class ReliableMessageSenderTest {
	@Test
	public void failedMessageRetriesWithUniqueIdsUntilAttemptLimit() {
		ManualScheduler scheduler = new ManualScheduler();
		List<Integer> messageIds = new ArrayList<>();
		AtomicInteger exhaustedAttempts = new AtomicInteger();
		ReliableMessageSender<String, String> sender = new ReliableMessageSender<>(scheduler,
				(peer, messageId, payload) -> messageIds.add(messageId),
				(peer, type, attempts) -> exhaustedAttempts.set(attempts));

		sender.send("peer", "INSTANCE", new byte[]{1});
		for (int attempt = 1; attempt < ReliableMessageSender.MAX_SEND_ATTEMPTS; attempt++) {
			sender.onSendFailed(messageIds.get(messageIds.size() - 1));
			assertTrue(scheduler.runNext());
		}
		sender.onSendFailed(messageIds.get(messageIds.size() - 1));

		assertEquals(ReliableMessageSender.MAX_SEND_ATTEMPTS, messageIds.size());
		assertEquals(messageIds.size(), new HashSet<>(messageIds).size());
		assertEquals(ReliableMessageSender.MAX_SEND_ATTEMPTS, exhaustedAttempts.get());
		assertFalse(scheduler.runNext());
	}

	@Test
	public void successfulMessageIsNotRetried() {
		ManualScheduler scheduler = new ManualScheduler();
		List<Integer> messageIds = new ArrayList<>();
		ReliableMessageSender<String, String> sender = new ReliableMessageSender<>(scheduler,
				(peer, messageId, payload) -> messageIds.add(messageId),
				(peer, type, attempts) -> {
				});

		sender.send("peer", "READY", new byte[]{1});
		int successfulMessageId = messageIds.get(0);
		sender.onSendSucceeded(successfulMessageId);
		sender.onSendFailed(successfulMessageId);

		assertEquals(1, messageIds.size());
		assertFalse(scheduler.runNext());
	}

	@Test
	public void newerMessageReplacesPendingMessageOfSameType() {
		ManualScheduler scheduler = new ManualScheduler();
		List<Integer> messageIds = new ArrayList<>();
		ReliableMessageSender<String, String> sender = new ReliableMessageSender<>(scheduler,
				(peer, messageId, payload) -> messageIds.add(messageId),
				(peer, type, attempts) -> {
				});

		sender.send("peer", "INSTANCE", new byte[]{1});
		int replacedMessageId = messageIds.get(0);
		sender.send("peer", "INSTANCE", new byte[]{2});
		int currentMessageId = messageIds.get(1);
		sender.onSendFailed(replacedMessageId);

		assertNotEquals(replacedMessageId, currentMessageId);
		assertFalse(scheduler.runNext());
	}

	private static final class ManualScheduler implements ReliableMessageSender.Scheduler {
		private final Queue<Runnable> tasks = new ArrayDeque<>();

		@Override
		public boolean schedule(Runnable task, long delayMillis) {
			assertEquals(ReliableMessageSender.RETRY_DELAY_MS, delayMillis);
			return tasks.offer(task);
		}

		@Override
		public void cancel(Runnable task) {
			tasks.remove(task);
		}

		private boolean runNext() {
			Runnable task = tasks.poll();
			if (task == null) {
				return false;
			}
			task.run();
			return true;
		}
	}
}
