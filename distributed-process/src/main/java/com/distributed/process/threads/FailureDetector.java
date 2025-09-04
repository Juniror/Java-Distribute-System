package com.distributed.process.threads;

import com.distributed.process.communication.ElectionMessage;
import com.distributed.process.communication.RedisMessageBroker;
import com.distributed.process.model.ProcessInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Failure Detector & Boss Election Thread
 * ตรวจสอบ processes ที่ตายแล้ว (เกิน 20 วินาที) และเลือก Boss ใหม่
 * รันทุก 5 วินาที เพื่อตรวจสอบสถานะ
 */
public class FailureDetector extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(FailureDetector.class);

    private final String processId;                                     // PID ของ process นี้
    private final RedisMessageBroker messageBroker;                    // สำหรับส่งข้อความ election
    private final ConcurrentHashMap<String, ProcessInfo> memberList;   // รายชื่อ processes
    private volatile boolean running = true;
    private volatile boolean isElectionInProgress = false;             // ป้องกัน election หลายครั้งพร้อมกัน

    // Election timeouts
    private static final long ELECTION_TIMEOUT = 5000;     // 5 วินาทีรอ response
    private static final long COORDINATOR_TIMEOUT = 3000;  // 3 วินาทีรอ coordinator

    public FailureDetector(String processId, RedisMessageBroker messageBroker,
                           ConcurrentHashMap<String, ProcessInfo> memberList) {
        this.processId = processId;
        this.messageBroker = messageBroker;
        this.memberList = memberList;
        this.setName("FailureDetector-" + processId);
        this.setDaemon(true);
    }

    @Override
    public void run() {
        logger.info("Failure detector started for PID={}", processId);

        while (running) {
            try {
                // ตรวจสอบ failures ทุก 5 วินาที
                checkForFailures();

                // ตรวจสอบว่าต้องเลือก Boss ใหม่หรือไม่
                checkBossElection();

                Thread.sleep(5000); // รอ 5 วินาที

            } catch (InterruptedException e) {
                logger.info("Failure detector interrupted for PID={}", processId);
                break;
            } catch (Exception e) {
                logger.error("Error in failure detector for PID={}: {}", processId, e.getMessage());
            }
        }

        logger.info("🔍 Failure detector stopped for PID={}", processId);
    }

    /**
     * ตรวจสอบ processes ที่ตายแล้ว (เกิน 20 วินาที)
     */
    private void checkForFailures() {
        List<String> failedProcesses = new ArrayList<>();

        for (ProcessInfo process : memberList.values()) {
            if (process.isExpired()) {
                process.setAlive(false);
                failedProcesses.add(process.getProcessId());
                logger.warn("Process PID={} is dead (no heartbeat for >20s)",
                        process.getProcessId());
            }
        }

        // ลบ failed processes ออกจาก member list
        for (String failedPid : failedProcesses) {
            memberList.remove(failedPid);
            logger.info("Removed dead process PID={} from member list", failedPid);
        }

        if (!failedProcesses.isEmpty()) {
            logger.info("Detected {} failed processes", failedProcesses.size());
        }
    }

    /**
     * ตรวจสอบว่าต้องเลือก Boss ใหม่หรือไม่
     */
    private void checkBossElection() {
        // หา current boss
        ProcessInfo currentBoss = findCurrentBoss();

        if (currentBoss == null || !currentBoss.isAlive()) {
            logger.warn("No Boss found or Boss is dead, starting election...");
            startElection();
        } else {
            logger.debug("Current Boss: PID={}", currentBoss.getProcessId());
        }
    }

    /**
     * หา current boss จาก member list
     */
    private ProcessInfo findCurrentBoss() {
        return memberList.values().stream()
                .filter(ProcessInfo::isBoss)
                .filter(ProcessInfo::isAlive)
                .findFirst()
                .orElse(null);
    }

    /**
     * เริ่มการเลือก Boss ใหม่ด้วย Bully Algorithm
     */
    private void startElection() {
        if (isElectionInProgress) {
            logger.debug("Election already in progress, skipping...");
            return;
        }

        isElectionInProgress = true;
        logger.info("Starting Boss election from PID={}", processId);

        try {
            // หา processes ที่มี PID สูงกว่าตัวเอง
            List<String> higherProcesses = getHigherProcesses();

            if (higherProcesses.isEmpty()) {
                // ไม่มี process ไหนที่มี PID สูงกว่า -> ประกาศตัวเองเป็น Boss
                becomeBoss();
            } else {
                // ส่ง ELECTION message ไปยัง processes ที่มี PID สูงกว่า
                for (String higherPid : higherProcesses) {
                    messageBroker.publishElection("ELECTION", higherPid);
                }

                // รอ response เป็นเวลา ELECTION_TIMEOUT
                Thread.sleep(ELECTION_TIMEOUT);

                // ถ้าไม่มีใครตอบ -> ประกาศตัวเองเป็น Boss
                becomeBoss();
            }
        } catch (Exception e) {
            logger.error("Error during election: {}", e.getMessage());
        } finally {
            isElectionInProgress = false;
        }
    }

    /**
     * หา processes ที่มี PID สูงกว่าตัวเอง
     */
    private List<String> getHigherProcesses() {
        List<String> higherProcesses = new ArrayList<>();

        for (ProcessInfo process : memberList.values()) {
            if (process.isAlive() &&
                    Integer.parseInt(process.getProcessId()) > Integer.parseInt(processId)) {
                higherProcesses.add(process.getProcessId());
            }
        }

        return higherProcesses;
    }

    /**
     * ประกาศตัวเองเป็น Boss
     */
    private void becomeBoss() {
        // เคลียร์ boss เก่า
        memberList.values().forEach(p -> p.setBoss(false));

        // ตั้งตัวเองเป็น boss
        ProcessInfo myProcess = memberList.computeIfAbsent(processId, ProcessInfo::new);
        myProcess.setBoss(true);

        // ประกาศให้ processes อื่นทราบ
        messageBroker.publishElection("COORDINATOR", null);

        logger.info("I am the new Boss! PID={}", processId);
    }

    /**
     * หยุดการทำงานของ failure detector
     */
    public void stopDetecting() {
        running = false;
        this.interrupt();
    }
}
