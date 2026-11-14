package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import java.util.UUID;

public class TransportMessage {
	private static final String COMMAND_SEPARATOR = ":";
	public final Command command;
	/**
	 * What follows the command and its separator, as it was sent, empty when nothing does. The
	 * fields are read from it by the command's own method, see toPubSubID and toCallsign: only
	 * the command knows which separators are between its fields, and which are in its last one.
	 */
	public final String arguments;

	private TransportMessage(Command command, String arguments) {
		this.command = command;
		this.arguments = arguments;
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
		int commandEnd = data.indexOf(COMMAND_SEPARATOR);

		Command command;
		try {
			command = Command.valueOf(commandEnd < 0 ? data : data.substring(0, commandEnd));
		} catch (IllegalArgumentException e) {
			return null;
		}
		return new TransportMessage(command,
				commandEnd < 0 ? "" : data.substring(commandEnd + COMMAND_SEPARATOR.length()));
	}

	/**
	 * The sender of an INSTANCE message, or null when the message is not a well formed one.
	 */
	public PubSubID toPubSubID(double distanceMeters) {
		if (command != Command.INSTANCE) {
			return null;
		}
		int idEnd = arguments.indexOf(COMMAND_SEPARATOR);
		if (idEnd < 0) {
			return null;
		}
		String callsign = lastField(idEnd + COMMAND_SEPARATOR.length());
		if (callsign == null) {
			return null;
		}
		try {
			return new PubSubID(UUID.fromString(arguments.substring(0, idEnd)), callsign,
					distanceMeters);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * The callsign of a CALLSIGN message, or null when the message is not a well formed one. It
	 * is read as the callsign of an INSTANCE message is, so that a peer has the same callsign
	 * whichever of the two said it.
	 */
	public String toCallsign() {
		if (command != Command.CALLSIGN) {
			return null;
		}
		return lastField(0);
	}

	/**
	 * The last field of the message, from where it starts in the arguments, or null when there
	 * is none. It is all that is left of the text, and not only what comes before the next
	 * separator: the callsign is any text, and one with a separator in it is sent as it is. So
	 * no field can be added after a callsign, which the versions reading it this way would take
	 * for the end of it: what is to be said besides goes in a command of its own, which the
	 * versions without it drop.
	 * <p>
	 * An empty one is none, as it has always been for the versions that cut the text at every
	 * separator: they drop an INSTANCE message without a callsign.
	 */
	private String lastField(int start) {
		return start < arguments.length() ? arguments.substring(start) : null;
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
		READY,
		// Only the sender's callsign, to a peer that knows the sender already. It is no part of
		// the data path's handshake, and the versions without it drop it as an unknown command.
		CALLSIGN
	}
}
