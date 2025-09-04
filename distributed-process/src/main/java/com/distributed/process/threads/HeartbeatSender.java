package com.distributed.process.threads;

import com.distributed.process.communication.RedisMessageBroker;
import com.distributed.process.model.HeartbeatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Heartbeat Sender Thread
 * ส่ง heartbeat message ทุก 1 วินาที เพื่อแสดงว่า process ยังมีชีวิตอยู่
 * รันเป็น background thread ตลอดเวลา
 */
public class HeartbeatSender extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatSender.class);

    private final String processId;                 // PID ของ process นี้
    private final RedisMessageBroker messageBroker; // สำหรับส่งข้อความ
    private volatile boolean running = true;        // ควบคุมการทำงานของ thread

    public HeartbeatSender(String processId, RedisMessageBroker messageBroker) {
        this.processId = processId;
        this.messageBroker = messageBroker;
        this.setName("HeartbeatSender-" + processId);
        this.setDaemon(true);  // ให้เป็น daemon thread
    }

    @Override
    public void run() {
        logger.info("Heartbeat sender started for PID={}", processId);

        while (running) {
            try {
                // สร้าง heartbeat message
                HeartbeatMessage heartbeat = new HeartbeatMessage(processId);

                // ส่งผ่าน Redis Pub/Sub
                messageBroker.publishHeartbeat(heartbeat);

                logger.debug("Sent heartbeat: PID={} at {}", processId, System.currentTimeMillis());

                // รอ 1 วินาที ก่อนส่งครั้งถัดไป
                Thread.sleep(1000);

            } catch (InterruptedException e) {
                logger.info("Heartbeat sender interrupted for PID={}", processId);
                break;
            } catch (Exception e) {
                logger.error("Error in heartbeat sender for PID={}: {}", processId, e.getMessage());

                // รอสักครู่แล้วลองใหม่
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }

        logger.info("Heartbeat sender stopped for PID={}", processId);
    }

    /**
     * หยุดการทำงานของ heartbeat sender
     */
    public void stopSending() {
        running = false;
        this.interrupt();
    }
}