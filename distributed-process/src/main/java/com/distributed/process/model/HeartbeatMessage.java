package com.distributed.process.model;

//Heartbeat message ที่ส่งระหว่าง process
public class HeartbeatMessage {
    private int processId;
    private long timestamp;
    private String status; // "ALIVE", "DEAD", "ELECTION", "BOSS"

    public HeartbeatMessage() {}

    public HeartbeatMessage(int processId, long timestamp, String status) {
        this.processId = processId;
        this.timestamp = timestamp;
        this.status = status;
    }

    public int getProcessId() { return processId; }
    public void setProcessId(int processId) { this.processId = processId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return String.format("Process[%d] %s at %d", processId, status, timestamp);
    }
}