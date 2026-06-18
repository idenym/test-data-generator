package com.testdatagen.repository;

import com.testdatagen.model.entity.GenerationTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GenerationTaskRepository extends JpaRepository<GenerationTask, Long> {

    List<GenerationTask> findAllByOrderByStartedAtDesc();

    @Query("SELECT COALESCE(SUM(t.totalCellCount), 0), " +
           "COALESCE(SUM(t.editedCellCount), 0), " +
           "COALESCE(SUM(t.regeneratedCellCount), 0), " +
           "COUNT(t) " +
           "FROM GenerationTask t " +
           "WHERE t.status = 'SUCCESS' AND t.totalCellCount > 0")
    List<Object[]> findAggregateStatistics();

    GenerationTask findByPreviewTaskId(String previewTaskId);
}
