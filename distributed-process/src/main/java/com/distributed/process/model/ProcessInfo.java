package com.distributed.process.model;

/*
  ข้อมูลของแต่ละ Process
  เก็บ PID, สถานะ, และเวลาที่ส่ง heartbeat ล่าสุด
 */
public class ProcessInfo {
    private String processId;           // PID ของ process
    private boolean isAlive;            // สถานะยังมีชีวิตหรือไม่
    private long lastHeartbeat;         // เวลาที่ส่ง heartbeat ล่าสุด (timestamp)
    private boolean isBoss;             // เป็น Boss หรือไม่


    public ProcessInfo(String processId) {
        this.processId = processId;
        this.isAlive = true;
        this.lastHeartbeat = System.currentTimeMillis();
        this.isBoss = false;
    }


    public String getProcessId() { return processId; }
    public void setProcessId(String processId) { this.processId = processId; }

    public boolean isAlive() { return isAlive; }
    public void setAlive(boolean alive) { this.isAlive = alive; }

    public long getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    public boolean isBoss() { return isBoss; }
    public void setBoss(boolean boss) { this.isBoss = boss; }


     //อัปเดต heartbeat ให้เป็นเวลาปัจจุบัน
    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
        this.isAlive = true;
    }

    //ตรวจสอบว่า process นี้ตายแล้วหรือไม่ (เกิน 20 วินาที)
    public boolean isExpired() {
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastHeartbeat) > 20000; //20 seconds
    }

    @Override
    public String toString() {
        return String.format("Process[ID=%s, Alive=%s, Boss=%s, LastHB=%d]",
                processId, isAlive, isBoss, lastHeartbeat);
    }
    //
}