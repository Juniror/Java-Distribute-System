package com.distributed.process;

import com.distributed.process.communication.RedisMessageBroker;
import com.distributed.process.model.ProcessInfo;
import com.distributed.process.threads.HeartbeatSender;
import com.distributed.process.threads.HeartbeatListener;
import com.distributed.process.threads.FailureDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main Distributed Process Class
 * จัดการ lifecycle ของ process และ coordination ระหว่าง threads
 */
public class DistributedProcess {
    private static final Logger logger = LoggerFactory.getLogger(DistributedProcess.class);

    private final String processId;
    private final RedisMessageBroker messageBroker;
    private final ConcurrentHashMap<String, ProcessInfo> memberList;

    // Threads
    private HeartbeatSender heartbeatSender;
    private HeartbeatListener heartbeatListener;
    private FailureDetector failureDetector;

    private volatile boolean running = true;

    public DistributedProcess(String processId) {
        this.processId = processId;
        this.messageBroker = new RedisMessageBroker(processId);
        this.memberList = new ConcurrentHashMap<>();

        // เพิ่มตัวเองใน member list
        ProcessInfo myProcess = new ProcessInfo(processId);
        myProcess.updateHeartbeat();
        this.memberList.put(processId, myProcess);

        logger.info("Initialized distributed process with PID={}", processId);
    }

    /**
     * เริ่มการทำงานของ process
     */
    public void start() {
        logger.info("Starting distributed process PID={}", processId);

        try {
            // สร้างและเริ่ม threads
            createAndStartThreads();

            // แสดงสถานะเริ่มต้น
            printStartupInfo();

            // รอให้ process ทำงาน
            waitForShutdown();

        } catch (Exception e) {
            logger.error("Error starting process PID={}: {}", processId, e.getMessage());
        } finally {
            shutdown();
        }
    }

