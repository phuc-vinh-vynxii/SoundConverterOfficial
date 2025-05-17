package com.soundconverter.models;

import java.util.ArrayList;
import java.util.List;

public class MergedAudio {
    private int id;
    private String fileName;
    private String filePath;
    private List<MergeSegment> segments;

    public MergedAudio() {
        segments = new ArrayList<>();
    }

    public MergedAudio(int id, String fileName, String filePath) {
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

    public List<MergeSegment> getSegments() {
        return segments;
    }

    public void setSegments(List<MergeSegment> segments) {
        this.segments = segments;
    }

    public void addSegment(MergeSegment segment) {
        this.segments.add(segment);
    }

    @Override
    public String toString() {
        return fileName;
    }

    public static class MergeSegment {
        private int sourceFileId;
        private int startTime;
        private int endTime;

        public MergeSegment(int sourceFileId, int startTime, int endTime) {
            this.sourceFileId = sourceFileId;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public int getSourceFileId() {
            return sourceFileId;
        }

        public void setSourceFileId(int sourceFileId) {
            this.sourceFileId = sourceFileId;
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
    }
} 