package com.testdatagen.repository;

import com.testdatagen.model.entity.RuleSet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleSetRepository extends JpaRepository<RuleSet, Long> {
}
