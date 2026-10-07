package dev.julioperez.nls.conversation.infrastructure.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public final class WhatsAppWebhookSignatureVerifier {
    private static final String SIGNATURE_PREFIX = "sha256=";

    public boolean isValid(byte[] rawBody, String signature, String appSecret) {
        if (rawBody == null || signature == null || appSecret == null || appSecret.isBlank()
                || !signature.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = (SIGNATURE_PREFIX + HexFormat.of().formatHex(mac.doFinal(rawBody)))
                    .getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII));
        } catch (java.security.GeneralSecurityException exception) {
            return false;
        }
    }
}
