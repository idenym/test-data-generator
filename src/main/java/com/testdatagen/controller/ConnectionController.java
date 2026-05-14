package com.testdatagen.controller;

import com.testdatagen.model.dto.ConnectionRequest;
import com.testdatagen.model.dto.ConnectionTestResult;
import com.testdatagen.model.entity.ConnectionConfig;
import com.testdatagen.service.ConnectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/connections")
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping
    public List<ConnectionConfig> list() {
        return connectionService.listAll();
    }

    @PostMapping
    public ConnectionConfig create(@Valid @RequestBody ConnectionRequest request) {
        return connectionService.save(request);
    }

    @PutMapping("/{id}")
    public ConnectionConfig update(@PathVariable Long id, @Valid @RequestBody ConnectionRequest request) {
        return connectionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        connectionService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/test")
    public ConnectionTestResult test(@RequestBody ConnectionRequest request) {
        return connectionService.testConnection(request);
    }
}
