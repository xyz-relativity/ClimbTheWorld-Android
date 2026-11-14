package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;

public class ChannelKeysTest {
	private static final Locale[] LOCALES = {Locale.forLanguageTag("tr"),
			Locale.forLanguageTag("az"), Locale.forLanguageTag("lt"), Locale.US, Locale.FRANCE};

	private final Locale defaultLocale = Locale.getDefault();

	@After
	public void restoreLocale() {
		Locale.setDefault(defaultLocale);
	}

	// The same text as TransportUtilities.computeDigest, which needs Android's Base64.
	private static String digest(String channel) throws NoSuchAlgorithmException {
		byte[] hash = MessageDigest.getInstance("SHA-256")
				.digest(("ctw.walkietalkie." + channel).getBytes(StandardCharsets.UTF_8));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
	}

	@Test
	public void sameNameAndPmkInEveryLocale() throws NoSuchAlgorithmException {
		for (Locale locale : LOCALES) {
			Locale.setDefault(locale);
			String welcome = ChannelKeys.encryptionKey(digest("Welcome"));
			assertEquals(locale.toString(), "PVXGUSB9", ChannelKeys.serviceName(welcome));
			assertEquals(locale.toString(), "BXXBWNKR",
					ChannelKeys.serviceName(ChannelKeys.encryptionKey(digest(""))));
			byte[] pmk = ChannelKeys.pmk(welcome);
			assertEquals(locale.toString(), 32, pmk.length);
			assertArrayEquals(locale.toString(),
					welcome.substring(0, 32).getBytes(StandardCharsets.US_ASCII), pmk);
		}
	}

	@Test
	public void unchangedFromReleasedVersionsOutsideTurkish() throws NoSuchAlgorithmException {
		Locale.setDefault(Locale.US);
		for (int i = 0; i < 5000; i++) {
			String digest = digest("channel " + i);
			String released = digest.toUpperCase();
			assertEquals(released, ChannelKeys.encryptionKey(digest));
			assertEquals(released.substring(0, 8),
					ChannelKeys.serviceName(ChannelKeys.encryptionKey(digest)));
			assertArrayEquals(released.substring(0, 32).getBytes(StandardCharsets.UTF_8),
					ChannelKeys.pmk(ChannelKeys.encryptionKey(digest)));
		}
	}
}
