package com.example.guardian.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Key-value application setting stored in the database.
 *
 * <p>Settings hold runtime flags that operators change from the dashboard,
 * for example the global chat notification switch. Unlike values from
 * {@code application.yml}, these survive UI edits without a restart.
 */
@Entity
@Table(name = "app_setting")
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 128)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 1024)
    private String value;

    /**
     * Returns the unique setting key.
     *
     * @return setting key
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the unique setting key.
     *
     * @param key setting key
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Returns the stored setting value.
     *
     * @return setting value as text
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the stored setting value.
     *
     * @param value setting value as text
     */
    public void setValue(String value) {
        this.value = value;
    }
}
