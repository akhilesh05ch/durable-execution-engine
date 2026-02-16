package engine;

import java.sql.*;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

public class StepStore {
    private final Connection connection;
    private final ReentrantLock writeLock = new ReentrantLock();
    
    public StepStore(String dbPath) throws SQLException {
        String url = "jdbc:sqlite:" + dbPath;
        this.connection = DriverManager.getConnection(url);
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA busy_timeout=5000");
        }
        
        initializeDatabase();
    }
    
    private void initializeDatabase() throws SQLException {
        String createTable = """
            CREATE TABLE IF NOT EXISTS steps (
                workflow_id TEXT NOT NULL,
                step_key TEXT NOT NULL,
                status TEXT NOT NULL,
                output TEXT,
                timestamp INTEGER NOT NULL,
                PRIMARY KEY (workflow_id, step_key)
            )
            """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }
    
    public Optional<StepRecord> getStep(String workflowId, String stepKey) throws SQLException {
        String query = "SELECT * FROM steps WHERE workflow_id = ? AND step_key = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, workflowId);
            stmt.setString(2, stepKey);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                StepRecord record = new StepRecord();
                record.setWorkflowId(rs.getString("workflow_id"));
                record.setStepKey(rs.getString("step_key"));
                record.setStatus(rs.getString("status"));
                record.setOutput(rs.getString("output"));
                record.setTimestamp(rs.getLong("timestamp"));
                return Optional.of(record);
            }
        }
        
        return Optional.empty();
    }
    
    public void saveStep(StepRecord record) throws SQLException {
        writeLock.lock();
        try {
            String insert = """
                INSERT OR REPLACE INTO steps (workflow_id, step_key, status, output, timestamp)
                VALUES (?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = connection.prepareStatement(insert)) {
                stmt.setString(1, record.getWorkflowId());
                stmt.setString(2, record.getStepKey());
                stmt.setString(3, record.getStatus());
                stmt.setString(4, record.getOutput());
                stmt.setLong(5, record.getTimestamp());
                stmt.executeUpdate();
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    public void clearWorkflow(String workflowId) throws SQLException {
        writeLock.lock();
        try {
            String delete = "DELETE FROM steps WHERE workflow_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(delete)) {
                stmt.setString(1, workflowId);
                stmt.executeUpdate();
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}