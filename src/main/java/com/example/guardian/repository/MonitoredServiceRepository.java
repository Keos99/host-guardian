package com.example.guardian.repository;

import com.example.guardian.model.MonitoredService;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, Long> {

    @Override
    @EntityGraph(attributePaths = {"host", "group"})
    List<MonitoredService> findAll();

    @EntityGraph(attributePaths = {"host", "group"})
    List<MonitoredService> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = {"host", "group"})
    List<MonitoredService> findAllByGroupIdInOrderByNameAsc(List<Long> groupIds);

    @EntityGraph(attributePaths = {"host", "group"})
    List<MonitoredService> findAllByGroupIdOrderByNameAsc(Long groupId);

    @EntityGraph(attributePaths = {"host", "group"})
    java.util.Optional<MonitoredService> findById(Long id);

    boolean existsByNameIgnoreCase(String name);

    long countByHostId(Long hostId);

    long countByGroupId(Long groupId);
}
