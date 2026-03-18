package com.root.aishopback.netty.message;

public class MonitorMessage {
    // Message type: HEARTBEAT or DATA
    private String type;
    
    // Account info
    private String account;

    // Hardware usage
    private double cpuUsage;
    private double memoryUsage;
    private double diskUsage;
    private int networkLatency;
    
    // IP Address
    private String ip;
    private long timestamp;
    private String signature;

    public MonitorMessage() {
    }

    public MonitorMessage(String type, String account, double cpuUsage, double memoryUsage) {
        this.type = type;
        this.account = account;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public double getDiskUsage() {
        return diskUsage;
    }

    public void setDiskUsage(double diskUsage) {
        this.diskUsage = diskUsage;
    }

    public int getNetworkLatency() {
        return networkLatency;
    }

    public void setNetworkLatency(int networkLatency) {
        this.networkLatency = networkLatency;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    @Override
    public String toString() {
        return "MonitorMessage{" +
                "type='" + type + '\'' +
                ", account='" + account + '\'' +
                ", ip='" + ip + '\'' +
                ", timestamp=" + timestamp +
                ", signature='" + signature + '\'' +
                ", cpuUsage=" + cpuUsage +
                ", memoryUsage=" + memoryUsage +
                ", diskUsage=" + diskUsage +
                ", networkLatency=" + networkLatency +
                '}';
    }
}
