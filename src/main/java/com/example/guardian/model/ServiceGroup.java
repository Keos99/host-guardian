package com.example.guardian.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Логическая группа monitored-сервисов для фильтрации на дашборде.
 */
@Entity
@Table(name = "service_group")
public class ServiceGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 2000)
    private String description;

    /**
     * Returns the persistent group identifier.
     *
     * @return database identifier of the group
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the persistent group identifier.
     *
     * @param id database identifier of the group
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the unique group display name.
     *
     * @return group name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the unique group display name.
     *
     * @param name group name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the optional group description.
     *
     * @return group description, or {@code null} when absent
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the optional group description.
     *
     * @param description group description
     */
    public void setDescription(String description) {
        this.description = description;
    }
}
