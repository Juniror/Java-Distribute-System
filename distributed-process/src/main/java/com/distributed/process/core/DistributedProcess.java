package com.distributed.process.core;

import com.distributed.process.config.RedisConfig;
import com.distributed.process.model.CustomMessage;
import com.distributed.process.model.HeartbeatMessage;
import com.distributed.process.model.ElectionMessage;
import com.google.gson.Gson;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/*
    หลัก Process class ที่จัดการ Multi-threading และ Distributed communication
    ใช้ Redis Pub/Sub ในการสื่อสารระหว่าง process
 */
public class DistributedProcess {
    private static final Logger logger = LoggerFactory.getLogger(DistributedProcess.class);

    // Redis channels
    private static final String HEARTBEAT_CHANNEL = "heartbeat";
    private static final String ELECTION_CHANNEL = "election";
    private  static  final String CUSTOMM_CHANNEL = "custom";

    // Process configuration
    private final int processId;
    private final AtomicInteger currentBoss = new AtomicInteger(-1);
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final AtomicBoolean electionInProgress = new AtomicBoolean(false);

    // Thread management
    private final ExecutorService threadPool;
    private final ScheduledExecutorService scheduler;

    // Process tracking
    private final ConcurrentHashMap<Integer, Long> processLastSeen = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    // Constants
    private static final long HEARTBEAT_INTERVAL = 1000; // 1 second
    private static final long FAILURE_TIMEOUT = 20000;  // 20 seconds
    private static final long ELECTION_TIMEOUT = 5000;  // 5 seconds

    public DistributedProcess(int processId) {
        this.processId = processId;
        this.threadPool = Executors.newFixedThreadPool(5);
        this.scheduler = Executors.newScheduledThreadPool(3);

        // Initialize Redis connection
        RedisConfig.initialize();

        logger.info("Process {} initialized", processId);
    }

    //เริ่มต้น process และสร้าง 3 main threads
    public void start() {
        logger.info("Starting Process {}", processId);

        // Thread 1: Heartbeat Sender - ส่ง heartbeat ทุก 1 วินาที
        startHeartbeatSender();

        // Thread 2: Message Listener - รับฟัง message จาก Redis
        startMessageListener();

        // Thread 3: Failure Detector & Boss Election
        startFailureDetector();

        // เริ่มต้น Boss Election ครั้งแรก
        scheduler.schedule(this::initiateElection, 2000, TimeUnit.MILLISECONDS);

        logger.info("Process {} started successfully", processId);
    }

