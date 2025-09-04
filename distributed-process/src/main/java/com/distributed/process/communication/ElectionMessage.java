package com.distributed.process.communication;

//Election Message สำหรับการเลือก Boss ใช้ใน Bully Algorithm
public class ElectionMessage {
    private String senderProcessId;     // PID ของผู้ส่ง
    private String messageType;         // ประเภทข้อความ: ELECTION, ANSWER, COORDINATOR
    private String targetProcessId;     // PID ของผู้รับ (ถ้ามี)
    private long timestamp;             // เวลาที่ส่งข้อความ

    public ElectionMessage(String senderProcessId, String messageType, String targetProcessId) {
        this.senderProcessId = senderProcessId;
        this.messageType = messageType;
        this.targetProcessId = targetProcessId;
        this.timestamp = System.currentTimeMillis();
    }

    public String getSenderProcessId() { return senderProcessId; }
    public void setSenderProcessId(String senderProcessId) { this.senderProcessId = senderProcessId; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getTargetProcessId() { return targetProcessId; }
    public void setTargetProcessId(String targetProcessId) { this.targetProcessId = targetProcessId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return String.format("Election[From=%s, Type=%s, To=%s, Time=%d]",
                senderProcessId, messageType, targetProcessId, timestamp);
    }
}