    /**
     * สร้างและเริ่ม threads ทั้งหมด
     */
    private void createAndStartThreads() {
        // Heartbeat Sender Thread
        heartbeatSender = new HeartbeatSender(processId, messageBroker);
        heartbeatSender.start();
        logger.info("Started HeartbeatSender thread");

        // Heartbeat Listener Thread
        heartbeatListener = new HeartbeatListener(processId, messageBroker, memberList);
        heartbeatListener.start();
        logger.info("Started HeartbeatListener thread");

        // Failure Detector Thread
        failureDetector = new FailureDetector(processId, messageBroker, memberList);
        failureDetector.start();
        logger.info("Started FailureDetector thread");

        // รอให้ threads เริ่มทำงาน
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * แสดงข้อมูลเริ่มต้นของ process
     */
    private void printStartupInfo() {
        logger.info("=== Distributed Process Started ===");
        logger.info("Process ID: {}", processId);
        logger.info("Redis Channels: heartbeat, election");
        logger.info("Heartbeat Interval: 1 second");
        logger.info("Failure Detection: 20 seconds timeout");
        logger.info("Boss Election: Bully Algorithm");
        logger.info("===================================");
        logger.info("Process is running... Type 'quit' to exit");
    }

    /**
     * รอให้ผู้ใช้สั่งหยุด process
     */
    private void waitForShutdown() {
        Scanner scanner = new Scanner(System.in);

        while (running) {
            try {
                System.out.print("> ");
                String input = scanner.nextLine().trim().toLowerCase();

                switch (input) {
                    case "quit":
                    case "exit":
                        logger.info("Shutdown requested by user");
                        running = false;
                        break;

                    case "status":
                        printStatus();
                        break;

                    case "members":
                        printMemberList();
                        break;

                    case "boss":
                        printCurrentBoss();
                        break;

                    case "help":
                        printHelp();
                        break;

                    default:
                        if (!input.isEmpty()) {
                            System.out.println("Unknown command: " + input + " (type 'help' for commands)");
                        }
                        break;
                }

            } catch (Exception e) {
                logger.error("Error processing user input: {}", e.getMessage());
            }
        }
    }

    /**
     * แสดงสถานะปัจจุบันของ process
     */
    private void printStatus() {
        ProcessInfo myProcess = memberList.get(processId);
        boolean isBoss = myProcess != null && myProcess.isBoss();

        System.out.println("=== Process Status ===");
        System.out.println("PID: " + processId);
        System.out.println("Role: " + (isBoss ? "BOSS" : "WORKER"));
        System.out.println("Total Members: " + memberList.size());
        System.out.println("Threads Running: " + countRunningThreads());
        System.out.println("====================");
    }

    /**
     * แสดงรายชื่อ processes ทั้งหมด
     */
    private void printMemberList() {
        System.out.println("=== Member List ===");
        if (memberList.isEmpty()) {
            System.out.println("No members found");
        } else {
            memberList.values().forEach(process -> {
                String role = process.isBoss() ? "BOSS" : "WORKER";
                String status = process.isAlive() ? "ALIVE" : "DEAD";
                System.out.printf("PID: %s | Role: %s | Status: %s%n",
                        process.getProcessId(), role, status);
            });
        }
        System.out.println("==================");
    }

    /**
     * แสดง Boss ปัจจุบัน
     */
    private void printCurrentBoss() {
        ProcessInfo boss = memberList.values().stream()
                .filter(ProcessInfo::isBoss)
                .filter(ProcessInfo::isAlive)
                .findFirst()
                .orElse(null);

        System.out.println("=== Current Boss ===");
        if (boss != null) {
            System.out.println("Boss PID: " + boss.getProcessId());
            boolean isMe = boss.getProcessId().equals(processId);
            System.out.println("Is Me: " + (isMe ? "YES" : "NO"));
        } else {
            System.out.println("No Boss found or Boss is dead");
        }
        System.out.println("==================");
    }

    /**
     * แสดงคำสั่งที่ใช้ได้
     */
    private void printHelp() {
        System.out.println("=== Available Commands ===");
        System.out.println("status  - Show process status");
        System.out.println("members - Show all members");
        System.out.println("boss    - Show current boss");
        System.out.println("help    - Show this help");
        System.out.println("quit    - Exit process");
        System.out.println("=========================");
    }

    /**
     * นับจำนวน threads ที่ทำงานอยู่
     */
    private int countRunningThreads() {
        int count = 0;
        if (heartbeatSender != null && heartbeatSender.isAlive()) count++;
        if (heartbeatListener != null && heartbeatListener.isAlive()) count++;
        if (failureDetector != null && failureDetector.isAlive()) count++;
        return count;
    }

    /**
     * หยุดการทำงานของ process
     */
    public void shutdown() {
        logger.info("Shutting down distributed process PID={}", processId);

        running = false;

        // หยุด threads
        if (heartbeatSender != null) {
            heartbeatSender.stopSending();
            logger.info("Stopped HeartbeatSender thread");
        }

        if (heartbeatListener != null) {
            heartbeatListener.stopListening();
            logger.info("Stopped HeartbeatListener thread");
        }

        if (failureDetector != null) {
            failureDetector.stopDetecting();
            logger.info("Stopped FailureDetector thread");
        }

        // รอให้ threads หยุดสมบูรณ์
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("Process PID={} shutdown completed", processId);
    }

    /**
     * Main method สำหรับรัน process
     */
    public static void main(String[] args) {
        // ตรวจสอบ command line arguments
        if (args.length != 1) {
            System.err.println("Usage: java DistributedProcess <process_id>");
            System.err.println("Example: java DistributedProcess 1");
            System.exit(1);
        }

        String processId = args[0];

        // ตรวจสอบว่า process ID เป็นตัวเลข
        try {
            Integer.parseInt(processId);
        } catch (NumberFormatException e) {
            System.err.println("Error: Process ID must be a number");
            System.exit(1);
        }

        // สร้างและเริ่ม process
        DistributedProcess process = new DistributedProcess(processId);

        // เพิ่ม shutdown hook สำหรับ graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nReceived shutdown signal...");
            process.shutdown();
        }));

        // เริ่มการทำงาน
        process.start();
    }
}