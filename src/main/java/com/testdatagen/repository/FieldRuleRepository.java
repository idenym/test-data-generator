package com.testdatagen.repository;

import com.testdatagen.model.entity.FieldRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FieldRuleRepository extends JpaRepository<FieldRule, Long> {

    List<FieldRule> findByRuleSetIdOrderByPriorityDesc(Long ruleSetId);

    List<FieldRule> findByRuleSetIdIsNullOrderByPriorityDesc();

    List<FieldRule> findAllByOrderByPriorityDesc();

    List<FieldRule> findAllByUserIdOrderByPriorityDesc(Long userId);

    List<FieldRule> findAllByUserIdOrUserIdIsNullOrderByPriorityDesc(Long userId);
}
