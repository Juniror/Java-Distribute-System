package com.distributed.process;

import com.distributed.process.core.DistributedProcess;
import com.distributed.process.config.RedisConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

/*
    Main application สำหรับรัน Distributed Process
    รันหลาย instance พร้อมกันได้ (มั้ง)
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // รับ Process ID จาก command line หรือ input
        int processId = getProcessId(args);
        try (FileWriter fileWriter = new FileWriter("processID.txt",false)){
            fileWriter.write(String.valueOf(processId));
        }catch (IOException e){
            logger.error("{}",e.getMessage());
        }
        logger.info("Starting Distributed Process System - Process {}", processId);

        // สร้างและเริ่ม process
        DistributedProcess process = new DistributedProcess(processId);

        // เพิ่ม shutdown hook เพื่อปิดโปรแกรมอย่างสะอาด
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received...");
            process.shutdown();
            RedisConfig.shutdown();
        }));

        // เริ่มต้น process
        process.start();

        // Command line interface
        startCommandInterface(process);
    }

    //รับ Process ID จาก command line arguments หรือจาก user input
    private static int getProcessId(String[] args) {
        if (args.length > 0) {
            try {
                return Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.error("Invalid process ID: {}", args[0]);
            }
        }

        // ถ้าไม่มี argument ให้รับจาก user input
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter Process ID : ");
        return scanner.nextInt();
    }

    //Command line interface สำหรับควบคุม process (อันนี้อาจลบ)
    private static void startCommandInterface(DistributedProcess process) {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        logger.info("Process {} is running. Commands: status, quit", process.getProcessId());

        while (running) {
            System.out.print("Process " + process.getProcessId() + " > ");
            String command = scanner.nextLine().trim().toLowerCase();

            switch (command) {
                case "status":
                case "s":
                    process.printStatus();
                    break;

                case "quit":
                case "q":
                case "exit":
                    logger.info("Shutting down Process {}...", process.getProcessId());
                    running = false;
                    break;

                case "help":
                case "h":
                    System.out.println("Available commands:");
                    System.out.println("  status (s) - Show process status");
                    System.out.println("  quit (q)   - Shutdown process");
                    System.out.println("  help (h)   - Show this help");
                    break;

                default:
                    if (!command.isEmpty()) {
                        System.out.println("Unknown command: " + command + ". Type 'help' for available commands.");
                    }
                    break;
            }
        }

        process.shutdown();
        RedisConfig.shutdown();
        System.exit(0);
    }
}