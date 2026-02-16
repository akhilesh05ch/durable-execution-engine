package engine;

public class StepRecord {
    private String workflowId;
    private String stepKey;
    private String status;
    private String output;
    private long timestamp;
    
    public StepRecord() {}
    
    public StepRecord(String workflowId, String stepKey, String status, String output) {
        this.workflowId = workflowId;
        this.stepKey = stepKey;
        this.status = status;
        this.output = output;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    
    public String getStepKey() { return stepKey; }
    public void setStepKey(String stepKey) { this.stepKey = stepKey; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}