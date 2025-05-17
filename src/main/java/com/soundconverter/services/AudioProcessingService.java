package com.soundconverter.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.soundconverter.models.AudioFile;
import com.soundconverter.models.MergedAudio;
import com.soundconverter.models.MergedAudio.MergeSegment;

public class AudioProcessingService {
    
    private static final String FFMPEG_PATH = "./src/main/resources/ffmpeg/";
    private static final String FFMPEG_EXE = FFMPEG_PATH + "ffmpeg.exe";
    private static final String FFPROBE_EXE = FFMPEG_PATH + "ffprobe.exe";
    private static final String OUTPUT_DIR = "./output/";
    
    private static AudioProcessingService instance;
    
    private AudioProcessingService() {
        // Create output directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create output directory: " + e.getMessage());
        }
    }
    
    public static synchronized AudioProcessingService getInstance() {
        if (instance == null) {
            instance = new AudioProcessingService();
        }
        return instance;
    }
    
    public boolean isFFmpegInstalled() {
        File ffmpegFile = new File(FFMPEG_EXE);
        File ffprobeFile = new File(FFPROBE_EXE);
        return ffmpegFile.exists() && ffprobeFile.exists();
    }
    
    public static String getInstallationGuide() {
        return "# FFmpeg Installation Guide\n\n" +
               "## Windows Installation\n\n" +
               "1. Download FFmpeg for Windows from: https://ffmpeg.org/download.html#build-windows\n" +
               "2. Extract the zip file\n" +
               "3. Copy 'ffmpeg.exe' and 'ffprobe.exe' from the 'bin' directory to: " + FFMPEG_PATH + "\n";
    }
    
    /**
     * Extract a segment from an audio file
     * @param sourceFile The source audio file
     * @param startTime Start time in seconds
     * @param endTime End time in seconds
     * @return Path to the extracted segment file
     */
    public String extractSegment(String sourceFile, int startTime, int endTime) throws IOException {
        // Ensure output directory exists
        File outputDirFile = new File(OUTPUT_DIR);
        if (!outputDirFile.exists()) {
            boolean created = outputDirFile.mkdirs();
            if (!created) {
                throw new IOException("Failed to create output directory: " + OUTPUT_DIR);
            }
        }
        
        String outputFilename = new File(OUTPUT_DIR, UUID.randomUUID().toString() + ".mp3").getAbsolutePath();
        
        List<String> command = new ArrayList<>();
        command.add(FFMPEG_EXE);
        command.add("-i");
        command.add(sourceFile);
        command.add("-ss");
        command.add(formatTime(startTime));
        command.add("-to");
        command.add(formatTime(endTime));
        command.add("-c");
        command.add("copy");
        command.add(outputFilename);
        
        System.out.println("Executing FFmpeg extract command: " + String.join(" ", command));
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                System.out.println("[FFmpeg Extract] " + line);
            }
        }
        
        try {
            boolean completed = process.waitFor(30, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("FFmpeg extract process timed out");
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("FFmpeg extract process failed with exit code " + exitCode + ":\n" + output);
            }
        } catch (InterruptedException e) {
            throw new IOException("FFmpeg extract process interrupted: " + e.getMessage());
        }
        
        File outputFile = new File(outputFilename);
        if (!outputFile.exists()) {
            throw new IOException("Failed to extract segment - output file not created");
        }
        
        if (outputFile.length() == 0) {
            throw new IOException("Failed to extract segment - output file is empty");
        }
        
        return outputFilename;
    }
    
    /**
     * Merge segments from multiple audio files
     * @param mergedAudio The merged audio object containing segments to merge
     * @param audioFiles List of audio files with their information
     * @return Path to the merged audio file
     */
    public String mergeSegments(MergedAudio mergedAudio, List<AudioFile> audioFiles) throws IOException {
        // Get the user-specified output path
        String outputPath = mergedAudio.getFilePath();
        
        // Create a temporary directory for the segment files
        String tempDirName = "temp_" + UUID.randomUUID().toString();
        File tempDir = new File(OUTPUT_DIR, tempDirName);
        if (!tempDir.exists()) {
            boolean created = tempDir.mkdirs();
            if (!created) {
                throw new IOException("Failed to create temporary directory: " + tempDir.getAbsolutePath());
            }
        }
        
        // Create a file list for FFmpeg
        StringBuilder fileList = new StringBuilder();
        List<String> extractedFiles = new ArrayList<>();
        
        try {
            // Extract each segment
            int segmentIndex = 0;
            for (MergeSegment segment : mergedAudio.getSegments()) {
                // Find the source audio file
                AudioFile sourceFile = null;
                for (AudioFile file : audioFiles) {
                    if (file.getId() == segment.getSourceFileId()) {
                        sourceFile = file;
                        break;
                    }
                }
                
                if (sourceFile == null) {
                    throw new IOException("Source file not found for segment");
                }
                
                System.out.println("Processing segment from: " + sourceFile.getFilePath());
                System.out.println("Start time: " + segment.getStartTime() + ", End time: " + segment.getEndTime());
                
                // Extract the segment
                String segmentFile = extractSegment(
                        sourceFile.getFilePath(),
                        segment.getStartTime() / 1000, // Convert ms to seconds
                        segment.getEndTime() / 1000    // Convert ms to seconds
                );
                
                // Rename the segment file to ensure correct order
                File orderedSegmentFile = new File(tempDir, String.format("%03d.mp3", segmentIndex));
                Files.move(Paths.get(segmentFile), orderedSegmentFile.toPath(), 
                           java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                
                // Add to file list - use absolute path with proper escaping
                String escapedPath = orderedSegmentFile.getAbsolutePath().replace("\\", "/");
                fileList.append("file '").append(escapedPath).append("'\n");
                extractedFiles.add(orderedSegmentFile.getAbsolutePath());
                
                segmentIndex++;
            }
            
            // Write the file list
            File fileListFile = new File(tempDir, "list.txt");
            Files.write(fileListFile.toPath(), fileList.toString().getBytes());
            
            // Verify file list was created
            if (!fileListFile.exists() || fileListFile.length() == 0) {
                throw new IOException("Failed to create file list for FFmpeg");
            }
            
            System.out.println("File list created at: " + fileListFile.getAbsolutePath());
            System.out.println("File list contents:\n" + fileList.toString());
            
            // Use the user-specified output path directly
            
            // Merge using FFmpeg
            List<String> command = new ArrayList<>();
            command.add(FFMPEG_EXE);
            command.add("-f");
            command.add("concat");
            command.add("-safe");
            command.add("0");
            command.add("-i");
            command.add(fileListFile.getAbsolutePath());
            command.add("-c");
            command.add("copy");
            command.add(outputPath);
            
            System.out.println("Executing FFmpeg command: " + String.join(" ", command));
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    System.out.println("[FFmpeg] " + line);
                }
            }
            
            try {
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new IOException("FFmpeg process failed with exit code " + exitCode + ":\n" + output);
                }
                
                // Verify the output file was created
                File outputFile = new File(outputPath);
                if (!outputFile.exists() || outputFile.length() == 0) {
                    throw new IOException("Output file was not created or is empty: " + outputPath);
                }
                
                return outputPath;
            } catch (InterruptedException e) {
                throw new IOException("FFmpeg process interrupted: " + e.getMessage());
            }
        } finally {
            // Clean up temp files
            cleanupTempDir(tempDir.getAbsolutePath());
        }
    }
    
    private void cleanupTempDir(String tempDir) {
        try {
            File dir = new File(tempDir);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            dir.delete();
        } catch (Exception e) {
            System.err.println("Error cleaning up temp directory: " + e.getMessage());
        }
    }
    
    /**
     * Format time in seconds to HH:MM:SS.mmm format for FFmpeg
     */
    private String formatTime(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
    
    /**
     * Format time in milliseconds to HH:MM:SS.mmm format for FFmpeg
     */
    private String formatTimeWithMs(int milliseconds) {
        int totalSeconds = milliseconds / 1000;
        int ms = milliseconds % 1000;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);
    }
    
    /**
     * Get the duration of an audio file in seconds
     */
    public int getAudioDuration(String filePath) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(FFPROBE_EXE);
        command.add("-v");
        command.add("error");
        command.add("-show_entries");
        command.add("format=duration");
        command.add("-of");
        command.add("default=noprint_wrappers=1:nokey=1");
        command.add(filePath);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }
        
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            throw new IOException("FFprobe process interrupted: " + e.getMessage());
        }
        
        try {
            float duration = Float.parseFloat(output.toString().trim());
            return (int) Math.ceil(duration);
        } catch (NumberFormatException e) {
            throw new IOException("Failed to parse audio duration: " + output);
        }
    }
} 