package com.example.guardian.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageStatusTest {

    @Test
    void exposesProviderStatusCodesAcceptedByTheChatBackend() {
        assertThat(MessageStatus.OK.getValue()).isEqualTo("OK");
        assertThat(MessageStatus.FAIL.getValue()).isEqualTo("FAIL");
        assertThat(MessageStatus.SUCCESS.getValue()).isEqualTo("SUCCESS");
        assertThat(MessageStatus.FAILURE.getValue()).isEqualTo("FAILURE");
        assertThat(MessageStatus.UNSTABLE.getValue()).isEqualTo("UNSTABLE");
        assertThat(MessageStatus.NOT_BUILT.getValue()).isEqualTo("NOT_BUILT");
        assertThat(MessageStatus.ABORTED.getValue()).isEmpty();
    }

    @Test
    void definesExactlyTheProviderContractValues() {
        assertThat(MessageStatus.values())
                .extracting(Enum::name)
                .containsExactly("OK", "FAIL", "SUCCESS", "FAILURE", "UNSTABLE", "NOT_BUILT", "ABORTED");
    }
}
