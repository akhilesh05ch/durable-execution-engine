# Durable Execution Engine

A Java implementation of a durable workflow execution system that survives crashes and resumes from the exact point of failure without re-executing completed steps.

## Overview

Traditional programs lose all state when they crash. This engine makes workflows **durable** - they can be interrupted at any point and resume exactly where they left off.

**Real-world use case**: Processing payment workflows, employee onboarding, data pipelines - any multi-step process where you don't want to restart from the beginning after a failure.

## Key Features

- ✅ **Fault Tolerance** - Workflows survive crashes and resume automatically
- ✅ **Step Memoization** - Completed steps never re-execute
- ✅ **Parallel Execution** - Built-in support for concurrent steps
- ✅ **Type Safety** - Full Java Generics support
- ✅ **Thread Safety** - Safe concurrent database access
- ✅ **Simple API** - Write normal Java code with loops and conditionals

## Architecture

### Core Components

```
User Workflow
    ↓
WorkflowEngine (orchestration)
    ↓
DurableContext (step execution & memoization)
    ↓
StepStore (thread-safe database access)
    ↓
SQLite Database (persistent storage)
```

**How it works**:
1. Each step execution is saved to SQLite before returning
2. On crash, the workflow restarts and checks the database
3. Completed steps return cached results (no re-execution)
4. Incomplete steps execute normally

### Database Schema

```sql
CREATE TABLE steps (
    workflow_id TEXT,          -- Identifies the workflow instance
    step_key TEXT,             -- Unique: stepId_sequenceNumber
    status TEXT,               -- "COMPLETED" or "FAILED"
    output TEXT,               -- JSON serialized result
    timestamp INTEGER,
    PRIMARY KEY (workflow_id, step_key)
);
```

## How Sequence Tracking Works

**The Problem**: How do you uniquely identify steps in loops?

```java
for (int i = 0; i < 3; i++) {
    ctx.step("process", () -> work(i));  // Same ID, different iterations
}
```

**The Solution**: Atomic sequence counter

```java
private final AtomicInteger sequenceCounter = new AtomicInteger(0);

// Generates unique keys:
// "process_0", "process_1", "process_2"
```

Each step gets: `stepId + "_" + sequenceNumber`

**Why this works**:
- ✅ Deterministic - same execution order every time
- ✅ Thread-safe - AtomicInteger handles concurrency
- ✅ Simple - no complex parsing or code analysis
- ✅ Works with all control flow (loops, conditionals, nested structures)

## Thread Safety Mechanisms

Multiple parallel steps writing to the database require careful synchronization:

### Layer 1: Unique Step Keys
```java
AtomicInteger counter ensures:
Thread 1: "laptop_2"
Thread 2: "access_3"
→ Different keys = no collision
```

### Layer 2: SQLite WAL Mode
```java
PRAGMA journal_mode=WAL;  // Concurrent reads allowed
PRAGMA busy_timeout=5000; // Wait for locks
```

### Layer 3: Java ReentrantLock
```java
writeLock.lock();
try {
    // Only one thread writes at a time
    database.insert(step);
} finally {
    writeLock.unlock();
}
```

**Result**: Safe concurrent execution with no race conditions.

## Usage Examples

### Basic Sequential Workflow

```java
WorkflowEngine engine = new WorkflowEngine("workflow.db");

String result = engine.executeWorkflow("order-123", ctx -> {
    Order order = ctx.step("validate", () -> validateOrder());
    Payment pay = ctx.step("payment", () -> processPayment(order));
    String track = ctx.step("ship", () -> shipOrder(order));
    return track;
});
```

### Parallel Execution

```java
engine.executeWorkflow("data-process", ctx -> {
    RawData data = ctx.step("fetch", () -> fetchData());
    
    // Run these concurrently
    ParallelExecutor parallel = new ParallelExecutor(ctx);
    var task1 = parallel.submit("process-1", () -> processUsers(data));
    var task2 = parallel.submit("process-2", () -> processOrders(data));
    parallel.await();
    
    return ctx.step("finalize", () -> createReport(task1.get(), task2.get()));
});
```

### Loops

```java
engine.executeWorkflow("batch", ctx -> {
    List<Item> items = ctx.step("fetch", () -> getItems());
    
    for (Item item : items) {
        // Each iteration gets unique sequence number
        ctx.step("process", () -> processItem(item));
    }
    
    return "Processed " + items.size() + " items";
});
```

## Building and Running

### Prerequisites
- Java 17+
- Maven 3.6+

### Build

```bash
mvn clean package
```

### Run Demo

```bash
java -jar target/durable-execution-engine-1.0.0.jar
```

### Run Tests

```bash
mvn test
```

Expected output:
```
Tests run: 8, Failures: 0, Errors: 0
BUILD SUCCESS
```

## Demo Application

The CLI demonstrates durability with an employee onboarding workflow:

**Workflow Steps**:
1. Create employee record (sequential, 2s)
2. Provision laptop (parallel, 3s) + Setup access (parallel, 2.5s)
3. Send welcome email (sequential, 1.5s)

**Testing Durability**:

```bash
# Step 1: Reset workflow state
Choose: 3

# Step 2: Simulate crash
Choose: 2
# Workflow starts, then crashes mid-execution

# Step 3: Restart app and resume
java -jar target/durable-execution-engine-1.0.0.jar
Choose: 1
# Completed steps show "already completed, returning cached result"
# Incomplete steps execute normally
```

**Key observation**: Employee record ID stays the same after crash, proving the step wasn't re-executed.

## Test Suite

### 8 Comprehensive Tests

1. **testBasicStepExecution** - Verify steps execute and return results
2. **testStepMemoization** - Prove completed steps aren't re-executed
3. **testWorkflowResumeAfterFailure** - Verify resume from failure point
4. **testParallelExecution** - Confirm steps run concurrently (timing test)
5. **testSequenceTrackingInLoops** - Verify loop iterations get unique IDs
6. **testWorkflowReset** - Confirm state clearing works
7. **testDifferentReturnTypes** - Test String, Integer, Boolean, Double
8. **testErrorPropagationInParallel** - Verify exceptions propagate correctly

Run with:
```bash
mvn test
```

All tests pass with 100% success rate.

