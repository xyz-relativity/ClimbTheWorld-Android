package com.climbtheworld.app.walkietalkie.transport;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class TransportUtilities {
	public static String computeDigest(String message) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return Base64.encodeToString(md.digest(message.getBytes(StandardCharsets.UTF_8)),
					Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
