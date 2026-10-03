package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import java.util.Arrays;
import java.util.UUID;

public class TransportMessage {
	private static final String COMMAND_SEPARATOR = ":";
	public final Command command;
	public final String[] message;

	private TransportMessage(Command command, String... message) {
		this.command = command;
		this.message = message;
	}

	/**
	 * Returns null when the data is not a message this version understands, for example an
	 * unknown command from a newer version or another app using the same service name.
	 */
	public static TransportMessage fromData(byte[] data) {
		if (data == null) {
			return null;
		}
		return fromData(new String(data));
	}

	public static TransportMessage fromData(String data) {
		String[] split = data.split(COMMAND_SEPARATOR);

		Command command;
		try {
			command = Command.valueOf(split[0]);
		} catch (IllegalArgumentException e) {
			return null;
		}
		return new TransportMessage(command, Arrays.copyOfRange(split, 1, split.length));
	}

	/**
	 * The sender of an INSTANCE message, or null when the message is not a well formed one.
	 */
	public PubSubID toPubSubID(double distanceMeters) {
		if (command != Command.INSTANCE || message.length < 2) {
			return null;
		}
		try {
			return new PubSubID(UUID.fromString(message[0]), message[1], distanceMeters);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public static byte[] buildMessage(Command command) {
		return buildMessage(command, "");
	}

	public static byte[] buildMessage(Command command, String... message) {
		StringBuilder result = new StringBuilder(command.toString());
		for (String msg : message) {
			result.append(COMMAND_SEPARATOR).append(msg);
		}
		return result.toString().getBytes();
	}

	public enum Command {
		INSTANCE,
		READY
	}
}
