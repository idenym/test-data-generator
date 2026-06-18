package com.testdatagen.service;

import com.alibaba.fastjson.JSON;
import com.testdatagen.engine.DataGenerationPipeline;
import com.testdatagen.engine.ProgressCallback;
import com.testdatagen.exception.TaskCancelledException;
import com.testdatagen.model.dto.DataGenRequest;
import com.testdatagen.model.dto.DataPreviewResponse;
import com.testdatagen.model.dto.PreviewStatusResponse;
import com.testdatagen.model.dto.SqlAnalysisResult;
import com.testdatagen.model.entity.GenerationTask;
import com.testdatagen.model.enums.TaskStatus;
import com.testdatagen.repository.GenerationTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步预览任务注册中心。
 * 同时管理内存中的实时状态和数据库中的持久化记录。
 * 内存层负责实时进度和轮询，数据库层负责历史展示和详情查看。
 */
@Component
public class PreviewTaskRegistry {

    private static final Logger log = LoggerFactory.getLogger(PreviewTaskRegistry.class);

    /** 任务超时时间（毫秒），默认 10 分钟 */
    private static final long TASK_TIMEOUT_MS = 10 * 60 * 1000L;

    /** 已完成任务内存保留时间（毫秒），默认 10 分钟 */
    private static final long COMPLETED_RETAIN_MS = 10 * 60 * 1000L;

    private final ConcurrentHashMap<String, PreviewTask> tasks = new ConcurrentHashMap<>();
    private final GenerationTaskRepository taskRepository;

    public PreviewTaskRegistry(GenerationTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * 提交异步预览任务。立即返回 SubmitResult，后台执行生成。
     * SubmitResult 包含 previewTaskId（内存轮询）和 dbTaskId（DB详情页跳转）。
     */
    public SubmitResult submit(DataGenRequest request, SqlAnalysisResult analysis,
                               DataGenerationPipeline pipeline) {
        String previewTaskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        int totalTables = analysis.getGenerationOrder() != null ? analysis.getGenerationOrder().size() : 0;

        // 创建数据库记录
        GenerationTask dbTask = new GenerationTask();
        dbTask.setConnectionId(request.getConnectionId());
        dbTask.setInputSql(request.getSql());
        dbTask.setRowCount(request.getRowCount());
        dbTask.setStatus(TaskStatus.RUNNING);
        dbTask.setTaskType("PREVIEW");
        dbTask.setTotalTables(totalTables);
        dbTask.setPreviewTaskId(previewTaskId);
        if (request.getFieldRules() != null && !request.getFieldRules().isEmpty()) {
            dbTask.setRulesSnapshot(JSON.toJSONString(request.getFieldRules()));
        }
        dbTask.setAnalysisSnapshot(JSON.toJSONString(analysis));
        dbTask = taskRepository.save(dbTask);

        PreviewTask memTask = new PreviewTask(previewTaskId, totalTables, dbTask.getId());
        tasks.put(previewTaskId, memTask);

        log.info("预览任务已提交: previewTaskId={}, dbId={}, 共 {} 张表", previewTaskId, dbTask.getId(), totalTables);

        try {
            java.util.concurrent.CompletableFuture.runAsync(() -> executeTask(memTask, request, analysis, pipeline));
        } catch (Exception e) {
            memTask.status = TaskStatus.FAILED;
            memTask.errorMessage = "提交任务失败: " + e.getMessage();
            memTask.completedAt = Instant.now();
            dbTask.setStatus(TaskStatus.FAILED);
            dbTask.setErrorMessage(memTask.errorMessage);
            dbTask.setCompletedAt(LocalDateTime.now());
            taskRepository.save(dbTask);
            log.error("预览任务提交失败: previewTaskId={}", previewTaskId, e);
        }

        return new SubmitResult(previewTaskId, dbTask.getId());
    }

    /**
     * 查询任务状态（从内存层读取实时进度）
     */
    public PreviewStatusResponse getStatus(String previewTaskId) {
        PreviewTask task = tasks.get(previewTaskId);
        if (task == null) {
            // 内存中已清理，尝试从数据库查找
            GenerationTask dbTask = taskRepository.findByPreviewTaskId(previewTaskId);
            if (dbTask != null) {
                PreviewStatusResponse resp = new PreviewStatusResponse();
                resp.setTaskId(previewTaskId);
                resp.setStatus(dbTask.getStatus());
                resp.setErrorMessage(dbTask.getErrorMessage());
                PreviewStatusResponse.Progress progress = new PreviewStatusResponse.Progress(
                        dbTask.getCompletedTables() != null ? dbTask.getCompletedTables() : 0,
                        dbTask.getTotalTables() != null ? dbTask.getTotalTables() : 0,
                        dbTask.getCurrentTable());
                resp.setProgress(progress);
                return resp;
            }
            PreviewStatusResponse resp = new PreviewStatusResponse();
            resp.setTaskId(previewTaskId);
            resp.setStatus(TaskStatus.FAILED);
            resp.setErrorMessage("任务不存在或已过期");
            return resp;
        }

        PreviewStatusResponse resp = new PreviewStatusResponse();
        resp.setTaskId(previewTaskId);
        resp.setStatus(task.status);
        resp.setErrorMessage(task.errorMessage);

        PreviewStatusResponse.Progress progress = new PreviewStatusResponse.Progress(
                task.completedTables, task.totalTables, task.currentTable);
        resp.setProgress(progress);

        if (task.status == TaskStatus.SUCCESS) {
            resp.setResult(task.result);
        }

        return resp;
    }

    /**
     * 获取结果（仅当任务成功时有数据）
     */
    public DataPreviewResponse getResult(String previewTaskId) {
        PreviewTask task = tasks.get(previewTaskId);
        if (task == null || task.status != TaskStatus.SUCCESS) {
            return null;
        }
        return task.result;
    }

    /**
     * 取消任务
     * @return true 如果成功标记取消
     */
    public boolean cancel(String previewTaskId) {
        PreviewTask task = tasks.get(previewTaskId);
        if (task == null) {
            return false;
        }
        if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.PENDING) {
            task.cancelled.set(true);
            log.info("预览任务已标记取消: previewTaskId={}", previewTaskId);
            return true;
        }
        return false;
    }