    /*
        Thread 1: Heartbeat Sender Thread
        ส่งข้อความ heartbeat ทุก 1 วินาทีเพื่อแจ้งว่า process นี้ยังมีชีวิต
     */
    private void startHeartbeatSender() {
        scheduler.scheduleAtFixedRate(() -> {
            try (Jedis jedis = RedisConfig.getJedis()) {
                HeartbeatMessage heartbeat = new HeartbeatMessage(
                        processId,
                        System.currentTimeMillis(),
                        "ALIVE"
                );

                String message = gson.toJson(heartbeat);
                jedis.publish(HEARTBEAT_CHANNEL, message);

                logger.debug("Process {} sent heartbeat", processId);
            } catch (Exception e) {
                logger.error("Error sending heartbeat from Process {}: {}", processId, e.getMessage());
            }
        }, 1000, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /*
        Thread 2: Message Listener Thread
        รับ heartbeat และ election message จาก Redis Pub/Sub
     */
    private void startMessageListener() {
        threadPool.submit(() -> {
            try (Jedis jedis = RedisConfig.getJedis()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        try {
                            if (CUSTOMM_CHANNEL.equals(channel)){
                                handleCustomMessage(message);
                            } else if (HEARTBEAT_CHANNEL.equals(channel)) {
                                handleHeartbeatMessage(message);
                            } else if (ELECTION_CHANNEL.equals(channel)) {
                                handleElectionMessage(message);
                            }
                        } catch (Exception e) {
                            logger.error("Error processing message in Process {}: {}", processId, e.getMessage());
                        }
                    }
                }, HEARTBEAT_CHANNEL, ELECTION_CHANNEL,CUSTOMM_CHANNEL);
            } catch (Exception e) {
                logger.error("Message listener error in Process {}: {}", processId, e.getMessage());
            }
        });
    }

    /*
       Thread 3: Failure Detector & Boss Election Thread
       ตรวจสอบ process ที่ตายและเริ่ม election หาก boss ตาย
     */
    private void startFailureDetector() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long currentTime = System.currentTimeMillis();
                AtomicBoolean bossAlive = new AtomicBoolean(false);

                // ตรวจสอบ process ที่ตาย
                processLastSeen.entrySet().removeIf(entry -> {
                    int pid = entry.getKey();
                    long lastSeen = entry.getValue();

                    if (currentTime - lastSeen > FAILURE_TIMEOUT) {
                        logger.warn("Process {} detected as DEAD (timeout)", pid);

                        // ถ้า boss ตาย
                        if (pid == currentBoss.get()) {
                            logger.warn("BOSS Process {} is DEAD! Starting election...", pid);
                            currentBoss.set(-1);
                            initiateElection();
                        }
                        return true; // remove from map
                    }

                    // ตรวจสอบว่า boss ยังมีชีวิต
                    if (pid == currentBoss.get()){
                        bossAlive.set(true);
                    }

                    return false; // keep in map
                });

                // หาก boss หายไปแต่ไม่มีใน processLastSeen
                if (currentBoss.get() != -1 && !bossAlive.get() && !processLastSeen.containsKey(currentBoss.get())) {
                    logger.warn("BOSS Process {} not found! Starting election...", currentBoss.get());
                    currentBoss.set(-1);
                    initiateElection();
                }

            } catch (Exception e) {
                logger.error("Failure detector error in Process {}: {}", processId, e.getMessage());
            }
        }, 5000, 5000, TimeUnit.MILLISECONDS);
    }
    //จัดการ heartbeat message ที่ได้รับ
    private void handleHeartbeatMessage(String message) {
        try {
            HeartbeatMessage heartbeat = gson.fromJson(message, HeartbeatMessage.class);

            // ไม่ต้องประมวลผล heartbeat ของตัวเอง
            if (heartbeat.getProcessId() == this.processId) {
                return;
            }

            // อัพเดท timestamp ของ process ที่ส่ง heartbeat
            processLastSeen.put(heartbeat.getProcessId(), heartbeat.getTimestamp());

            logger.debug("Process {} received heartbeat from Process {}",
                    processId, heartbeat.getProcessId());

        } catch (Exception e) {
            logger.error("Error parsing heartbeat message: {}", e.getMessage());
        }
    }


    //จัดการ election message (Bully Algorithm)
    private void handleElectionMessage(String message) {
        try {
            ElectionMessage election = gson.fromJson(message, ElectionMessage.class);

            // ไม่ต้องประมวลผล message ของตัวเอง
            if (election.getProcessId() == this.processId) {
                return;
            }

            switch (election.getMessageType()) {
                case "ELECTION":
                    handleElectionRequest(election);
                    break;
                case "ANSWER":
                    handleElectionAnswer(election);
                    break;
                case "COORDINATOR":
                    handleCoordinatorAnnouncement(election);
                    break;
            }

        } catch (Exception e) {
            logger.error("Error parsing election message: {}", e.getMessage());
        }
    }

    //จัดการคำขอ election (Bully(ไม่ดีต่อสุขภาพ นั่นมันบุหรี่!) Algorithm)
    private void handleElectionRequest(ElectionMessage election) {
        // หาก process ID นี้มากกว่า ให้ตอบกลับและเริ่ม election process นี้
        if (this.processId > election.getProcessId()) {
            logger.info("Process {} received ELECTION from lower Process {}, sending ANSWER",
                    processId, election.getProcessId());

            // ส่ง ANSWER กลับ
            sendElectionMessage("ANSWER");

            // เริ่ม election ของ process นี้
            initiateElection();
        }
    }

    //จัดการ ANSWER message
    private void handleElectionAnswer(ElectionMessage election) {
        logger.info("Process {} received ANSWER from higher Process {}, stopping election",
                processId, election.getProcessId());
        electionInProgress.set(false);
    }

    //จัดการประกาศ COORDINATOR ใหม่
    private void handleCoordinatorAnnouncement(ElectionMessage election) {
        logger.info("Process {} acknowledges Process {} as new BOSS",
                processId, election.getProcessId());
        currentBoss.set(election.getProcessId());
        electionInProgress.set(false);
    }


    //เริ่มต้น Boss Election (Bully Algorithm)
    private void initiateElection() {
        if (!electionInProgress.compareAndSet(false, true)) {
            return; // Election already in progress
        }

        logger.info("Process {} starting BOSS ELECTION", processId);

        // หา process ที่มี ID สูงกว่า
        boolean hasHigherProcess = false;
        for (Integer pid : processLastSeen.keySet()) {
            if (pid > this.processId) {
                hasHigherProcess = true;
                break;
            }
        }

        if (hasHigherProcess) {
            // ส่ง ELECTION message ไปยัง process ที่มี ID สูงกว่า
            sendElectionMessage("ELECTION");

            // รอ ANSWER timeout
            scheduler.schedule(() -> {
                if (electionInProgress.get()) {
                    // ไม่ได้รับ ANSWER ดังนั้นอันเก่าเป็น BOSS
                    becomeBoss();
                }
            }, ELECTION_TIMEOUT, TimeUnit.MILLISECONDS);

        } else {
            // ไม่มี process ใดที่มี ID สูงกว่า ดังนั้น Process เดิมเป็น BOSS
            becomeBoss();
        }
    }

    //ประกาศตัวเองเป็น BOSS
    private void becomeBoss() {
        currentBoss.set(this.processId);
        electionInProgress.set(false);

        logger.info("Process {} is now the BOSS!", processId);

        // ประกาศเป็น COORDINATOR
        sendElectionMessage("COORDINATOR");
    }
    private void handleCustomMessage(String message) {
        CustomMessage customMessage = gson.fromJson(message,CustomMessage.class);
        if (customMessage.getType().equals("REQUEST") ){
            // ถ้าหากตัวนีน้เป็นบอส
            if (getCurrentBoss() == processId && customMessage.getProcessId() == processId){
                customMessage.setType("BROARDCAST");
                sendCustomMessage(customMessage);
            }
        }else if (customMessage.getType().equals("BROARDCAST")){
            if (processId != customMessage.getProcessId()){
                logger.info("message : {}",customMessage.getMessage());
            }
        }
    }
    public void sendCustomMessage(CustomMessage customMessage) {
        try(Jedis jedis = RedisConfig.getJedis()) {
            jedis.publish(CUSTOMM_CHANNEL,gson.toJson(customMessage));
            logger.info("Process {} send custom message : {}",customMessage.getProcessId(),customMessage.getMessage());
        }
    }

    //ส่ง election message ผ่าน Redis
    private void sendElectionMessage(String messageType) {
        try (Jedis jedis = RedisConfig.getJedis()) {
            ElectionMessage election = new ElectionMessage(
                    processId,
                    messageType,
                    System.currentTimeMillis()
            );

            String message = gson.toJson(election);
            jedis.publish(ELECTION_CHANNEL, message);

            logger.info("Process {} sent {} message", processId, messageType);

        } catch (Exception e) {
            logger.error("Error sending election message: {}", e.getMessage());
        }
    }

    //แสดงสถานะปัจจุบันของ process
    public void printStatus() {
        logger.info("=== Process {} Status ===", processId);
        logger.info("Current BOSS: {}", currentBoss.get() == -1 ? "NONE" : currentBoss.get());
        logger.info("Active Processes: {}", processLastSeen.keySet());
        logger.info("Election in Progress: {}", electionInProgress.get());
        logger.info("========================");
    }

    //หยุดการทำงานของ process
    public void shutdown() {
        logger.info("Shutting down Process {}", processId);

        isRunning.set(false);
        threadPool.shutdown();
        scheduler.shutdown();

        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            scheduler.shutdownNow();
        }

        logger.info("Process {} shutdown complete", processId);
    }
    public int getProcessId() { return processId; }
    public int getCurrentBoss() { return currentBoss.get(); }
    public boolean isElectionInProgress() { return electionInProgress.get(); }
}