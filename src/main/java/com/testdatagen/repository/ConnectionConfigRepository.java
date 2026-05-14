package com.testdatagen.repository;

import com.testdatagen.model.entity.ConnectionConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectionConfigRepository extends JpaRepository<ConnectionConfig, Long> {
}
