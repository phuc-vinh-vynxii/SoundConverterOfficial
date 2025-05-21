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
        // Tạo thư mục output nếu không tồn tại
        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));
        } catch (IOException e) {
            System.err.println("Không thể tạo thư mục output: " + e.getMessage());
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
     * Trích xuất một đoạn từ file âm thanh
     * @param sourceFile File âm thanh nguồn
     * @param startTime Thời gian bắt đầu tính bằng giây
     * @param endTime Thời gian kết thúc tính bằng giây
     * @return Đường dẫn đến file đoạn đã trích xuất
     */
    public String extractSegment(String sourceFile, int startTime, int endTime) throws IOException {
        // Đảm bảo thư mục output tồn tại
        File outputDirFile = new File(OUTPUT_DIR);
        if (!outputDirFile.exists()) {
            boolean created = outputDirFile.mkdirs();
            if (!created) {
                throw new IOException("Không thể tạo thư mục output: " + OUTPUT_DIR);
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
        
        System.out.println("Đang chạy lệnh FFmpeg trích xuất: " + String.join(" ", command));
        
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
                throw new IOException("Quá trình FFmpeg trích xuất đã hết thời gian chờ");
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("Quá trình FFmpeg trích xuất thất bại với mã lỗi " + exitCode + ":\n" + output);
            }
        } catch (InterruptedException e) {
            throw new IOException("Quá trình FFmpeg trích xuất bị gián đoạn: " + e.getMessage());
        }
        
        File outputFile = new File(outputFilename);
        if (!outputFile.exists()) {
            throw new IOException("Không thể trích xuất đoạn - file output không được tạo");
        }
        
        if (outputFile.length() == 0) {
            throw new IOException("Không thể trích xuất đoạn - file output rỗng");
        }
        
        return outputFilename;
    }
    
    /**
     * Trộn các đoạn từ nhiều file âm thanh
     * @param mergedAudio Đối tượng âm thanh đã trộn chứa các đoạn cần trộn
     * @param audioFiles Danh sách các file âm thanh với thông tin của chúng
     * @return Đường dẫn đến file âm thanh đã trộn
     */
    public String mergeSegments(MergedAudio mergedAudio, List<AudioFile> audioFiles) throws IOException {
        // Lấy đường dẫn output do người dùng chỉ định
        String outputPath = mergedAudio.getFilePath();
        
        // Tạo thư mục tạm cho các file đoạn
        String tempDirName = "temp_" + UUID.randomUUID().toString();
        File tempDir = new File(OUTPUT_DIR, tempDirName);
        if (!tempDir.exists()) {
            boolean created = tempDir.mkdirs();
            if (!created) {
                throw new IOException("Không thể tạo thư mục tạm: " + tempDir.getAbsolutePath());
            }
        }
        
        // Tạo danh sách file cho FFmpeg
        StringBuilder fileList = new StringBuilder();
        List<String> extractedFiles = new ArrayList<>();
        
        try {
            // Trích xuất từng đoạn
            int segmentIndex = 0;
            for (MergeSegment segment : mergedAudio.getSegments()) {
                // Tìm file âm thanh nguồn
                AudioFile sourceFile = null;
                for (AudioFile file : audioFiles) {
                    if (file.getId() == segment.getSourceFileId()) {
                        sourceFile = file;
                        break;
                    }
                }
                
                if (sourceFile == null) {
                    throw new IOException("Không tìm thấy file nguồn cho đoạn");
                }
                
                System.out.println("Đang xử lý đoạn từ: " + sourceFile.getFilePath());
                System.out.println("Thời gian bắt đầu: " + segment.getStartTime() + ", Thời gian kết thúc: " + segment.getEndTime());
                
                // Trích xuất đoạn
                String segmentFile = extractSegment(
                        sourceFile.getFilePath(),
                        segment.getStartTime() / 1000, // Chuyển ms sang giây
                        segment.getEndTime() / 1000    // Chuyển ms sang giây
                );
                
                // Đổi tên file đoạn để đảm bảo thứ tự đúng
                File orderedSegmentFile = new File(tempDir, String.format("%03d.mp3", segmentIndex));
                Files.move(Paths.get(segmentFile), orderedSegmentFile.toPath(), 
                           java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                
                // Thêm vào danh sách file - sử dụng đường dẫn tuyệt đối với escape đúng
                String escapedPath = orderedSegmentFile.getAbsolutePath().replace("\\", "/");
                fileList.append("file '").append(escapedPath).append("'\n");
                extractedFiles.add(orderedSegmentFile.getAbsolutePath());
                
                segmentIndex++;
            }
            
            // Ghi danh sách file
            File fileListFile = new File(tempDir, "list.txt");
            Files.write(fileListFile.toPath(), fileList.toString().getBytes());
            
            // Xác minh danh sách file đã được tạo
            if (!fileListFile.exists() || fileListFile.length() == 0) {
                throw new IOException("Không thể tạo danh sách file cho FFmpeg");
            }
            
            // Sử dụng đường dẫn output do người dùng chỉ định trực tiếp
            
            // Trộn bằng FFmpeg
            List<String> mergeCommand = new ArrayList<>();
            mergeCommand.add(FFMPEG_EXE);
            mergeCommand.add("-f");
            mergeCommand.add("concat");
            mergeCommand.add("-safe");
            mergeCommand.add("0");
            mergeCommand.add("-i");
            mergeCommand.add(fileListFile.getAbsolutePath());
            mergeCommand.add("-c");
            mergeCommand.add("copy");
            mergeCommand.add(outputPath);
            
            System.out.println("Đang chạy lệnh FFmpeg trộn: " + String.join(" ", mergeCommand));
            
            ProcessBuilder pb = new ProcessBuilder(mergeCommand);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    System.out.println("[FFmpeg Merge] " + line);
                }
            }
            
            try {
                boolean completed = process.waitFor(60, TimeUnit.SECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    throw new IOException("Quá trình FFmpeg trộn đã hết thời gian chờ");
                }
                
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    throw new IOException("Quá trình FFmpeg trộn thất bại với mã lỗi " + exitCode + ":\n" + output);
                }
            } catch (InterruptedException e) {
                throw new IOException("Quá trình FFmpeg trộn bị gián đoạn: " + e.getMessage());
            }
            
            // Xác minh file output đã được tạo
            File mergedFile = new File(outputPath);
            if (!mergedFile.exists()) {
                throw new IOException("Không thể trộn các đoạn - file output không được tạo");
            }
            
            if (mergedFile.length() == 0) {
                throw new IOException("Không thể trộn các đoạn - file output rỗng");
            }
            
            return outputPath;
        } finally {
            // Dọn dẹp file tạm
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
            System.err.println("Lỗi khi dọn dẹp thư mục tạm: " + e.getMessage());
        }
    }
    
    /**
     * Định dạng thời gian theo giây sang định dạng HH:MM:SS.mmm cho FFmpeg
     */
    private String formatTime(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
    
    /**
     * Định dạng thời gian theo mili giây sang định dạng HH:MM:SS.mmm cho FFmpeg
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
     * Lấy độ dài của file âm thanh theo giây
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
            throw new IOException("Quá trình FFprobe bị gián đoạn: " + e.getMessage());
        }
        
        try {
            float duration = Float.parseFloat(output.toString().trim());
            return (int) Math.ceil(duration);
        } catch (NumberFormatException e) {
            throw new IOException("Không thể phân tích độ dài âm thanh: " + output);
        }
    }
} 