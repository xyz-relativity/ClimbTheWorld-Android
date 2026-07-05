package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import java.util.Arrays;

public class TransportMessage {
	private static final String COMMAND_SEPARATOR = ":";
	public final Command command;
	public final String[] message;

	private TransportMessage(Command command, String... message) {
		this.command = command;
		this.message = message;
	}

	public static TransportMessage fromData(byte[] data) {
		return fromData(new String(data));
	}

	public static TransportMessage fromData(String data) {
		String[] split = data.split(COMMAND_SEPARATOR);

		return new TransportMessage(Command.valueOf(split[0]),
				Arrays.copyOfRange(split, 1, split.length));
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
