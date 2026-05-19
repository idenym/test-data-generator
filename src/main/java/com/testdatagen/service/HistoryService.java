package com.testdatagen.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.testdatagen.model.entity.GenerationTask;
import com.testdatagen.repository.GenerationTaskRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HistoryService {

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
}
