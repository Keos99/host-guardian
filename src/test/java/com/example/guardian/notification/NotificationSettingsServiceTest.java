package com.example.guardian.notification;

import com.example.guardian.model.AppSetting;
import com.example.guardian.repository.AppSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsServiceTest {

    @Mock
    private AppSettingRepository appSettingRepository;

    @Captor
    private ArgumentCaptor<AppSetting> settingCaptor;

    private NotificationSettingsService service;

    @BeforeEach
    void setUp() {
        service = new NotificationSettingsService(appSettingRepository);
    }

    @Test
    void defaultsToEnabledAndCachesTheLookup() {
        when(appSettingRepository.findById(NotificationSettingsService.GLOBAL_ENABLED_KEY))
                .thenReturn(Optional.empty());

        assertThat(service.isGlobalEnabled()).isTrue();
        assertThat(service.isGlobalEnabled()).isTrue();

        verify(appSettingRepository, times(1)).findById(NotificationSettingsService.GLOBAL_ENABLED_KEY);
    }

    @Test
    void readsStoredDisabledValue() {
        AppSetting stored = new AppSetting();
        stored.setKey(NotificationSettingsService.GLOBAL_ENABLED_KEY);
        stored.setValue("false");
        when(appSettingRepository.findById(NotificationSettingsService.GLOBAL_ENABLED_KEY))
                .thenReturn(Optional.of(stored));

        assertThat(service.isGlobalEnabled()).isFalse();
    }

    @Test
    void setCreatesSettingPersistsAndCachesNewValue() {
        when(appSettingRepository.findById(NotificationSettingsService.GLOBAL_ENABLED_KEY))
                .thenReturn(Optional.empty());

        assertThat(service.setGlobalEnabled(false)).isFalse();
        assertThat(service.isGlobalEnabled()).isFalse();

        verify(appSettingRepository).save(settingCaptor.capture());
        assertThat(settingCaptor.getValue().getKey()).isEqualTo(NotificationSettingsService.GLOBAL_ENABLED_KEY);
        assertThat(settingCaptor.getValue().getValue()).isEqualTo("false");
        verify(appSettingRepository, times(1)).findById(NotificationSettingsService.GLOBAL_ENABLED_KEY);
    }

    @Test
    void setUpdatesExistingSetting() {
        AppSetting stored = new AppSetting();
        stored.setKey(NotificationSettingsService.GLOBAL_ENABLED_KEY);
        stored.setValue("false");
        when(appSettingRepository.findById(NotificationSettingsService.GLOBAL_ENABLED_KEY))
                .thenReturn(Optional.of(stored));

        assertThat(service.setGlobalEnabled(true)).isTrue();

        verify(appSettingRepository).save(stored);
        assertThat(stored.getValue()).isEqualTo("true");
        assertThat(service.isGlobalEnabled()).isTrue();
    }
}
