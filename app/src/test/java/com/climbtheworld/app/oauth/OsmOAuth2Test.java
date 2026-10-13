package com.climbtheworld.app.oauth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.security.SecureRandom;

// The real org.json, which plain unit tests only have as a stub.
@RunWith(RobolectricTestRunner.class)
public class OsmOAuth2Test {
	@Test
	public void challengeMatchesTheRfcExample() {
		// RFC 7636, appendix B.
		assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
				OsmOAuth2.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
	}

	@Test
	public void secretsAreLongRandomAndUrlSafe() {
		SecureRandom random = new SecureRandom();
		String first = OsmOAuth2.newSecret(random);

		assertEquals(64, first.length());
		assertTrue(first.matches("[A-Za-z0-9_-]+"));
		assertNotEquals(first, OsmOAuth2.newSecret(random));
	}

	@Test
	public void authorizationUrlCarriesTheChallengeNotTheVerifier() {
		String url = OsmOAuth2.authorizationUrl("https://www.openstreetmap.org/", "client id",
				"dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", "state1");

		assertTrue(url.startsWith("https://www.openstreetmap.org/oauth2/authorize?response_type=code"));
		assertTrue(url.contains("&client_id=client%20id"));
		assertTrue(url.contains("&redirect_uri=climbtheworld%3A%2F%2Foauth"));
		assertTrue(url.contains("&scope=read_prefs%20write_api"));
		assertTrue(url.contains("&state=state1"));
		assertTrue(url.contains("&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"));
		assertTrue(url.endsWith("&code_challenge_method=S256"));
		assertTrue(!url.contains("dBjftJeZ4CVP"));
	}

	@Test
	public void tokenRequestSendsTheVerifier() {
		assertEquals("grant_type=authorization_code&code=a%2Fb&redirect_uri=climbtheworld%3A%2F%2Foauth"
						+ "&client_id=id&code_verifier=v",
				OsmOAuth2.tokenRequestBody("id", "a/b", "v"));
	}

	@Test
	public void accessTokenIsReadFromTheResponse() throws JSONException {
		assertEquals("abc", OsmOAuth2.accessToken("{\"access_token\":\"abc\",\"token_type\":\"Bearer\","
				+ "\"scope\":\"read_prefs write_api\",\"created_at\":1}"));
		assertThrows(JSONException.class, () -> OsmOAuth2.accessToken("{\"error\":\"invalid_grant\"}"));
	}
}
