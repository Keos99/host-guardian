package com.example.guardian.notification;

/**
 * Immutable chat notification ready to be delivered by a {@link ChatProvider}.
 *
 * <p>The record carries the full set of payload fields expected by the chat
 * backend: the recipient {@code peer}, the event {@code status} and {@code text},
 * and a {@code url} back to the Host Guardian service.
 *
 * @param status severity of the event
 * @param text human-readable message text
 * @param peer chat recipient identifier
 * @param url link to the Host Guardian service
 */
public record ChatMessage(MessageStatus status, String text, String peer, String url) {
}
