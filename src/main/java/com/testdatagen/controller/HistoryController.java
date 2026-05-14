package com.testdatagen.controller;

import com.testdatagen.model.entity.GenerationTask;
import com.testdatagen.service.HistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public List<GenerationTask> list() {
        return historyService.listAll();
    }

    @GetMapping("/{id}")
    public GenerationTask getById(@PathVariable Long id) {
        return historyService.getById(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        historyService.delete(id);
        return ResponseEntity.ok().build();
    }
}
