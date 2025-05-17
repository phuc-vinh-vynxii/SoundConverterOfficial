package com.soundconverter.models;

import java.util.ArrayList;
import java.util.List;

public class AudioFile {
    private int id;
    private String fileName;
    private String filePath;
    private List<AudioSegment> segments;

    public AudioFile() {
        segments = new ArrayList<>();
    }

    public AudioFile(int id, String fileName, String filePath) {
        this.id = id;
        this.fileName = fileName;
        this.filePath = filePath;
        this.segments = new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public List<AudioSegment> getSegments() {
        return segments;
    }

    public void setSegments(List<AudioSegment> segments) {
        this.segments = segments;
    }

    public void addSegment(AudioSegment segment) {
        this.segments.add(segment);
    }

    @Override
    public String toString() {
        return fileName;
    }
} 