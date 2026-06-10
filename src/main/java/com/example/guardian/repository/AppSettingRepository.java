package com.example.guardian.repository;

import com.example.guardian.model.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for key-value application settings.
 */
public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
