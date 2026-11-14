package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The channel's key, Wi-Fi Aware service name and PMK, built from the digest of the channel name.
 * They go on the air, so they must be the same bytes on every phone whatever its language.
 */
final class ChannelKeys {
	private ChannelKeys() {
	}

	static String encryptionKey(String digest) {
		// Locale.ROOT: in Turkish or Azerbaijani the default locale makes 'i' an 'İ', which gives
		// another service name and a PMK longer than 32 bytes.
		return digest.toUpperCase(Locale.ROOT);
	}

	static String serviceName(String encryptionKey) {
		return encryptionKey.substring(0, 8);
	}

	static byte[] pmk(String encryptionKey) {
		return encryptionKey.substring(0, 32).getBytes(StandardCharsets.UTF_8);
	}
}
