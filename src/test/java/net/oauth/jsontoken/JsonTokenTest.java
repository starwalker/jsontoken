/**
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package net.oauth.jsontoken;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.oauth.jsontoken.crypto.HmacSHA256Signer;
import net.oauth.jsontoken.crypto.RsaSHA256Signer;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import com.fasterxml.jackson.core.JsonParseException;
import java.security.SignatureException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by steve on 12/09/14.
 */
public class JsonTokenTest extends JsonTokenTestBase {
    private static final int SKEW_IN_MIN = 1;

    public static String TOKEN_STRING = "eyJhbGciOiJIUzI1NiIsImtpZCI6ImtleTIifQ.eyJpc3MiOiJnb29nbGUuY29tIiwiYmFyIjoxNSwiZm9vIjoic29tZSB2YWx1ZSIsImF1ZCI6Imh0dHA6Ly93d3cuZ29vZ2xlLmNvbSIsImlhdCI6MTI3NjY2OTcyMiwiZXhwIjoxMjc2NjY5NzIyfQ.jKcuP6BR_-cKpQv2XdFLguYgOxw4ahkZiqjcgrQcm70";
    public static String TOKEN_STRING_ISSUER_NULL = "eyJhbGciOiJIUzI1NiIsImtpZCI6ImtleTIifQ.eyJpc3MiOm51bGwsImJhciI6MTUsImZvbyI6InNvbWUgdmFsdWUiLCJhdWQiOiJodHRwOi8vd3d3Lmdvb2dsZS5jb20iLCJpYXQiOjEyNzY2Njk3MjIsImV4cCI6MTI3NjY2OTcyMn0.jKcuP6BR_-cKpQv2XdFLguYgOxw4ahkZiqjcgrQcm70";
    public static String TOKEN_STRING_BAD_SIG = "eyJhbGciOiJIUzI1NiIsImtpZCI6ImtleTIifQ.eyJpc3MiOiJnb29nbGUuY29tIiwiYmFyIjoxNSwiZm9vIjoic29tZSB2YWx1ZSIsImF1ZCI6Imh0dHA6Ly93d3cuZ29vZ2xlLmNvbSIsImlhdCI6MTI3NjY2OTcyMiwiZXhwIjoxMjc2NjY5NzIyfQ.jKcuP6BR_";
    public static String TOKEN_STRING_2PARTS = "eyJhbGciOiJIUzI1NiIsImtpZCI6ImtleTIifQ.eyJpc3MiOiJnb29nbGUuY29tIiwiYmFyIjoxNSwiZm9vIjoic29tZSB2YWx1ZSIsImF1ZCI6Imh0dHA6Ly93d3cuZ29vZ2xlLmNvbSIsImlhdCI6MTI3NjY2OTcyMiwiZXhwIjoxMjc2NjY5NzIyfQ";
    public static String TOKEN_STRING_EMPTY_SIG = "eyJhbGciOiJIUzI1NiIsImtpZCI6ImtleTIifQ.eyJpc3MiOiJnb29nbGUuY29tIiwiYmFyIjoxNSwiZm9vIjoic29tZSB2YWx1ZSIsImF1ZCI6Imh0dHA6Ly93d3cuZ29vZ2xlLmNvbSIsImlhdCI6MTI3NjY2OTcyMiwiZXhwIjoxMjc2NjY5NzIyfQ.";
    public static String TOKEN_STRING_CORRUPT_HEADER = "0yJ0bGci0iJIUzI0NiIsIm0pZCI60mtleT0ifQ.eyJpc3MiOiJnb29nbGUuY29tIiwiYmFyIjoxNSwiZm9vIjoic29tZSB2YWx1ZSIsImF1ZCI6Imh0dHA6Ly93d3cuZ29vZ2xlLmNvbSIsImlhdCI6MTI3NjY2OTcyMiwiZXhwIjoxMjc2NjY5NzIyfQ.jKcuP6BR_-cKpQv2XdFLguYgOxw4ahkZiqjcgrQcm70";
    public static String TOKEN_STRING_CORRUPT_PAYLOAD = "eyJhbGciOiJIUzI1NiIsImtpZCI6ImtleTIifQ.eyJpc3&&&&&XtOiJnb290bGUuY20tIiwiYmFyIjoxNSwiZm9vIjoic290ZSB2YWx1ZSIsImF1ZCI6Imh0dHA6Ly93d3cuZ29vZ2xlLmNvbSIsImlhdCI6MTI3NjY2OTcyMiwiZXhwIjoxMjc2NjY5NzIyfQ.jKcuP6BR_-cKpQv2XdFLguYgOxw4ahkZiqjcgrQcm70";
    public FakeClock clock = new FakeClock(SKEW_IN_MIN);

