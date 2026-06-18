package com.example.guardian.notification;

/**
 * Raised when a chat message could not be delivered to the chat backend.
 *
 * <p>The exception is unchecked on purpose: senders never let delivery
 * problems interrupt monitoring or configuration flows, so it is always
 * caught and logged by {@link ChatNotifier}.
 */
public class ChatSendException extends RuntimeException {

    /**
     * Creates an exception with a description only.
     *
     * @param message failure description
     */
    public ChatSendException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a description and a cause.
     *
     * @param message failure description
     * @param cause original transport error
     */
    public ChatSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
