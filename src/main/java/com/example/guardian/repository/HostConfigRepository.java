package com.example.guardian.repository;

import com.example.guardian.model.HostConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for configured hosts.
 */
public interface HostConfigRepository extends JpaRepository<HostConfig, Long> {

    /**
     * Checks whether another host already uses the same name ignoring case.
     *
     * @param name candidate host name
     * @return {@code true} when a host with the same logical name exists
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Loads all hosts sorted by name for stable UI presentation.
     *
     * @return ordered list of hosts
     */
    List<HostConfig> findAllByOrderByNameAsc();
}
