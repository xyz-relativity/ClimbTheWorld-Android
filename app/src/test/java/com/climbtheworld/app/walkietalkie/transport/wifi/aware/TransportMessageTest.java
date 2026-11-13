package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
		// A separator in the callsign cuts it the same way in the two messages that carry it.
		for (String callsign : new String[]{"Adam", "Adam B", "Adam:B", "Ad\u00e9la"}) {
			PubSubID instance = TransportMessage.fromData(TransportMessage.buildMessage(
							TransportMessage.Command.INSTANCE, DEVICE.toString(), callsign))
					.toPubSubID(PubSubID.UNKNOWN_DISTANCE);
			String said = TransportMessage.fromData(TransportMessage.buildMessage(
					TransportMessage.Command.CALLSIGN, callsign)).toCallsign();

			assertEquals(instance.callsign, said);
		}
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
		assertEquals(0, message.message.length);
	}

	@Test
	public void unknownCommandIsNoMessage() {
		// As a version without the CALLSIGN command reads it, and this one a newer command.
		assertNull(TransportMessage.fromData("NEWER:Adam".getBytes(StandardCharsets.UTF_8)));
		assertNull(TransportMessage.fromData("NEWER"));
		assertNull(TransportMessage.fromData("callsign:Adam"));
		assertNull(TransportMessage.fromData(""));
		assertNull(TransportMessage.fromData((byte[]) null));
	}
}
