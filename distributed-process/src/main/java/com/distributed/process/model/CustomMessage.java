package com.distributed.process.model;

public class CustomMessage {
    private int processId;
    private String message;
    private String type; // REQUEST , BROARDCAST

    public CustomMessage(int processId, String payload,String type) {
        this.processId = processId;
        this.message = payload;
        this.type = type;
    }

    public int getProcessId() { return processId; }
    public  void setProcessId(int processId){this.processId = processId;}
    public String getMessage() { return message; }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return String.format("Process[%d] CUSTOM: %s", processId, message);
    }
}
