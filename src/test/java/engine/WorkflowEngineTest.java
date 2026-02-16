package engine;

import org.junit.jupiter.api.*;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WorkflowEngineTest {
    private static final String TEST_DB = "test_workflow.db";
    private WorkflowEngine engine;
    
    @BeforeEach
    void setUp() throws Exception {
        File dbFile = new File(TEST_DB);
        if (dbFile.exists()) {
            dbFile.delete();
        }
        
        engine = new WorkflowEngine(TEST_DB);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        engine.close();
        
        File dbFile = new File(TEST_DB);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("Test basic step execution")
    void testBasicStepExecution() throws Exception {
        String result = engine.executeWorkflow("test-001", ctx -> {
            String step1 = ctx.step("step1", () -> "Hello");
            String step2 = ctx.step("step2", () -> step1 + " World");
            return step2;
        });
        
        assertEquals("Hello World", result);
    }
    
    @Test
    @Order(2)
    @DisplayName("Test step memoization")
    void testStepMemoization() throws Exception {
        AtomicInteger executionCount = new AtomicInteger(0);
        
        String result1 = engine.executeWorkflow("test-002", ctx -> {
            String step1 = ctx.step("step1", () -> {
                executionCount.incrementAndGet();
                return "First";
            });
            
            String step2 = ctx.step("step2", () -> {
                executionCount.incrementAndGet();
                return "Second";
            });
            
            return step1 + " " + step2;
        });
        
        assertEquals("First Second", result1);
        assertEquals(2, executionCount.get());
        
        executionCount.set(0);
        
        String result2 = engine.executeWorkflow("test-002", ctx -> {
            String step1 = ctx.step("step1", () -> {
                executionCount.incrementAndGet();
                return "First";
            });
            
            String step2 = ctx.step("step2", () -> {
                executionCount.incrementAndGet();
                return "Second";
            });
            
            return step1 + " " + step2;
        });
        
        assertEquals("First Second", result2);
        assertEquals(0, executionCount.get());
    }
    
    @Test
    @Order(3)
    @DisplayName("Test workflow resume after failure")
    void testWorkflowResumeAfterFailure() throws Exception {
        AtomicInteger executionCount = new AtomicInteger(0);
        
        try {
            engine.executeWorkflow("test-003", ctx -> {
                ctx.step("step1", () -> {
                    executionCount.incrementAndGet();
                    return "Step 1";
                });
                
                ctx.step("step2", () -> {
                    executionCount.incrementAndGet();
                    return "Step 2";
                });
                
                ctx.step("step3", () -> {
                    executionCount.incrementAndGet();
                    throw new RuntimeException("Simulated failure");
                });
                
                return "Success";
            });
            fail("Should have thrown exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Simulated failure"));
        }
        
        assertEquals(3, executionCount.get());
        executionCount.set(0);
        
        String result = engine.executeWorkflow("test-003", ctx -> {
            ctx.step("step1", () -> {
                executionCount.incrementAndGet();
                return "Step 1";
            });
            
            ctx.step("step2", () -> {
                executionCount.incrementAndGet();
                return "Step 2";
            });
            
            ctx.step("step3", () -> {
                executionCount.incrementAndGet();
                return "Step 3 - Fixed";
            });
            
            ctx.step("step4", () -> {
                executionCount.incrementAndGet();
                return "Step 4";
            });
            
            return "Success";
        });
        
        assertEquals("Success", result);
        assertEquals(2, executionCount.get());
    }
    
    @Test
    @Order(4)
    @DisplayName("Test parallel execution")
    void testParallelExecution() throws Exception {
        long startTime = System.currentTimeMillis();
        
        engine.executeWorkflow("test-004", ctx -> {
            WorkflowEngine.ParallelExecutor parallel = new WorkflowEngine.ParallelExecutor(ctx);
            
            parallel.submit("task1", () -> {
                Thread.sleep(1000);
                return "Task 1";
            });
            
            parallel.submit("task2", () -> {
                Thread.sleep(1000);
                return "Task 2";
            });
            
            parallel.submit("task3", () -> {
                Thread.sleep(1000);
                return "Task 3";
            });
            
            parallel.await();
            return "All tasks complete";
        });
        
        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 2000);
    }
    
    @Test
    @Order(5)
    @DisplayName("Test sequence tracking in loops")
    void testSequenceTrackingInLoops() throws Exception {
        String result = engine.executeWorkflow("test-005", ctx -> {
            StringBuilder sb = new StringBuilder();
            
            for (int i = 0; i < 3; i++) {
                final int index = i;
                String stepResult = ctx.step("loop-step", () -> {
                    return "Iteration-" + index;
                });
                sb.append(stepResult).append(" ");
            }
            
            return sb.toString().trim();
        });
        
        assertEquals("Iteration-0 Iteration-1 Iteration-2", result);
    }
    
    @Test
    @Order(6)
    @DisplayName("Test workflow reset")
    void testWorkflowReset() throws Exception {
        AtomicInteger executionCount = new AtomicInteger(0);
        
        engine.executeWorkflow("test-006", ctx -> {
            ctx.step("step1", () -> {
                executionCount.incrementAndGet();
                return "Data";
            });
            return "Done";
        });
        
        assertEquals(1, executionCount.get());
        
        engine.resetWorkflow("test-006");
        executionCount.set(0);
        
        engine.executeWorkflow("test-006", ctx -> {
            ctx.step("step1", () -> {
                executionCount.incrementAndGet();
                return "Data";
            });
            return "Done";
        });
        
        assertEquals(1, executionCount.get());
    }
    
    @Test
    @Order(7)
    @DisplayName("Test different return types")
    void testDifferentReturnTypes() throws Exception {
        engine.executeWorkflow("test-007", ctx -> {
            String stringResult = ctx.step("string-step", () -> "Hello");
            assertEquals("Hello", stringResult);
            
            Integer intResult = ctx.step("int-step", () -> 42);
            assertEquals(42, intResult);
            
            Boolean boolResult = ctx.step("bool-step", () -> true);
            assertTrue(boolResult);
            
            Double doubleResult = ctx.step("double-step", () -> 3.14);
            assertEquals(3.14, doubleResult, 0.001);
            
            return "All types tested";
        });
    }
    
    @Test
    @Order(8)
    @DisplayName("Test error propagation in parallel execution")
    void testErrorPropagationInParallel() {
        assertThrows(Exception.class, () -> {
            engine.executeWorkflow("test-008", ctx -> {
                WorkflowEngine.ParallelExecutor parallel = new WorkflowEngine.ParallelExecutor(ctx);
                
                parallel.submit("task1", () -> {
                    Thread.sleep(500);
                    return "Success";
                });
                
                parallel.submit("task2", () -> {
                    Thread.sleep(300);
                    throw new RuntimeException("Task 2 failed");
                });
                
                parallel.await();
                return "Should not reach here";
            });
        });
    }
}