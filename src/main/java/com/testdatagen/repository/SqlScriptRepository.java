package com.testdatagen.repository;

import com.testdatagen.model.entity.SqlScript;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SqlScriptRepository extends JpaRepository<SqlScript, Long> {

    List<SqlScript> findAllByOrderByUpdatedAtDesc();

    List<SqlScript> findByConnectionIdOrderByUpdatedAtDesc(Long connectionId);
}
