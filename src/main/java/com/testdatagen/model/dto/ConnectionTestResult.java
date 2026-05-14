package com.testdatagen.model.dto;

public class ConnectionTestResult {
    private boolean success;
    private String message;
    private long latencyMs;
    private String version;

    public static ConnectionTestResult ok(String version, long latencyMs) {
        ConnectionTestResult r = new ConnectionTestResult();
        r.success = true;
        r.message = "连接成功";
        r.version = version;
        r.latencyMs = latencyMs;
        return r;
    }

    public static ConnectionTestResult fail(String message) {
        ConnectionTestResult r = new ConnectionTestResult();
        r.success = false;
        r.message = message;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}
