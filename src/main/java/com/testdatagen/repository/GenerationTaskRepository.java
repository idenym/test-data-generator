package com.testdatagen.repository;

import com.testdatagen.model.entity.GenerationTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GenerationTaskRepository extends JpaRepository<GenerationTask, Long> {

    List<GenerationTask> findAllByOrderByStartedAtDesc();
}
