package com.pro.test.mq;

import com.pro.trigger.listener.StockMessageBatcher;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class StockMessageBatcherTest {

    @Test
    public void countThresholdFlushesWithoutWaitingForTimer() throws Exception {
        AtomicInteger written = new AtomicInteger();
        try (StockMessageBatcher batcher = new StockMessageBatcher(2, 60000,
                messages -> written.addAndGet(messages.size()))) {
            CompletableFuture<Void> first = batcher.submit(Collections.singletonList(new MessageExt()));
            assertFalse(first.isDone());
            CompletableFuture<Void> second = batcher.submit(Collections.singletonList(new MessageExt()));
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            assertEquals(2, written.get());
        }
    }

    @Test
    public void timerFlushesLowTraffic() throws Exception {
        AtomicInteger written = new AtomicInteger();
        try (StockMessageBatcher batcher = new StockMessageBatcher(500, 50,
                messages -> written.addAndGet(messages.size()))) {
            batcher.submit(Collections.singletonList(new MessageExt())).get(3, TimeUnit.SECONDS);
            assertEquals(1, written.get());
        }
    }

    @Test
    public void doesNotAcknowledgeUntilDatabaseCompletes() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (StockMessageBatcher batcher = new StockMessageBatcher(1, 60000, messages -> {
            started.countDown();
            try {
                if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        })) {
            CompletableFuture<Void> receipt = batcher.submit(Collections.singletonList(new MessageExt()));
            try {
                assertTrue(started.await(2, TimeUnit.SECONDS));
                assertFalse(receipt.isDone());
            } finally {
                release.countDown();
            }
            receipt.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    public void failedBatchFailsEveryReceiptAndNextBatchCanSucceed() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (StockMessageBatcher batcher = new StockMessageBatcher(2, 60000, messages -> {
            if (calls.getAndIncrement() == 0) throw new IllegalStateException("Database unavailable");
        })) {
            CompletableFuture<Void> first = batcher.submit(Collections.singletonList(new MessageExt()));
            CompletableFuture<Void> second = batcher.submit(Collections.singletonList(new MessageExt()));
            for (CompletableFuture<Void> receipt : Arrays.asList(first, second)) {
                try {
                    receipt.get(2, TimeUnit.SECONDS);
                    fail("Expected failed database write");
                } catch (ExecutionException expected) {
                    assertTrue(expected.getCause() instanceof IllegalStateException);
                }
            }
            batcher.submit(Arrays.asList(new MessageExt(), new MessageExt())).get(2, TimeUnit.SECONDS);
            assertEquals(2, calls.get());
        }
    }

    @Test
    public void closeFlushesPendingMessagesAndRejectsNewOnes() throws Exception {
        AtomicInteger written = new AtomicInteger();
        StockMessageBatcher batcher = new StockMessageBatcher(500, 60000,
                messages -> written.addAndGet(messages.size()));
        CompletableFuture<Void> receipt = batcher.submit(Collections.singletonList(new MessageExt()));
        batcher.close();
        receipt.get(2, TimeUnit.SECONDS);
        assertEquals(1, written.get());
        assertTrue(batcher.submit(Collections.singletonList(new MessageExt())).isCompletedExceptionally());
    }
}
