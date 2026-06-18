package com.example.guardian.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessSnapshotTest {

    @Test
    void matchesCommandLineByRegexAndReturnsFirstMatch() {
        ProcessSnapshot snapshot = ProcessSnapshot.of(List.of(
                new LinuxProcessInspector.ProcessInfo(1L, "java -jar billing-api.jar"),
                new LinuxProcessInspector.ProcessInfo(2L, "java -jar billing-api.jar")
        ));

        assertThat(snapshot.isAvailable()).isTrue();
        assertThat(snapshot.findFirst("billing-api\\.jar").orElseThrow().pid()).isEqualTo(1L);
        assertThat(snapshot.findFirst("nope")).isEmpty();
    }

    @Test
    void fallsBackToSubstringMatchingForInvalidRegex() {
        ProcessSnapshot snapshot = ProcessSnapshot.of(List.of(
                new LinuxProcessInspector.ProcessInfo(7L, "service with [unclosed group")
        ));

        assertThat(snapshot.findFirst("[unclosed").orElseThrow().pid()).isEqualTo(7L);
    }

    @Test
    void unavailableSnapshotIsEmptyAndMatchesNothing() {
        ProcessSnapshot snapshot = ProcessSnapshot.unavailable();

        assertThat(snapshot.isAvailable()).isFalse();
        assertThat(snapshot.findFirst("anything")).isEmpty();
    }
}
