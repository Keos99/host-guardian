package com.example.guardian.notification;

import com.example.guardian.model.AppSetting;
import com.example.guardian.repository.AppSettingRepository;
import org.springframework.stereotype.Service;

/**
 * Stores the runtime (dashboard-controlled) global notification switch.
 *
 * <p>The value is persisted in the {@code app_setting} table so it survives
 * application restarts, and cached in memory because it is consulted on every
 * notification attempt. Per-service switches live on
 * {@link com.example.guardian.model.MonitoredService} instead.
 */
@Service
public class NotificationSettingsService {

    static final String GLOBAL_ENABLED_KEY = "notifications.global-enabled";

    private final AppSettingRepository appSettingRepository;

    private volatile Boolean cachedGlobalEnabled;

    /**
     * Creates a notification settings service.
     *
     * @param appSettingRepository repository for key-value application settings
     */
    public NotificationSettingsService(AppSettingRepository appSettingRepository) {
        this.appSettingRepository = appSettingRepository;
    }

    /**
     * Checks whether notifications are globally enabled at runtime.
     *
     * <p>Defaults to {@code true} when the switch has never been changed.
     *
     * @return {@code true} when the global runtime switch is on
     */
    public boolean isGlobalEnabled() {
        Boolean cached = cachedGlobalEnabled;
        if (cached != null) {
            return cached;
        }

        boolean stored = appSettingRepository.findById(GLOBAL_ENABLED_KEY)
                .map(setting -> Boolean.parseBoolean(setting.getValue()))
                .orElse(true);
        cachedGlobalEnabled = stored;
        return stored;
    }

    /**
     * Updates the global runtime notification switch.
     *
     * @param enabled new switch value
     * @return persisted switch value
     */
    public boolean setGlobalEnabled(boolean enabled) {
        AppSetting setting = appSettingRepository.findById(GLOBAL_ENABLED_KEY)
                .orElseGet(() -> {
                    AppSetting created = new AppSetting();
                    created.setKey(GLOBAL_ENABLED_KEY);
                    return created;
                });
        setting.setValue(String.valueOf(enabled));
        appSettingRepository.save(setting);
        cachedGlobalEnabled = enabled;
        return enabled;
    }
}
