package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

public class TransportMessage {
	private static final String COMMAND_SEPARATOR = "-";
	public final Command command;
	public final String message;

	private TransportMessage(Command command, String message) {
		this.command = command;
		this.message = message;
	}

	public static TransportMessage fromString(String data) {
		String[] split = data.split(COMMAND_SEPARATOR);

		return new TransportMessage(Command.valueOf(split[0]), split[1]);
	}

	public static byte[] buildMessage(Command command, String message) {
		return (command + COMMAND_SEPARATOR + message).getBytes();
	}

	public enum Command {
		CALLSIGH
	}
}
