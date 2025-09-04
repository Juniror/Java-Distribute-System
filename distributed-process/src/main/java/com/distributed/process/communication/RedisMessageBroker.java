package com.distributed.process.communication;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import com.distributed.process.config.RedisConfig;
import com.distributed.process.model.HeartbeatMessage;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
     Redis Message Broker
     จัดการการส่งและรับข้อความผ่าน Redis Pub/Sub
     ใช้สำหรับ heartbeat และ election messages
 */
public class RedisMessageBroker {
    private static final Logger logger = LoggerFactory.getLogger(RedisMessageBroker.class);

    // Redis Channels
    public static final String HEARTBEAT_CHANNEL = "heartbeat";     // ช่อง heartbeat
    public static final String ELECTION_CHANNEL = "election";       // ช่อง boss election

    private final Gson gson;                    // แปลง Object เป็น JSON
    private final String processId;             // PID ของ process นี้

    public RedisMessageBroker(String processId) {
        this.processId = processId;
        this.gson = new Gson();
    }

    /*
         ส่ง Heartbeat Message ไปที่ Redis channel
         @param message HeartbeatMessage ที่จะส่ง
     */
    public void publishHeartbeat(HeartbeatMessage message) {
        try (Jedis jedis = RedisConfig.getJedis()) {
            // แปลง Object เป็น JSON string
            String jsonMessage = gson.toJson(message);

            // ส่งข้อความไปยัง heartbeat channel
            jedis.publish(HEARTBEAT_CHANNEL, jsonMessage);

            logger.debug("Sent heartbeat: PID={}", message.getProcessId());

        } catch (Exception e) {
            logger.error("Failed to publish heartbeat: {}", e.getMessage());
        }
    }

    /*
        ส่ง Election Message สำหรับการเลือก Boss
        @param messageType ประเภทข้อความ (ELECTION, ANSWER, COORDINATOR)
        @param targetProcessId PID ของผู้รับ (ถ้ามี)
     */
    public void publishElection(String messageType, String targetProcessId) {
        try (Jedis jedis = RedisConfig.getJedis()) {
            // สร้าง election message
            ElectionMessage electionMsg = new ElectionMessage(processId, messageType, targetProcessId);
            String jsonMessage = gson.toJson(electionMsg);

            // ส่งข้อความไปยัง election channel
            jedis.publish(ELECTION_CHANNEL, jsonMessage);

            logger.info("Sent election message: {} from PID={}", messageType, processId);

        } catch (Exception e) {
            logger.error("Failed to publish election message: {}", e.getMessage());
        }
    }

    /**
     * Subscribe ฟัง Heartbeat messages จาก processes อื่น
     * @param callback MessageCallback สำหรับประมวลผลข้อความที่รับได้
     */
    public void subscribeHeartbeat(MessageCallback callback) {
        // สร้าง thread ใหม่สำหรับ subscribe (เพราะ subscribe จะ block thread)
        Thread subscriberThread = new Thread(() -> {
            try (Jedis jedis = RedisConfig.getJedis()) {

                // สร้าง JedisPubSub listener
                JedisPubSub pubSub = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        try {
                            // แปลง JSON กลับเป็น Object
                            HeartbeatMessage heartbeat = gson.fromJson(message, HeartbeatMessage.class);

                            // ไม่ประมวลผล heartbeat ของตัวเอง
                            if (!heartbeat.getProcessId().equals(processId)) {
                                callback.onHeartbeatReceived(heartbeat);
                                logger.debug("Received heartbeat from PID={}", heartbeat.getProcessId());
                            }

                        } catch (Exception e) {
                            logger.error("Failed to process heartbeat message: {}", e.getMessage());
                        }
                    }
                };

                logger.info("Started listening for heartbeats on channel: {}", HEARTBEAT_CHANNEL);

                // เริ่ม subscribe (จะ block จนกว่าจะ unsubscribe)
                jedis.subscribe(pubSub, HEARTBEAT_CHANNEL);

            } catch (Exception e) {
                logger.error("Heartbeat subscription failed: {}", e.getMessage());
            }
        });

        subscriberThread.setName("HeartbeatSubscriber-" + processId);
        subscriberThread.setDaemon(true);   // ให้ thread นี้เป็น daemon thread
        subscriberThread.start();
    }

    /**
     * Subscribe ฟัง Election messages
     * @param callback MessageCallback สำหรับประมวลผลข้อความ election
     */
    public void subscribeElection(MessageCallback callback) {
        Thread subscriberThread = new Thread(() -> {
            try (Jedis jedis = RedisConfig.getJedis()) {

                JedisPubSub pubSub = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        try {
                            // แปลง JSON กลับเป็น ElectionMessage
                            ElectionMessage electionMsg = gson.fromJson(message, ElectionMessage.class);

                            // ไม่ประมวลผลข้อความของตัวเอง
                            if (!electionMsg.getSenderProcessId().equals(processId)) {
                                callback.onElectionReceived(electionMsg);
                                logger.info("Received election: {} from PID={}",
                                        electionMsg.getMessageType(), electionMsg.getSenderProcessId());
                            }

                        } catch (Exception e) {
                            logger.error("Failed to process election message: {}", e.getMessage());
                        }
                    }
                };

                logger.info("Started listening for elections on channel: {}", ELECTION_CHANNEL);
                jedis.subscribe(pubSub, ELECTION_CHANNEL);

            } catch (Exception e) {
                logger.error("Election subscription failed: {}", e.getMessage());
            }
        });

        subscriberThread.setName("ElectionSubscriber-" + processId);
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    //Interface สำหรับ callback functions
    public interface MessageCallback {
        void onHeartbeatReceived(HeartbeatMessage message);
        void onElectionReceived(ElectionMessage message);
    }
}
