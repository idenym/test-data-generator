package com.testdatagen.service;

import com.testdatagen.model.entity.GenerationTask;
import com.testdatagen.repository.GenerationTaskRepository;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public void delete(Long id) {
        taskRepository.deleteById(id);
    }
}
