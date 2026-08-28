package it.javaWS.models.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Snapshot delle metriche di sistema esposto da GET /api/status.
 */
@Getter
@AllArgsConstructor
public class ServerStatusDTO {

    private Instant timestamp;
    private String hostname;
    private long uptimeSeconds;
    private Cpu cpu;
    private Memory memory;
    private Disk disk;
    private Network network;
    private Http http;

    @Getter
    @AllArgsConstructor
    public static class Cpu {
        private int cores;
        private double usagePercent;
    }

    @Getter
    @AllArgsConstructor
    public static class Memory {
        private long totalBytes;
        private long usedBytes;
        private long freeBytes;
        private double usagePercent;
    }

    @Getter
    @AllArgsConstructor
    public static class Disk {
        private long totalBytes;
        private long usedBytes;
        private long freeBytes;
        private double usagePercent;
    }

    @Getter
    @AllArgsConstructor
    public static class Network {
        private long rxBytesTotal;
        private long txBytesTotal;
    }

    @Getter
    @AllArgsConstructor
    public static class Http {
        private long requestsTotal;
        private long bytesInTotal;
        private long bytesOutTotal;
        private long requests2xx;
        private long requests4xx;
        private long requests5xx;
        private long totalTimeMs;
    }
}
