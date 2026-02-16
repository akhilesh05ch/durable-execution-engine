package engine;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class WorkflowEngine {
    private final StepStore store;
    
    public WorkflowEngine(String dbPath) throws SQLException {
        this.store = new StepStore(dbPath);
    }
    
    public <T> T executeWorkflow(String workflowId, WorkflowFunction<T> workflow) throws Exception {
        DurableContext context = new DurableContext(workflowId, store);
        
        System.out.println("[WorkflowEngine] Starting workflow: " + workflowId);
        
        try {
            T result = workflow.execute(context);
            System.out.println("[WorkflowEngine] Workflow " + workflowId + " completed successfully");
            return result;
        } catch (Exception e) {
            System.err.println("[WorkflowEngine] Workflow " + workflowId + " failed: " + e.getMessage());
            throw e;
        }
    }
    
    public static class ParallelExecutor {
        private final DurableContext context;
        private final List<CompletableFuture<?>> futures;
        private final ExecutorService executor;
        
        public ParallelExecutor(DurableContext context) {
            this.context = context;
            this.futures = new ArrayList<>();
            this.executor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
            );
        }
        
        public <T> CompletableFuture<T> submit(String stepId, Callable<T> fn) {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return context.step(stepId, fn);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, executor);
            
            futures.add(future);
            return future;
        }
        
        public void await() throws Exception {
            try {
                CompletableFuture<Void> allOf = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
                );
                allOf.join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                throw e;
            } finally {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    
    public void resetWorkflow(String workflowId) throws SQLException {
        store.clearWorkflow(workflowId);
        System.out.println("[WorkflowEngine] Reset workflow: " + workflowId);
    }
    
    public void close() throws SQLException {
        store.close();
    }
    
    @FunctionalInterface
    public interface WorkflowFunction<T> {
        T execute(DurableContext ctx) throws Exception;
    }
}