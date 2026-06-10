package com.example.guardian.notification;

/**
 * Transport abstraction for delivering notifications to a chat.
 *
 * <p>Implementations hide all transport details (HTTP client, payload format,
 * authentication) behind a single method, so the rest of the application only
 * works with {@link ChatMessage}. New chat backends are added by implementing
 * this interface.
 */
public interface ChatProvider {

    /**
     * Delivers one message to the chat.
     *
     * @param message message to deliver
     * @throws ChatSendException when the message could not be delivered
     */
    void send(ChatMessage message);
}
