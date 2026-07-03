package com.example.oktaapp;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okta.jwt.AccessTokenVerifier;
import com.okta.jwt.Jwt;
import com.okta.jwt.JwtVerifiers;
import com.okta.jwt.JwtVerificationException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Echoes back selected contents of a Lambda Function URL request
 * (API Gateway v2 HTTP event format). Requires a valid Okta-issued
 * bearer token; requests without one get a 401.
 *
 * OKTA_ISSUER must be a custom authorization server (e.g.
 * https://org.okta.com/oauth2/default) — org authorization server
 * tokens are opaque and cannot be verified locally.
 */
public class OktaAppLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AccessTokenVerifier verifier = JwtVerifiers.accessTokenVerifierBuilder()
            .setIssuer(System.getenv("OKTA_ISSUER"))
            .setAudience(System.getenv("OKTA_AUDIENCE"))
            .setConnectionTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        Jwt jwt;
        try {
            String token = bearerToken(event);
            if (token == null) {
                throw new JwtVerificationException("missing bearer token");
            }
            jwt = verifier.decode(token);
        } catch (JwtVerificationException e) {
            context.getLogger().log("rejected request: " + e.getMessage());
            return response(401,
                    Map.of("content-type", "application/json",
                            "www-authenticate", "Bearer realm=\"okta-app-lambda\""),
                    "{\"error\":\"unauthorized\",\"message\":\"a valid Okta bearer token is required\"}");
        }

        Map<String, Object> echo = new LinkedHashMap<>();

        Map<String, Object> requestContext = asMap(event.get("requestContext"));
        Map<String, Object> http = asMap(requestContext.get("http"));

        echo.put("method", http.get("method"));
        echo.put("path", http.get("path"));
        echo.put("sourceIp", http.get("sourceIp"));
        echo.put("userAgent", http.get("userAgent"));
        echo.put("queryStringParameters", event.get("queryStringParameters"));
        echo.put("headers", redactAuthorization(asMap(event.get("headers"))));
        echo.put("body", decodeBody(event));
        echo.put("requestId", context.getAwsRequestId());
        echo.put("caller", callerInfo(jwt.getClaims()));

        try {
            return response(200, Map.of("content-type", "application/json"),
                    MAPPER.writeValueAsString(echo));
        } catch (Exception e) {
            return response(500, Map.of("content-type", "application/json"),
                    "{\"error\":\"failed to serialize echo response\"}");
        }
    }

    private static String bearerToken(Map<String, Object> event) {
        for (Map.Entry<String, Object> entry : asMap(event.get("headers")).entrySet()) {
            if ("authorization".equalsIgnoreCase(entry.getKey())
                    && entry.getValue() instanceof String s
                    && s.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return s.substring(7).trim();
            }
        }
        return null;
    }

    private static Map<String, Object> callerInfo(Map<String, Object> claims) {
        Map<String, Object> caller = new LinkedHashMap<>();
        caller.put("sub", claims.get("sub"));
        if (claims.containsKey("cid")) {
            caller.put("cid", claims.get("cid"));
        }
        return caller;
    }

    private static Map<String, Object> redactAuthorization(Map<String, Object> headers) {
        Map<String, Object> redacted = new LinkedHashMap<>(headers);
        redacted.replaceAll((name, value) ->
                "authorization".equalsIgnoreCase(name) ? "<redacted>" : value);
        return redacted;
    }

    private static Map<String, Object> response(int statusCode, Map<String, String> headers, String body) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("statusCode", statusCode);
        response.put("headers", headers);
        response.put("body", body);
        return response;
    }

    private static Object decodeBody(Map<String, Object> event) {
        Object body = event.get("body");
        if (body instanceof String s && Boolean.TRUE.equals(event.get("isBase64Encoded"))) {
            return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }
}
