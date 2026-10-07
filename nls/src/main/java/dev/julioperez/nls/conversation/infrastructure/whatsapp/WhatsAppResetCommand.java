package dev.julioperez.nls.conversation.infrastructure.whatsapp;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

final class WhatsAppResetCommand {
    private static final Set<String> COMMANDS = Set.of(
            "reiniciar", "/reiniciar", "reiniciar chat", "/reiniciar chat",
            "reiniciar conversacion", "/reiniciar conversacion", "reset", "/reset");

    private WhatsAppResetCommand() {
    }

    static boolean matches(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = Normalizer.normalize(message.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[.!?¡¿]+$", "")
                .strip()
                .replaceAll("\\s+", " ");
        return COMMANDS.contains(normalized);
    }
}
