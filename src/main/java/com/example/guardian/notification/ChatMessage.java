package com.example.guardian.notification;

/**
 * Immutable chat notification ready to be delivered by a {@link ChatProvider}.
 *
 * @param status severity of the event
 * @param text human-readable message text
 */
public record ChatMessage(MessageStatus status, String text) {
}
