package com.corosus.watut.client.screen;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.function.Function;

public class ByteBufferProcessor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final BlockingQueue<ByteBuffer> inputQueue;
    private final BlockingQueue<ByteBuffer> outputQueue;
    private final ExecutorService executorService;
    private final Function<ByteBuffer, ByteBuffer> processingFunction;
    private volatile boolean isRunning;
    private volatile boolean processing;
    
    public ByteBufferProcessor(Function<ByteBuffer, ByteBuffer> processingFunction) {
        this.inputQueue = new LinkedBlockingQueue<>();
        this.outputQueue = new LinkedBlockingQueue<>();
        this.executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "WATUT-ByteBufferProcessor");
            return thread;
        });
        this.processingFunction = processingFunction;
        this.isRunning = true;
        this.processing = false;
        
        startProcessing();
    }
    
    private void startProcessing() {
        executorService.submit(() -> {
            while (isRunning || !inputQueue.isEmpty()) {
                try {
                    ByteBuffer input = inputQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (input != null) {
                        processing = true;
                        try {
                            ByteBuffer result = processingFunction.apply(input);
                            if (result != null) {
                                outputQueue.put(result);
                            }
                        } catch (Throwable t) {
                            LOGGER.error("WATUT ByteBufferProcessor failed to process a buffer", t);
                        } finally {
                            processing = false;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
    
    public void submitForProcessing(ByteBuffer buffer) {
        if (!isRunning) {
            throw new IllegalStateException("Processor has been shutdown");
        }
        try {
            inputQueue.put(buffer);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to submit buffer for processing", e);
        }
    }
    
    public ByteBuffer pollProcessedBuffer() {
        return outputQueue.poll();
    }

    public boolean hasWork() {
        return processing || !inputQueue.isEmpty();
    }
    
    public void shutdown() {
        isRunning = false;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
