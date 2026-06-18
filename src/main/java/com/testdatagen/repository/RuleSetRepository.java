package com.testdatagen.repository;

import com.testdatagen.model.entity.RuleSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleSetRepository extends JpaRepository<RuleSet, Long> {

    List<RuleSet> findAllByUserId(Long userId);

    List<RuleSet> findAllByUserIdOrUserIdIsNull(Long userId);
}
