package com.testdatagen.controller;

import com.testdatagen.model.entity.SqlScript;
import com.testdatagen.service.SqlScriptService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/scripts")
public class SqlScriptController {

    private final SqlScriptService sqlScriptService;

    public SqlScriptController(SqlScriptService sqlScriptService) {
        this.sqlScriptService = sqlScriptService;
    }

    @GetMapping
    public List<SqlScript> listAll() {
        return sqlScriptService.listAll();
    }

    @GetMapping("/{id}")
    public SqlScript getById(@PathVariable Long id) {
        return sqlScriptService.getById(id);
    }

    @PostMapping
    public SqlScript create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String sqlContent = (String) body.get("sqlContent");
        String description = (String) body.get("description");
        Long connectionId = body.get("connectionId") != null
                ? Long.valueOf(body.get("connectionId").toString()) : null;
        return sqlScriptService.create(name, sqlContent, description, connectionId);
    }

    @PutMapping("/{id}")
    public SqlScript update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String sqlContent = (String) body.get("sqlContent");
        String description = (String) body.get("description");
        return sqlScriptService.update(id, name, sqlContent, description);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sqlScriptService.delete(id);
        return ResponseEntity.ok().build();
    }
}
