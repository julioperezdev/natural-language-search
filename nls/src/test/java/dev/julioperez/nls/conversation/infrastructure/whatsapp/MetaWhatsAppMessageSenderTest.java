package dev.julioperez.nls.conversation.infrastructure.whatsapp;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MetaWhatsAppMessageSenderTest {
    @Test
    void sendsRepliesThroughTheConfiguredCloudApiPhoneNumber() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WhatsAppProperties properties = new WhatsAppProperties(true, "secret", "https://graph.facebook.com",
                "v25.0", "business-1", "phone-1", "", Duration.ofSeconds(2), Duration.ofSeconds(5));
        WhatsAppMessageSender sender = new MetaWhatsAppMessageSender(
                builder.baseUrl(properties.graphApiBaseUrl()).build(), properties,
                () -> new WhatsAppSecrets("synthetic-access-token", "verify", "app-secret"));

        server.expect(requestTo("https://graph.facebook.com/v25.0/phone-1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer synthetic-access-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"messaging_product":"whatsapp","to":"5491111111111","type":"text",
                         "text":{"body":"Encontré una opción."}}
                        """))
                .andRespond(withSuccess());

        sender.sendText("5491111111111", "Encontré una opción.");

        server.verify();
    }
}