    @Override
    public void setUp() throws Exception {
        super.setUp();
        clock.setNow(Instant.ofEpochMilli(1276669722000L));
    }

    public void testDeserialize() throws Exception {
        JsonTokenParser parser = new JsonTokenParser(clock, locators, new IgnoreAudience());
        JsonToken token = parser.deserialize(TOKEN_STRING);

        assertEquals("google.com", token.getIssuer());
        assertEquals(15, token.getParamAsLong("bar").longValue());
        assertEquals("some value", token.getParamAsString("foo"));
    }

    public void testCreateJsonToken() throws Exception {
        HmacSHA256Signer signer = new HmacSHA256Signer("google.com", "key2", SYMMETRIC_KEY);

        JsonToken token = new JsonToken(signer, clock);
        token.setParam("bar", 15);
        token.setParam("foo", "some value");
        token.setAudience("http://www.google.com");
        token.setIssuedAt(clock.now());
        token.setExpiration(clock.now().plusMillis(60));

        assertEquals(TOKEN_STRING, token.serializeAndSign());
    }

    private void checkTimeFrame(Instant issuedAt, Instant expiration) throws Exception {
        HmacSHA256Signer signer = new HmacSHA256Signer("google.com", "key2", SYMMETRIC_KEY);
        JsonToken token = new JsonToken(signer, clock);
        if (issuedAt != null) {
            token.setIssuedAt(issuedAt);
        }
        if (expiration != null) {
            token.setExpiration(expiration);
        }
        token.setAudience("http://www.google.com");

        JsonToken verifiedToken = new JsonTokenParser(clock, locators, new IgnoreAudience())
                .verifyAndDeserialize(token.serializeAndSign());
        assertEquals(issuedAt, verifiedToken.getIssuedAt());
        assertEquals(expiration, verifiedToken.getExpiration());
    }

    private void checkTimeFrameIllegalStateException(Instant issuedAt, Instant expiration)
            throws Exception {
        try {
            checkTimeFrame(issuedAt, expiration);
            fail("IllegalStateException should be thrown.");
        } catch (IllegalStateException e) {
            // Pass.
        }
    }

    public void testIssuedAtAfterExpiration() throws Exception {
        Instant issuedAt = clock.now();
        Instant expiration = issuedAt.minusSeconds(1);
        checkTimeFrameIllegalStateException(issuedAt, expiration);
    }

    public void testIssueAtSkew() throws Exception {
        Instant issuedAt = clock.now().plusSeconds(SKEW_IN_MIN * 60 -1);
        Instant expiration = issuedAt.plusSeconds(1);
        checkTimeFrame(issuedAt, expiration);
    }
    public void testIssueAtTooMuchSkew() throws Exception {
        Instant issuedAt = clock.now().plusSeconds(SKEW_IN_MIN * 60 + 1);
        Instant expiration = issuedAt.plusSeconds(1);
        checkTimeFrameIllegalStateException(issuedAt, expiration);
    }

    public void testExpirationSkew() throws Exception {
        Instant expiration = clock.now().minusSeconds(SKEW_IN_MIN * 60 -1);
        Instant issuedAt = expiration.minusSeconds(1);
        checkTimeFrame(issuedAt, expiration);
    }

    public void testExpirationTooMuchSkew() throws Exception {
        Instant expiration = clock.now().minusSeconds(SKEW_IN_MIN * 60 + 1);
        Instant issuedAt = expiration.minusSeconds(1);
        checkTimeFrameIllegalStateException(issuedAt, expiration);
    }

    public void testIssuedAtNull() throws Exception {
        Instant expiration = clock.now().minusSeconds(SKEW_IN_MIN * 60 - 1);
        checkTimeFrame(null, expiration);
    }

    public void testExpirationNull() throws Exception {
        Instant issuedAt = clock.now().plusSeconds(SKEW_IN_MIN * 60 - 1);
        checkTimeFrame(issuedAt, null);
    }

    public void testIssueAtNullExpirationNull() throws Exception {
        checkTimeFrame(null, null);
    }

    public void testFutureToken() throws Exception {
        Instant issuedAt = clock.now().plusSeconds(SKEW_IN_MIN * 60 + 1);
        Instant expiration = issuedAt.plusSeconds(24 * 60 * 60);
        checkTimeFrameIllegalStateException(issuedAt, expiration);
    }

