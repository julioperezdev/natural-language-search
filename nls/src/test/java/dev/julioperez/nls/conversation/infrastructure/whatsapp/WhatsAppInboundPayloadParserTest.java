package dev.julioperez.nls.conversation.infrastructure.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class WhatsAppInboundPayloadParserTest {
    @Test
    void acceptsAnySenderWhenAllowedRecipientIsEmptyAndIgnoresStatusEvents() {
        WhatsAppInboundPayloadParser parser = new WhatsAppInboundPayloadParser(
                new ObjectMapper(), properties(""));
        String payload = """
                {"object":"whatsapp_business_account","entry":[{"id":"business-1","changes":[
                  {"field":"messages","value":{"metadata":{"phone_number_id":"phone-1"},"messages":[
                    {"id":"wamid.1","from":"5491111111111","type":"text","text":{"body":"quiero una remera"}},
                    {"id":"wamid.2","from":"5492222222222","type":"image"}]}}
                ]}]}
                """;

        assertThat(parser.parse(payload.getBytes())).containsExactly(
                new WhatsAppInboundTextMessage("wamid.1", "5491111111111", "quiero una remera"));
    }

    @Test
    void honorsTheOptionalSenderAllowList() {
        WhatsAppInboundPayloadParser parser = new WhatsAppInboundPayloadParser(
                new ObjectMapper(), properties("5493333333333"));
        String payload = """
                {"object":"whatsapp_business_account","entry":[{"id":"business-1","changes":[
                  {"field":"messages","value":{"metadata":{"phone_number_id":"phone-1"},"messages":[
                    {"id":"wamid.1","from":"5491111111111","type":"text","text":{"body":"hola"}}]}}
                ]}]}
                """;

        assertThat(parser.parse(payload.getBytes())).isEmpty();
    }

    private static WhatsAppProperties properties(String allowedRecipient) {
        return new WhatsAppProperties(true, "secret", "https://graph.facebook.com", "v25.0",
                "business-1", "phone-1", allowedRecipient, Duration.ofSeconds(2), Duration.ofSeconds(5));
    }
}
