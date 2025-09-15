    package com.distributed.process.core;

    import com.distributed.process.config.RedisConfig;
    import com.distributed.process.model.CustomMessage;
    import org.slf4j.LoggerFactory;
    import redis.clients.jedis.Jedis;
    import com.google.gson.Gson;

    import java.io.FileReader;
    import java.io.IOException;
    import java.util.Scanner;
    import org.slf4j.Logger;

    public class CommandSend {
        public static void main(String[] args) {

            Scanner sc = new Scanner(System.in);
            RedisConfig.initialize();
            int processID = 0;

            Logger logger = LoggerFactory.getLogger(CommandSend.class);
            Gson gson = new Gson();

            try (Scanner fileScanner = new Scanner(new java.io.File("processID.txt"))) {
                if (fileScanner.hasNextInt()) {
                    processID = fileScanner.nextInt();
                }
            } catch (IOException e) {
                logger.error("Error reading processID.txt: {}", e.getMessage());
            }


            try (Jedis jedis = RedisConfig.getJedis()) {
                while (true) {
                    System.out.print("> ");
                    String message = sc.nextLine().trim();

                    if (message.equalsIgnoreCase("exit")) {
                        System.out.println("Exiting...");
                        break;
                    }
                    CustomMessage customMessage = new CustomMessage(processID, message, "REQUEST");
                    jedis.publish("custom",gson.toJson(customMessage));

                }

            } catch (Exception e) {
                logger.warn(e.getMessage());
            }
            sc.close();
        }
    }