    public void testPastToken() throws Exception {
        Instant expiration = clock.now().minusSeconds(SKEW_IN_MIN * 60 + 1);
        Instant issuedAt = expiration.minusSeconds(24 * 60 * 60);
        checkTimeFrameIllegalStateException(issuedAt, expiration);
    }

    public void testJsonNull() throws Exception {
        JsonTokenParser parser = new JsonTokenParser(null, null);
        JsonToken token = parser.deserialize(TOKEN_STRING_ISSUER_NULL);
        assertNull(token.getIssuer());
    }

    public void testDeserializeInvalidToken() throws Exception {
        JsonTokenParser parser = new JsonTokenParser(clock, locators, new IgnoreAudience());
        JsonToken token1 = parser.deserialize(TOKEN_STRING_BAD_SIG);
        deserializeAndExpectIllegalArgument(parser, TOKEN_STRING_2PARTS);
        deserializeAndExpectIllegalArgument(parser, TOKEN_STRING_EMPTY_SIG);
    }

    public void testDeserializeCorruptJson() throws Exception {
        JsonTokenParser parser = new JsonTokenParser(clock, locators, new IgnoreAudience());
        try {
            parser.deserialize(TOKEN_STRING_CORRUPT_HEADER);
            fail("Expected JsonParseException");
        } catch (JsonParseException e) {
            // no-op
        }
        try {
            parser.deserialize(TOKEN_STRING_CORRUPT_PAYLOAD);
            fail("Expected JsonParseException");
        } catch (JsonParseException e) {
            // no-op
        }
    }


    private void deserializeAndExpectIllegalArgument(JsonTokenParser parser,
                                                     String tokenString) throws Exception {
        try {
            parser.deserialize(tokenString);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // no-op
        } catch (IllegalStateException e) {
            // no-op
        }
    }

    public void testVerifyAndDeserialize() throws Exception {
        JsonTokenParser parser = new JsonTokenParser(clock, locators, new IgnoreAudience());
        JsonToken token = parser.verifyAndDeserialize(TOKEN_STRING);

        assertEquals("google.com", token.getIssuer());
        assertEquals(15, token.getParamAsLong("bar").longValue());
        assertEquals("some value", token.getParamAsString("foo"));
    }

    public static String TOKEN_FROM_RUBY = "eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9.eyJoZWxsbyI6ICJ3b3JsZCJ9.tvagLDLoaiJKxOKqpBXSEGy7SYSifZhjntgm9ctpyj8";

    public void testVerificationOnTokenFromRuby() throws Exception {
        JsonTokenParser parser = new JsonTokenParser(clock, locatorsFromRuby, new IgnoreAudience());
        JsonToken token = parser.verifyAndDeserialize(TOKEN_FROM_RUBY);
    }

    public void testCreateAnotherJsonToken() throws Exception {
        HmacSHA256Signer signer = new HmacSHA256Signer(null, (String) null, "secret".getBytes());

        JsonToken token = new JsonToken(signer, clock);
        token.setParam("hello", "world");
        String encodedToken = token.serializeAndSign();
    }

    public void testPublicKey() throws Exception {

        RsaSHA256Signer signer = new RsaSHA256Signer("google.com", "key1", privateKey);

        JsonToken token = new JsonToken(signer, clock);
        token.setParam("bar", 15);
        token.setParam("foo", "some value");
        token.setExpiration(clock.now().plusMillis(60));

        String tokenString = token.serializeAndSign();

        assertNotNull(token.toString());

        JsonTokenParser parser = new JsonTokenParser(clock, locators, new IgnoreAudience());
        token = parser.verifyAndDeserialize(tokenString);
        assertEquals("google.com", token.getIssuer());
        assertEquals(15, token.getParamAsLong("bar").longValue());
        assertEquals("some value", token.getParamAsString("foo"));

        // now test what happens if we tamper with the token
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> payload = mapper.readValue(StringUtils.newStringUtf8(Base64.decodeBase64(tokenString.split(Pattern.quote("."))[1])),new TypeReference<LinkedHashMap<String,Object>>(){});
        payload.remove("bar");
        payload.put("bar", 14);
        String payloadString = mapper.writeValueAsString(payload);
        String[] parts = tokenString.split("\\.");
        parts[1] = Base64.encodeBase64URLSafeString(payloadString.getBytes());
        assertEquals(3, parts.length);

        String tamperedToken = parts[0] + "." + parts[1] + "." + parts[2];

        try {
            token = parser.verifyAndDeserialize(tamperedToken);
            fail("verification should have failed");
        } catch (SignatureException e) {
            // expected
        }
    }
}
