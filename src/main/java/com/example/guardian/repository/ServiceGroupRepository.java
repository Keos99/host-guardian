package com.example.guardian.repository;

import com.example.guardian.model.ServiceGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceGroupRepository extends JpaRepository<ServiceGroup, Long> {

    boolean existsByNameIgnoreCase(String name);

    List<ServiceGroup> findAllByOrderByNameAsc();
}
