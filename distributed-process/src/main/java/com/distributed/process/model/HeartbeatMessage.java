package com.distributed.process.model;

//Heartbeat Message ที่ส่งผ่าน Redis Pub/Sub ใช้ส่งข้อมูลว่า process ยังมีชีวิตอยู่
public class HeartbeatMessage {
    private String processId;       // PID ของผู้ส่ง
    private long timestamp;         // เวลาที่ส่ง heartbeat
    private String status;          // สถานะ (alive, boss, etc.)

    public HeartbeatMessage(String processId) {
        this.processId = processId;
        this.timestamp = System.currentTimeMillis();
        this.status = "alive";
    }

    public String getProcessId() { return processId; }
    public void setProcessId(String processId) { this.processId = processId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return String.format("Heartbeat[PID=%s, Time=%d, Status=%s]",
                processId, timestamp, status);
    }
}