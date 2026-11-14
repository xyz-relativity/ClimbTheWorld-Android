package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class TransportMessageTest {
	private static final UUID DEVICE = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Test
	public void callsignMessageCarriesTheCallsign() {
		byte[] data = TransportMessage.buildMessage(TransportMessage.Command.CALLSIGN, "Adam");

		assertArrayEquals("CALLSIGN:Adam".getBytes(StandardCharsets.UTF_8), data);

		TransportMessage message = TransportMessage.fromData(data);
		assertEquals(TransportMessage.Command.CALLSIGN, message.command);
		assertEquals("Adam", message.toCallsign());
		// It is not taken for the message that starts the data path.
		assertNull(message.toPubSubID(PubSubID.UNKNOWN_DISTANCE));
	}

	@Test
	public void callsignIsOnlyReadFromACallsignMessage() {
		TransportMessage instance = TransportMessage.fromData(TransportMessage.buildMessage(
				TransportMessage.Command.INSTANCE, DEVICE.toString(), "Adam"));
		TransportMessage ready = TransportMessage.fromData(
				TransportMessage.buildMessage(TransportMessage.Command.READY));

		assertNull(instance.toCallsign());
		assertNull(ready.toCallsign());
	}

	@Test
	public void callsignMessageWithoutACallsignIsMalformed() {
		assertNull(TransportMessage.fromData("CALLSIGN").toCallsign());
		assertNull(TransportMessage.fromData(
				TransportMessage.buildMessage(TransportMessage.Command.CALLSIGN, "")).toCallsign());
	}

	@Test
	public void bothMessagesGiveTheSameCallsign() {
		// As it was typed, with the separators in it: the callsign is the last field of both.
		for (String callsign : new String[]{"Adam", "Adam B", "Adam:B", "Ad\u00e9la", ":Adam",
				"Adam:", "A::B:", ":", "::", "Adam\nB", " Adam "}) {
			PubSubID instance = TransportMessage.fromData(TransportMessage.buildMessage(
							TransportMessage.Command.INSTANCE, DEVICE.toString(), callsign))
					.toPubSubID(PubSubID.UNKNOWN_DISTANCE);
			String said = TransportMessage.fromData(TransportMessage.buildMessage(
					TransportMessage.Command.CALLSIGN, callsign)).toCallsign();

			assertEquals(DEVICE, instance.uuid);
			assertEquals(callsign, instance.callsign);
			assertEquals(callsign, said);
		}
	}

	@Test
	public void callsignWithASeparatorIsSentAsItAlwaysWas() {
		// Only the reading changed: the released versions, which cut the callsign at its first
		// separator, are sent what they always were, and still read "Adam" in it.
		assertArrayEquals(("INSTANCE:" + DEVICE + ":Adam:B").getBytes(StandardCharsets.UTF_8),
				TransportMessage.buildMessage(TransportMessage.Command.INSTANCE,
						DEVICE.toString(), "Adam:B"));
		assertArrayEquals("CALLSIGN:Adam:B".getBytes(StandardCharsets.UTF_8),
				TransportMessage.buildMessage(TransportMessage.Command.CALLSIGN, "Adam:B"));
	}

	@Test
	public void instanceMessageWithoutACallsignIsMalformed() {
		// An empty callsign stays none, as it is for the released versions.
		assertNull(TransportMessage.fromData("INSTANCE").toPubSubID(PubSubID.UNKNOWN_DISTANCE));
		assertNull(TransportMessage.fromData("INSTANCE:" + DEVICE)
				.toPubSubID(PubSubID.UNKNOWN_DISTANCE));
		assertNull(TransportMessage.fromData(TransportMessage.buildMessage(
						TransportMessage.Command.INSTANCE, DEVICE.toString(), ""))
				.toPubSubID(PubSubID.UNKNOWN_DISTANCE));
	}

	@Test
	public void instanceMessageWithoutAnIdIsMalformed() {
		assertNull(TransportMessage.fromData("INSTANCE::Adam")
				.toPubSubID(PubSubID.UNKNOWN_DISTANCE));
		assertNull(TransportMessage.fromData("INSTANCE:Adam:B")
				.toPubSubID(PubSubID.UNKNOWN_DISTANCE));
		assertNull(TransportMessage.fromData("INSTANCE:::")
				.toPubSubID(PubSubID.UNKNOWN_DISTANCE));
	}

	@Test
	public void longestCallsignFitsTheServiceInfo() {
		// The screen takes 10 characters, see TextViewSwitcher, of 3 bytes each at most, and
		// Wi-Fi Aware 255 bytes of service info or of message.
		String callsign = "\u20ac\u20ac\u20ac\u20ac\u20ac\u20ac\u20ac\u20ac\u20ac\u20ac";
		byte[] data = TransportMessage.buildMessage(TransportMessage.Command.INSTANCE,
				DEVICE.toString(), callsign);

		assertTrue(data.length <= 255);
		assertEquals(callsign,
				TransportMessage.fromData(data).toPubSubID(PubSubID.UNKNOWN_DISTANCE).callsign);
	}

	@Test
	public void instanceMessageGivesTheSender() {
		PubSubID sender = TransportMessage.fromData(TransportMessage.buildMessage(
						TransportMessage.Command.INSTANCE, DEVICE.toString(), "Adam"))
				.toPubSubID(PubSubID.UNKNOWN_DISTANCE);

		assertEquals(DEVICE, sender.uuid);
		assertEquals("Adam", sender.callsign);
		assertEquals(PubSubID.UNKNOWN_DISTANCE, sender.distanceMeters, 0);
	}

	@Test
	public void readyMessageIsReadWithoutArguments() {
		TransportMessage message = TransportMessage.fromData(
				TransportMessage.buildMessage(TransportMessage.Command.READY));

		assertNotNull(message);
		assertEquals(TransportMessage.Command.READY, message.command);
		assertEquals("", message.arguments);
	}

	@Test
	public void unknownCommandIsNoMessage() {
		// As a version without the CALLSIGN command reads it, and this one a newer command.
		assertNull(TransportMessage.fromData("NEWER:Adam".getBytes(StandardCharsets.UTF_8)));
		assertNull(TransportMessage.fromData("NEWER"));
		assertNull(TransportMessage.fromData("callsign:Adam"));
		assertNull(TransportMessage.fromData(""));
		assertNull(TransportMessage.fromData((byte[]) null));
		// Nothing but separators: once an exception, as the split left no command to look at.
		assertNull(TransportMessage.fromData(":"));
		assertNull(TransportMessage.fromData("::"));
		assertNull(TransportMessage.fromData(":Adam"));
	}
}
