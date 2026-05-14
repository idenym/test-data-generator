package com.testdatagen.controller;

import com.testdatagen.model.dto.SqlAnalysisRequest;
import com.testdatagen.model.dto.SqlAnalysisResult;
import com.testdatagen.service.SqlParserService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/sql")
public class SqlAnalysisController {

    private final SqlParserService sqlParserService;

    public SqlAnalysisController(SqlParserService sqlParserService) {
        this.sqlParserService = sqlParserService;
    }

    @PostMapping("/analyze")
    public SqlAnalysisResult analyze(@Valid @RequestBody SqlAnalysisRequest request) {
        return sqlParserService.analyze(request.getConnectionId(), request.getSql());
    }
}
