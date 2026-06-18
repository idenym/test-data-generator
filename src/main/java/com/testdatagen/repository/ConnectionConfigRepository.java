package com.testdatagen.repository;

import com.testdatagen.model.entity.ConnectionConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConnectionConfigRepository extends JpaRepository<ConnectionConfig, Long> {

    List<ConnectionConfig> findAllByUserId(Long userId);

    List<ConnectionConfig> findAllByUserIdOrUserIdIsNull(Long userId);
}
