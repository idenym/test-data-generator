package com.testdatagen.controller;

import com.testdatagen.model.dto.TableMetadata;
import com.testdatagen.service.MetadataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/metadata")
public class MetadataController {

    private final MetadataService metadataService;

    public MetadataController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping("/{connectionId}/tables")
    public List<String> listTables(@PathVariable Long connectionId) {
        return metadataService.listTables(connectionId);
    }

    @GetMapping("/{connectionId}/tables/{tableName}")
    public TableMetadata getTableMetadata(@PathVariable Long connectionId, @PathVariable String tableName) {
        return metadataService.getTableMetadata(connectionId, tableName);
    }

    @GetMapping("/{connectionId}/foreign-keys")
    public Map<String, List<String[]>> getForeignKeys(@PathVariable Long connectionId) {
        return metadataService.getForeignKeys(connectionId);
    }
}
