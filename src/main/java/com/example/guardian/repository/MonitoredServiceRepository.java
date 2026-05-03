package com.example.guardian.repository;

import com.example.guardian.model.MonitoredService;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for monitored service configurations.
 *
 * <p>Query methods eagerly fetch related host and group entities because that data
 * is required by the API layer and dashboard views.
 */
public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, Long> {

    /**
     * Loads every configured service together with its host and group references.
     *
     * @return complete list of monitored services
     */
    @Override
    @EntityGraph(attributePaths = {"host", "group"})
    List<MonitoredService> findAll();

    /**
     * Loads all services ordered by name with host and group references pre-fetched.
     *
     * @return ordered list of monitored services
     */
    @EntityGraph(attributePaths = {"host", "group"})
    List<MonitoredService> findAllByOrderByNameAsc();

    /**
     * Loads all services that belong to any of the requested groups.
     *
     * @param groupIds identifiers of groups to include
     * @return ordered list of matching services
     */
    @EntityGraph(attributePaths = {"host", "group"})
    List<MonitoredService> findAllByGroupIdInOrderByNameAsc(List<Long> groupIds);

    /**
     * Loads all services for a single group.
     *
     * @param groupId identifier of the requested group
     * @return ordered list of services in that group
     */
    @EntityGraph(attributePaths = {"host", "group"})
    List<MonitoredService> findAllByGroupIdOrderByNameAsc(Long groupId);

    /**
     * Loads one service by identifier together with host and group references.
     *
     * @param id service identifier
     * @return optional monitored service
     */
    @EntityGraph(attributePaths = {"host", "group"})
    Optional<MonitoredService> findById(Long id);

    /**
     * Checks whether another service already uses the same name ignoring case.
     *
     * @param name candidate service name
     * @return {@code true} when a service with the same name exists
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Counts how many services reference the specified host.
     *
     * @param hostId host identifier
     * @return number of dependent services
     */
    long countByHostId(Long hostId);

    /**
     * Counts how many services reference the specified group.
     *
     * @param groupId group identifier
     * @return number of dependent services
     */
    long countByGroupId(Long groupId);
}
