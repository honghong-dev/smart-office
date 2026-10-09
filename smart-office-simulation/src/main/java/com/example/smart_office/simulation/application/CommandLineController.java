package com.example.smart_office.simulation.application;

import com.example.smart_office.simulation.domain.DeviceRegistry;
import com.example.smart_office.simulation.domain.FaultType;
import com.example.smart_office.simulation.domain.ProtectionAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Scanner;

/**
 * 命令行交互控制器
 *
 * <p>支持的命令：
 * <pre>
 *   inject {deviceId} {faultType}   注入故障
 *   reset {deviceId}                 复位
 *   random {probability}             随机批量注入（0.0~1.0）
 *   status                           查看所有设备状态
 *   help                             帮助
 *   quit                             退出
 * </pre>
 */
@Component
public class CommandLineController {

    private static final Logger log = LoggerFactory.getLogger(CommandLineController.class);

    private final FaultManagementService faultService;
    private final DeviceRegistry registry;

    public CommandLineController(FaultManagementService faultService,
                                 DeviceRegistry registry) {
        this.faultService = faultService;
        this.registry = registry;
    }

    /**
     * 应用就绪后启动命令行监听（在独立线程里，不阻塞主线程）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Thread cmdThread = new Thread(this::listen, "cmd-console");
        cmdThread.setDaemon(true);
        cmdThread.start();
    }

    private void listen() {
        printHelp();
        try (Scanner scanner = new Scanner(System.in)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;
                try {
                    handle(line);
                } catch (Exception e) {
                    log.error("命令执行失败：{}", line, e);
                }
            }
        }
    }

    private void handle(String line) {
        String[] parts = line.split("\\s+");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "inject":
                if (parts.length < 3) {
                    System.out.println("用法: inject {deviceId} {faultType}");
                    System.out.println("faultType 可选: BEARING_WEAR / OVERHEAT / SENSOR_DRIFT / SUDDEN_FAILURE");
                    return;
                }
                String deviceId = parts[1];
                String faultName = parts[2].toUpperCase();
                try {
                    FaultType type = FaultType.valueOf(faultName);
                    faultService.injectFault(deviceId, type);
                    System.out.println("✅ 已注入 " + type.getDescription() + " 到 " + deviceId);
                } catch (IllegalArgumentException e) {
                    System.out.println("❌ 未知故障类型: " + faultName);
                }
                break;

            case "reset":
                if (parts.length < 2) {
                    System.out.println("用法: reset {deviceId}");
                    return;
                }
                ProtectionAction action = faultService.reset(parts[1]);
                if (action != null) {
                    System.out.println("✅ " + parts[1] + " 复位成功");
                } else {
                    System.out.println("❌ 复位失败（状态不是 FAULT 或条件不满足）");
                }
                break;

            case "random":
                double p = parts.length >= 2 ? Double.parseDouble(parts[1]) : 0.1;
                int n = faultService.randomInject(p);
                System.out.println("✅ 随机注入 " + n + " 台设备");
                break;

            case "status":
                System.out.println("===== 设备状态 =====");
                registry.getAll().forEach(System.out::println);
                break;

            case "help":
                printHelp();
                break;

            case "quit":
            case "exit":
                System.out.println("退出（应用仍在运行，Ctrl+C 结束进程）");
                System.exit(0);
                break;

            default:
                System.out.println("未知命令: " + cmd + "，输入 help 查看帮助");
        }
    }

    private void printHelp() {
        System.out.println("\n===== 可用命令 =====");
        System.out.println("  inject {deviceId} {faultType}   注入故障");
        System.out.println("      faultType: BEARING_WEAR / OVERHEAT / SENSOR_DRIFT / SUDDEN_FAILURE");
        System.out.println("  reset {deviceId}                 复位");
        System.out.println("  random {probability}             随机批量注入（默认 0.1）");
        System.out.println("  status                           查看所有设备状态");
        System.out.println("  help                             帮助");
        System.out.println("  quit                             退出");
        System.out.println("========================\n");
    }
}