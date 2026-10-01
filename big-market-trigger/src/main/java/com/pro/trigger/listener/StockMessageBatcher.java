package com.pro.trigger.listener;

import org.apache.rocketmq.common.message.MessageExt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Keeps RocketMQ callbacks unacknowledged until their aggregated DB write completes. */
public class StockMessageBatcher implements AutoCloseable {

    private final int batchSize;
    private final long flushIntervalMs;
    private final Consumer<List<MessageExt>> writer;
    private final ScheduledThreadPoolExecutor executor;
    private List<MessageExt> pending = new ArrayList<>();
    private List<CompletableFuture<Void>> receipts = new ArrayList<>();
    private ScheduledFuture<?> timer;
    private boolean closed;

    public StockMessageBatcher(int batchSize, long flushIntervalMs, Consumer<List<MessageExt>> writer) {
        if (batchSize <= 0 || flushIntervalMs <= 0) {
            throw new IllegalArgumentException("Stock batch size and interval must be positive");
        }
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.writer = writer;
        this.executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "rocketmq-stock-flush");
            thread.setDaemon(true);
            return thread;
        });
        this.executor.setRemoveOnCancelPolicy(true);
    }

    public synchronized CompletableFuture<Void> submit(List<MessageExt> messages) {
        CompletableFuture<Void> receipt = new CompletableFuture<>();
        if (closed) {
            receipt.completeExceptionally(new IllegalStateException("Stock batcher is closed"));
            return receipt;
        }
        if (messages.isEmpty()) {
            receipt.complete(null);
            return receipt;
        }
        pending.addAll(messages);
        receipts.add(receipt);
        if (timer == null) {
            timer = executor.schedule(this::flush, flushIntervalMs, TimeUnit.MILLISECONDS);
        }
        if (pending.size() >= batchSize) dispatch();
        return receipt;
    }

    private synchronized void flush() {
        dispatch();
    }

    private void dispatch() {
        if (pending.isEmpty()) return;
        if (timer != null) timer.cancel(false);
        timer = null;
        List<MessageExt> batch = pending;
        List<CompletableFuture<Void>> batchReceipts = receipts;
        pending = new ArrayList<>();
        receipts = new ArrayList<>();
        executor.execute(() -> {
            try {
                writer.accept(batch);
                batchReceipts.forEach(receipt -> receipt.complete(null));
            } catch (Throwable failure) {
                // Returning failures to the blocked callbacks makes the broker retry the messages.
                batchReceipts.forEach(receipt -> receipt.completeExceptionally(failure));
            }
        });
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            dispatch();
            executor.shutdown();
        }
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
