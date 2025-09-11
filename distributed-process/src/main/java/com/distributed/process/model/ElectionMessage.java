package com.distributed.process.model;

//Election message ของ Boss Election
public class ElectionMessage {
    private int processId;
    private String messageType; // "ELECTION", "ANSWER", "COORDINATOR"
    private long timestamp;

    public ElectionMessage() {}

    public ElectionMessage(int processId, String messageType, long timestamp) {
        this.processId = processId;
        this.messageType = messageType;
        this.timestamp = timestamp;
    }

    public int getProcessId() { return processId; }
    public void setProcessId(int processId) { this.processId = processId; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return String.format("Election[%d] %s at %d", processId, messageType, timestamp);
    }
}
