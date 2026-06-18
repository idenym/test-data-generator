package com.testdatagen.engine;

/**
 * 预览任务进度回调接口。
 * Pipeline 在处理每张表时通过此接口上报进度，并检查是否需要取消。
 */
public interface ProgressCallback {

    /**
     * 当开始处理某张表时调用
     * @param tableName 表名
     * @param tableIndex 当前表在总列表中的索引（从0开始）
     * @param totalTables 需要处理的表总数
     */
    void onTableStart(String tableName, int tableIndex, int totalTables);

    /**
     * 当某张表处理完成时调用
     * @param tableName 表名
     * @param tableIndex 当前表在总列表中的索引
     * @param totalTables 需要处理的表总数
     */
    void onTableComplete(String tableName, int tableIndex, int totalTables);

    /**
     * 检查任务是否已被取消。
     * Pipeline 在关键节点调用此方法，若返回 true 则抛出 TaskCancelledException。
     * @return true 表示任务已被取消
     */
    boolean isCancelled();
}
