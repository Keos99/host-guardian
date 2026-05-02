package com.example.guardian.repository;

import com.example.guardian.model.HostConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HostConfigRepository extends JpaRepository<HostConfig, Long> {

    boolean existsByNameIgnoreCase(String name);

    List<HostConfig> findAllByOrderByNameAsc();
}
