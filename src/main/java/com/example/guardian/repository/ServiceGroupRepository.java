package com.example.guardian.repository;

import com.example.guardian.model.ServiceGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for logical service groups used by dashboard filtering.
 */
public interface ServiceGroupRepository extends JpaRepository<ServiceGroup, Long> {

    /**
     * Checks whether another group already uses the same name ignoring case.
     *
     * @param name candidate group name
     * @return {@code true} when a group with the same name exists
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Loads all groups sorted by name for stable UI rendering.
     *
     * @return ordered list of groups
     */
    List<ServiceGroup> findAllByOrderByNameAsc();
}
