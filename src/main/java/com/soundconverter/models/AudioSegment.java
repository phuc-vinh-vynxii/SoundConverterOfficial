package com.soundconverter.models;

public class AudioSegment {
    private int id;
    private int fileId;
    private int startTime; // in milliseconds
    private int endTime;   // in milliseconds
    private String text;

    public AudioSegment() {
    }

    public AudioSegment(int id, int fileId, int startTime, int endTime, String text) {
        this.id = id;
        this.fileId = fileId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.text = text;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getFileId() {
        return fileId;
    }

    public void setFileId(int fileId) {
        this.fileId = fileId;
    }

    public int getStartTime() {
        return startTime;
    }

    public void setStartTime(int startTime) {
        this.startTime = startTime;
    }

    public int getEndTime() {
        return endTime;
    }

    public void setEndTime(int endTime) {
        this.endTime = endTime;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getFormattedStartTime() {
        int hours = startTime / 3600000;
        int minutes = (startTime % 3600000) / 60000;
        int seconds = (startTime % 60000) / 1000;
        int millis = startTime % 1000;
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
    }

    public String getFormattedEndTime() {
        int hours = endTime / 3600000;
        int minutes = (endTime % 3600000) / 60000;
        int seconds = (endTime % 60000) / 1000;
        int millis = endTime % 1000;
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
    }

    @Override
    public String toString() {
        return getFormattedStartTime() + " - " + getFormattedEndTime() + ": " + text;
    }
} 