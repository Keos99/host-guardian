package com.example.guardian.notification;

/**
 * Severity of a chat notification.
 *
 * <p>Every status carries a short unicode marker that is prepended to the
 * message text, so messages stay readable in plain-text chats without any
 * formatting support on the receiver side.
 */
public enum MessageStatus {

    /**
     * Positive event, for example a service recovered.
     */
    OK("🟢"),

    /**
     * Neutral informational event, for example a configuration change.
     */
    INFO("🔵"),

    /**
     * Event that needs attention but not necessarily an action.
     */
    WARNING("🟡"),

    /**
     * Failure that likely requires operator intervention.
     */
    ALARM("🔴");

    private final String marker;

    MessageStatus(String marker) {
        this.marker = marker;
    }

    /**
     * Returns the unicode marker shown before the message text.
     *
     * @return marker symbol for chat rendering
     */
    public String getMarker() {
        return marker;
    }
}
