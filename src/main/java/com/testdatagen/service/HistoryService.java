package com.testdatagen.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.testdatagen.model.entity.GenerationTask;
import com.testdatagen.repository.GenerationTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HistoryService {

    private static final Logger log = LoggerFactory.getLogger(HistoryService.class);

    private final GenerationTaskRepository taskRepository;

    public HistoryService(GenerationTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<GenerationTask> listAll() {
        return taskRepository.findAllByOrderByStartedAtDesc();
    }

    public GenerationTask getById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + id));
    }

    public Map<String, Object> getGeneratedData(Long id) {
        GenerationTask task = getById(id);
        String snapshot = task.getGeneratedDataSnapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("tableData", new LinkedHashMap<>());
            empty.put("generationOrder", new java.util.ArrayList<>());
            return empty;
        }
        Map<String, List<Map<String, Object>>> tableData = JSON.parseObject(
                snapshot, new TypeReference<Map<String, List<Map<String, Object>>>>() {});
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableData", tableData);
        // 从 analysis snapshot 中获取 generationOrder
        String analysisSnapshot = task.getAnalysisSnapshot();
        if (analysisSnapshot != null && !analysisSnapshot.isEmpty()) {
            try {
                Map<String, Object> analysis = JSON.parseObject(analysisSnapshot);
                Object order = analysis.get("generationOrder");
                result.put("generationOrder", order != null ? order : new java.util.ArrayList<>());
            } catch (Exception e) {
                result.put("generationOrder", new java.util.ArrayList<>(tableData.keySet()));
            }
        } else {
            result.put("generationOrder", new java.util.ArrayList<>(tableData.keySet()));
        }
        return result;
    }

    public void delete(Long id) {
        taskRepository.deleteById(id);
    }

    public Map<String, Object> getStatistics() {
        try {
            List<Object[]> rows = taskRepository.findAggregateStatistics();
            if (rows == null || rows.isEmpty()) {
                log.warn("findAggregateStatistics returned empty");
                return emptyStatistics();
            }
            Object[] row = rows.get(0);
            if (row == null || row.length < 4) {
                log.warn("Aggregate row is null or too short: length={}", row == null ? "null" : row.length);
                return emptyStatistics();
            }
            log.info("Statistics query result: types=[{}, {}, {}, {}], values=[{}, {}, {}, {}]",
                    row[0] != null ? row[0].getClass().getSimpleName() : "null",
                    row[1] != null ? row[1].getClass().getSimpleName() : "null",
                    row[2] != null ? row[2].getClass().getSimpleName() : "null",
                    row[3] != null ? row[3].getClass().getSimpleName() : "null",
                    row[0], row[1], row[2], row[3]);

            long totalCells = row[0] != null ? ((Number) row[0]).longValue() : 0L;
            long editedCells = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            long regenCells = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            long taskCount = row[3] != null ? ((Number) row[3]).longValue() : 0L;

            double adoptionRate = totalCells > 0 ? (double) (totalCells - editedCells - regenCells) / totalCells : 1.0;
            double regenerationRate = totalCells > 0 ? (double) regenCells / totalCells : 0.0;

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalTasks", taskCount);
            stats.put("totalCells", totalCells);
            stats.put("editedCells", editedCells);
            stats.put("regeneratedCells", regenCells);
            stats.put("adoptionRate", Math.round(adoptionRate * 1000.0) / 1000.0);
            stats.put("regenerationRate", Math.round(regenerationRate * 1000.0) / 1000.0);
            return stats;
        } catch (Exception e) {
            log.error("getStatistics failed: {} - {}", e.getClass().getName(), e.getMessage(), e);
            return emptyStatistics();
        }
    }

    private Map<String, Object> emptyStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTasks", 0L);
        stats.put("totalCells", 0L);
        stats.put("editedCells", 0L);
        stats.put("regeneratedCells", 0L);
        stats.put("adoptionRate", 1.0);
        stats.put("regenerationRate", 0.0);
        return stats;
    }
}
