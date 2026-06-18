package com.testdatagen.exception;

/**
 * 当预览任务被用户主动取消时抛出。
 * Pipeline 在检查点发现 cancelled 标志为 true 时抛出此异常，
 * 由 PreviewTaskRegistry 捕获并将任务状态置为 CANCELLED。
 */
public class TaskCancelledException extends RuntimeException {

    public TaskCancelledException() {
        super("任务已被用户取消");
    }

    public TaskCancelledException(String message) {
        super(message);
    }
}
