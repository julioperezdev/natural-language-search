package dev.julioperez.nls.conversation.infrastructure.whatsapp;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookControllerTest {
    private static final String APP_SECRET = "synthetic-meta-app-secret";

    @Mock
    WhatsAppSecretsProvider secretsProvider;

    @Mock
    WhatsAppInboundPayloadParser payloadParser;

    @Mock
    WhatsAppInboundConversationHandler handler;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new WhatsAppWebhookController(
                secretsProvider, new WhatsAppWebhookSignatureVerifier(), payloadParser, handler)).build();
    }

    @Test
    void returnsTheChallengeOnlyForTheConfiguredVerificationToken() throws Exception {
        when(secretsProvider.get()).thenReturn(new WhatsAppSecrets("token", "expected-verify-token", APP_SECRET));

        mvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "expected-verify-token")
                        .param("hub.challenge", "challenge-value"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge-value"));

        mvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong-token")
                        .param("hub.challenge", "challenge-value"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatesAndDispatchesInboundTextMessages() throws Exception {
        byte[] body = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
        WhatsAppInboundTextMessage message = new WhatsAppInboundTextMessage("wamid.synthetic", "5491111111111", "busco remeras");
        when(secretsProvider.get()).thenReturn(new WhatsAppSecrets("token", "verify", APP_SECRET));
        when(payloadParser.parse(body)).thenReturn(List.of(message));

        mvc.perform(post("/webhook/whatsapp")
                        .contentType("application/json")
                        .header("X-Hub-Signature-256", signature(body, APP_SECRET))
                        .content(body))
                .andExpect(status().isOk());

        verify(handler).handle(message);
    }

    @Test
    void rejectsInvalidSignaturesBeforeParsingOrProcessing() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        when(secretsProvider.get()).thenReturn(new WhatsAppSecrets("token", "verify", APP_SECRET));

        mvc.perform(post("/webhook/whatsapp")
                        .contentType("application/json")
                        .header("X-Hub-Signature-256", "sha256=invalid")
                        .content(body))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(payloadParser, handler);
    }

    private static String signature(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