    /**
     * 通过 DB id 获取预览任务的实时进度（用于详情页轮询）
     */
    public PreviewStatusResponse getStatusByDbId(Long dbId) {
        GenerationTask dbTask = taskRepository.findById(dbId).orElse(null);
        if (dbTask == null || dbTask.getPreviewTaskId() == null) {
            return null;
        }
        // 优先从内存读取实时进度
        return getStatus(dbTask.getPreviewTaskId());
    }

    /**
     * 定时清理：过期的已完成内存任务、超时的运行中任务、同步DB状态
     */
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, PreviewTask>> it = tasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PreviewTask> entry = it.next();
            PreviewTask task = entry.getValue();

            // 清理已完成且超过保留时间的内存任务
            if (isTerminal(task.status) && task.completedAt != null
                    && now.toEpochMilli() - task.completedAt.toEpochMilli() > COMPLETED_RETAIN_MS) {
                log.debug("清理过期内存任务: previewTaskId={}, status={}", task.taskId, task.status);
                it.remove();
                continue;
            }

            // 超时的运行中任务强制失败
            if (task.status == TaskStatus.RUNNING
                    && now.toEpochMilli() - task.startedAt.toEpochMilli() > TASK_TIMEOUT_MS) {
                log.warn("预览任务超时: previewTaskId={}, 已运行 {}ms", task.taskId,
                        now.toEpochMilli() - task.startedAt.toEpochMilli());
                task.cancelled.set(true);
                task.status = TaskStatus.FAILED;
                task.errorMessage = "任务超时（超过 " + (TASK_TIMEOUT_MS / 1000) + " 秒）";
                task.completedAt = now;
                // 同步更新DB
                updateDbTaskOnTerminal(task);
            }
        }
    }

    /**
     * 定时将内存中的进度同步到DB（低频，每10秒）
     */
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void syncProgressToDb() {
        for (PreviewTask task : tasks.values()) {
            if (task.status == TaskStatus.RUNNING && task.dbTaskId != null) {
                try {
                    GenerationTask dbTask = taskRepository.findById(task.dbTaskId).orElse(null);
                    if (dbTask != null) {
                        dbTask.setCompletedTables(task.completedTables);
                        dbTask.setTotalTables(task.totalTables);
                        dbTask.setCurrentTable(task.currentTable);
                        taskRepository.save(dbTask);
                    }
                } catch (Exception e) {
                    log.warn("同步进度到DB失败: dbId={}", task.dbTaskId, e);
                }
            }
        }
    }

    private void executeTask(PreviewTask task, DataGenRequest request,
                              SqlAnalysisResult analysis, DataGenerationPipeline pipeline) {
        task.status = TaskStatus.RUNNING;
        task.startedAt = Instant.now();
        log.info("预览任务开始执行: previewTaskId={}, dbId={}", task.taskId, task.dbTaskId);

        try {
            ProgressCallback callback = new ProgressCallback() {
                @Override
                public void onTableStart(String tableName, int tableIndex, int totalTables) {
                    task.currentTable = tableName;
                    task.totalTables = totalTables;
                }

                @Override
                public void onTableComplete(String tableName, int tableIndex, int totalTables) {
                    task.completedTables = tableIndex + 1;
                    task.totalTables = totalTables;
                }

                @Override
                public boolean isCancelled() {
                    return task.cancelled.get();
                }
            };

            DataPreviewResponse result = pipeline.preview(request, analysis, callback);

            if (task.cancelled.get()) {
                task.status = TaskStatus.CANCELLED;
                task.errorMessage = "任务已被用户取消";
            } else {
                task.status = TaskStatus.SUCCESS;
                task.result = result;
            }
        } catch (TaskCancelledException e) {
            task.status = TaskStatus.CANCELLED;
            task.errorMessage = e.getMessage();
        } catch (Exception e) {
            task.status = TaskStatus.FAILED;
            task.errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("预览任务执行失败: previewTaskId={}", task.taskId, e);
        } finally {
            task.completedAt = Instant.now();
            // 终态同步到DB
            updateDbTaskOnTerminal(task);
        }
    }

    /**
     * 任务到达终态时，将最终结果写入DB
     */
    private void updateDbTaskOnTerminal(PreviewTask task) {
        if (task.dbTaskId == null) return;
        try {
            GenerationTask dbTask = taskRepository.findById(task.dbTaskId).orElse(null);
            if (dbTask == null) return;

            dbTask.setStatus(task.status);
            dbTask.setErrorMessage(task.errorMessage);
            dbTask.setCompletedAt(LocalDateTime.now());
            dbTask.setCompletedTables(task.completedTables);
            dbTask.setTotalTables(task.totalTables);
            dbTask.setCurrentTable(task.currentTable);

            if (task.status == TaskStatus.SUCCESS && task.result != null) {
                // 保存数据快照（每表最多200行）
                Map<String, java.util.List<Map<String, Object>>> snapshot = new java.util.LinkedHashMap<>();
                for (String tableName : task.result.getGenerationOrder()) {
                    java.util.List<Map<String, Object>> rows = task.result.getTableData().get(tableName);
                    if (rows != null) {
                        if (rows.size() > 200) rows = rows.subList(0, 200);
                        snapshot.put(tableName, new java.util.ArrayList<>(rows));
                    }
                }
                dbTask.setGeneratedDataSnapshot(JSON.toJSONString(snapshot));
                // 计算总行数
                int totalRows = 0;
                for (java.util.List<Map<String, Object>> rows : task.result.getTableData().values()) {
                    totalRows += rows.size();
                }
                dbTask.setRowsGenerated(totalRows);
                dbTask.setRowCount(totalRows);
            }

            taskRepository.save(dbTask);
            log.info("DB任务已更新: dbId={}, status={}", task.dbTaskId, task.status);
        } catch (Exception e) {
            log.error("更新DB任务失败: dbId={}", task.dbTaskId, e);
        }
    }

    private boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.SUCCESS || status == TaskStatus.FAILED || status == TaskStatus.CANCELLED;
    }

    /**
     * 内部任务状态对象（内存层）
     */
    private static class PreviewTask {
        final String taskId;          // 内存任务ID (previewTaskId)
        final Long dbTaskId;          // 关联的DB记录ID
        volatile TaskStatus status = TaskStatus.PENDING;
        volatile int completedTables;
        volatile int totalTables;
        volatile String currentTable;
        volatile String errorMessage;
        volatile DataPreviewResponse result;
        volatile Instant startedAt;
        volatile Instant completedAt;
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        PreviewTask(String taskId, int totalTables, Long dbTaskId) {
            this.taskId = taskId;
            this.totalTables = totalTables;
            this.dbTaskId = dbTaskId;
            this.startedAt = Instant.now();
        }
    }

    /**
     * 提交任务的结果，包含内存任务ID和DB记录ID。
     */
    public static class SubmitResult {
        private final String previewTaskId;
        private final Long dbTaskId;

        public SubmitResult(String previewTaskId, Long dbTaskId) {
            this.previewTaskId = previewTaskId;
            this.dbTaskId = dbTaskId;
        }

        public String getPreviewTaskId() { return previewTaskId; }
        public Long getDbTaskId() { return dbTaskId; }
    }
}