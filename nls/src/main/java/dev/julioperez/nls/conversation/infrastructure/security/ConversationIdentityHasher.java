package dev.julioperez.nls.conversation.infrastructure.security;

import dev.julioperez.nls.conversation.application.ConversationIdentityKeyUnavailableException;
import dev.julioperez.nls.conversation.domain.ConversationIdentity;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConversationIdentityHasher {
    private static final String ALGORITHM = "HmacSHA256";
    private final String key;

    public ConversationIdentityHasher(
            @Value("${nls.conversation.identity-hmac-key:}") String key) {
        this.key = key == null ? "" : key;
    }

    public String hash(ConversationIdentity identity) {
        if (key.length() < 32) {
            throw new ConversationIdentityKeyUnavailableException();
        }
        String canonical = identity.channel().name() + "\n"
                + canonicalize(identity.channelAccountId()) + "\n"
                + canonicalize(identity.participantId());
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Conversation identity could not be resolved.", exception);
        }
    }

    private static String canonicalize(String value) {
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
    }
}
