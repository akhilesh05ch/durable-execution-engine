package engine;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

public class DurableContext {

    private final String workflowId;
    private final StepStore store;
    private final AtomicInteger sequenceCounter;
    private final Gson gson;

    public DurableContext(String workflowId, StepStore store) {
        this.workflowId = workflowId;
        this.store = store;
        this.sequenceCounter = new AtomicInteger(0);
        this.gson = new Gson();
    }

    /**
     * Durable Step Execution
     *
     * Behavior:
     * COMPLETED -> return cached result
     * FAILED -> retry execution
     * NOT EXISTS -> execute and store result
     */
    public <T> T step(String id, Callable<T> fn) throws Exception {

        // Generate unique sequence ID
        int sequence = sequenceCounter.getAndIncrement();
        String stepKey = id + "_" + sequence;

        System.out.println("[DurableContext] Executing step: " + stepKey);

        // Check if step already exists in database
        Optional<StepRecord> existing = store.getStep(workflowId, stepKey);

        if (existing.isPresent()) {

            StepRecord record = existing.get();

            // Case 1: Already completed -> return cached result
            if ("COMPLETED".equals(record.getStatus())) {

                System.out.println(
                        "[DurableContext] Step " + stepKey +
                        " already completed, returning cached result"
                );

                String output = record.getOutput();

                TypeToken<ResultWrapper<T>> typeToken =
                        new TypeToken<ResultWrapper<T>>() {};

                ResultWrapper<T> wrapper =
                        gson.fromJson(output, typeToken.getType());

                return wrapper.getValue();
            }

            // Case 2: Previously failed -> retry execution
            if ("FAILED".equals(record.getStatus())) {

                System.out.println(
                        "[DurableContext] Step " + stepKey +
                        " previously failed, retrying execution..."
                );

                // Continue execution below (retry)
            }
        }

        // Execute step function
        try {

            T result = fn.call();

            // Serialize result
            ResultWrapper<T> wrapper = new ResultWrapper<>(result);
            String serialized = gson.toJson(wrapper);

            // Save success record
            StepRecord record = new StepRecord(
                    workflowId,
                    stepKey,
                    "COMPLETED",
                    serialized
            );

            store.saveStep(record);

            System.out.println(
                    "[DurableContext] Step " + stepKey +
                    " completed successfully"
            );

            return result;

        } catch (Exception e) {

            // Save failure record
            StepRecord record = new StepRecord(
                    workflowId,
                    stepKey,
                    "FAILED",
                    null
            );

            store.saveStep(record);

            System.out.println(
                    "[DurableContext] Step " + stepKey +
                    " failed: " + e.getMessage()
            );

            throw e;
        }
    }

    /**
     * Wrapper class to store generic result safely
     */
    private static class ResultWrapper<T> {

        private T value;

        public ResultWrapper(T value) {
            this.value = value;
        }

        public T getValue() {
            return value;
        }
    }

    public String getWorkflowId() {
        return workflowId;
    }
}
