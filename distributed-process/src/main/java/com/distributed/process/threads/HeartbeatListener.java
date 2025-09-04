package com.distributed.process.threads;

import com.distributed.process.communication.ElectionMessage;
import com.distributed.process.communication.RedisMessageBroker;
import com.distributed.process.model.HeartbeatMessage;
import com.distributed.process.model.ProcessInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Heartbeat Listener Thread
 * รับ heartbeat messages จาก processes อื่น และอัปเดต Member List
 * รันเป็น background thread ตลอดเวลา
 */
public class HeartbeatListener extends Thread implements RedisMessageBroker.MessageCallback {
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatListener.class);

    private final String processId;                                     // PID ของ process นี้
    private final RedisMessageBroker messageBroker;                    // สำหรับรับข้อความ
    private final ConcurrentHashMap<String, ProcessInfo> memberList;   // รายชื่อ processes ทั้งหมด
    private volatile boolean running = true;

    public HeartbeatListener(String processId, RedisMessageBroker messageBroker,
                             ConcurrentHashMap<String, ProcessInfo> memberList) {
        this.processId = processId;
        this.messageBroker = messageBroker;
        this.memberList = memberList;
        this.setName("HeartbeatListener-" + processId);
        this.setDaemon(true);
    }

    @Override
    public void run() {
        logger.info("Heartbeat listener started for PID={}", processId);

        // เริ่ม subscribe รับ heartbeat และ election messages
        messageBroker.subscribeHeartbeat(this);
        messageBroker.subscribeElection(this);

        // รอให้ thread ทำงานต่อไป
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.info("Heartbeat listener interrupted for PID={}", processId);
                break;
            }
        }

        logger.info("Heartbeat listener stopped for PID={}", processId);
    }

    /**
     * Callback เมื่อได้รับ heartbeat message
     * อัปเดต member list ด้วยข้อมูลใหม่
     */
    @Override
    public void onHeartbeatReceived(HeartbeatMessage message) {
        String senderId = message.getProcessId();

        // อัปเดตหรือเพิ่ม process ใน member list
        ProcessInfo processInfo = memberList.computeIfAbsent(senderId, ProcessInfo::new);
        processInfo.updateHeartbeat();  // อัปเดตเวลา heartbeat ล่าสุด

        logger.debug("Updated heartbeat for PID={}, total members: {}",
                senderId, memberList.size());

        // แสดง member list ทุก 10 heartbeats
        if (System.currentTimeMillis() % 10000 < 1000) {
            printMemberList();
        }
    }

    /**
     * Callback เมื่อได้รับ election message
     * จะถูกใช้ใน FailureDetector thread
     */
    @Override
    public void onElectionReceived(ElectionMessage message) {
        String messageType = message.getMessageType();
        String senderPid = message.getSenderProcessId();

        if ("ELECTION".equals(messageType)) {
            // ถ้าได้รับ ELECTION และ PID เราสูงกว่า ให้ตอบกลับ
            if (Integer.parseInt(processId) > Integer.parseInt(senderPid)) {
                messageBroker.publishElection("ANSWER", senderPid);
                logger.info("Replied ANSWER to election from PID={}", senderPid);
            }
        } else if ("COORDINATOR".equals(messageType)) {
            // อัปเดต boss ใหม่
            memberList.values().forEach(p -> p.setBoss(false));
            ProcessInfo newBoss = memberList.get(senderPid);
            if (newBoss != null) {
                newBoss.setBoss(true);
            }
            logger.info("New coordinator announced: PID={}", senderPid);
        }
    }

    /**
     * แสดงรายชื่อ processes ทั้งหมดใน cluster
     */
    private void printMemberList() {
        logger.info("Current member list ({}): {}",
                memberList.size(),
                memberList.keySet());
    }

    /**
     * ได้รับ member list สำหรับ threads อื่น
     */
    public ConcurrentHashMap<String, ProcessInfo> getMemberList() {
        return memberList;
    }

    /**
     * หยุดการทำงานของ listener
     */
    public void stopListening() {
        running = false;
        this.interrupt();
    }
}