package com.example.guardian.notification;

/**
 * Статусы сообщений для {@link ChatMessage}.
 *
 * <p>Набор значений зафиксирован контрактом чат-провайдера: он принимает только
 * перечисленные статусы. Строковый код статуса ({@link #getValue()}) передается
 * в поле {@code status} payload вебхука.
 */
public enum MessageStatus {

    OK("OK"),
    FAIL("FAIL"),
    SUCCESS("SUCCESS"),
    FAILURE("FAILURE"),
    UNSTABLE("UNSTABLE"),
    NOT_BUILT("NOT_BUILT"),
    ABORTED("");

    private final String status;

    MessageStatus(String status) {
        this.status = status;
    }

    /**
     * Возвращает строковый код статуса, передаваемый чат-провайдеру.
     *
     * @return код статуса, принимаемый провайдером
     */
    public String getValue() {
        return status;
    }
}
