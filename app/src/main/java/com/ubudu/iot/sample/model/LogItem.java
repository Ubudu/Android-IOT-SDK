package com.ubudu.iot.sample.model;

/**
 * Created by mgasztold on 09/06/2017.
 */

public class LogItem {

    public static final String TYPE_MESSAGE = "MESSAGE";
    public static final String TYPE_ERROR = "ERROR";

    private String logMessage;
    private String type;

    public LogItem(String logMessage, String type) {
        this.logMessage = logMessage;
        this.type = type;
    }

    public String getLogMessage() {
        return logMessage;
    }

    public String getType() {
        return type;
    }
}
