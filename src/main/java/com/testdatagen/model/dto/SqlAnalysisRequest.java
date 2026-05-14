package com.testdatagen.model.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class SqlAnalysisRequest {

    @NotNull(message = "连接ID不能为空")
    private Long connectionId;

    @NotBlank(message = "SQL不能为空")
    private String sql;

    public Long getConnectionId() { return connectionId; }
    public void setConnectionId(Long connectionId) { this.connectionId = connectionId; }
    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
}
