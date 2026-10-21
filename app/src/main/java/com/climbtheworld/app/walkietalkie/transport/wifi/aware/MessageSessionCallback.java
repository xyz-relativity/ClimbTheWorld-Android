package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PeerHandle;
import android.util.Log;

/**
 * What the publisher's and the subscriber's discovery sessions do alike: their sends are
 * tracked by their message sender, and the messages they get are parsed before being handled.
 */
abstract class MessageSessionCallback extends DiscoverySessionCallback {
	private final String tag;
	/** Names the session in the log, like "Publish &lt;uuid&gt;". */
	private final String logName;

	MessageSessionCallback(String tag, String logName) {
		this.tag = tag;
		this.logName = logName;
	}

	/** The session's message sender, null before the session started and once it is closed. */
	protected abstract ReliableMessageSender<PeerHandle, TransportMessage.Command> messageSender();

	/** A well-formed message from the peer. */
	protected abstract void onMessage(PeerHandle peerHandle, TransportMessage message);

	@Override
	public void onMessageSendSucceeded(int messageId) {
		super.onMessageSendSucceeded(messageId);
		ReliableMessageSender<PeerHandle, TransportMessage.Command> sender = messageSender();
		if (sender != null) {
			sender.onSendSucceeded(messageId);
		}
	}

	@Override
	public void onMessageSendFailed(int messageId) {
		super.onMessageSendFailed(messageId);
		ReliableMessageSender<PeerHandle, TransportMessage.Command> sender = messageSender();
		if (sender != null) {
			sender.onSendFailed(messageId);
		}
	}

	@Override
	public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
		super.onMessageReceived(peerHandle, message);

		Log.d(tag, logName + " message received: " + new String(message));

		TransportMessage transportMessage = TransportMessage.fromData(message);
		if (transportMessage == null) {
			Log.w(tag, "Ignoring malformed message.");
			return;
		}
		onMessage(peerHandle, transportMessage);
	}
}
