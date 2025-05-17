package com.soundconverter.ai;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.soundconverter.dao.AudioSegmentDAO;
import com.soundconverter.models.AudioSegment;

public class WhisperService {
    
    private static final String WHISPER_CLI_PATH = "./lib/whisper-cli.exe";
    private static final String MODEL_PATH = "./models/ggml-base-q8_0.bin";
    private static final String MODEL_PATH_EN = "./models/ggml-tiny.en.bin";
    private static final String MODEL_PATH_MULTILINGUAL = "./models/ggml-base-q8_0.bin";
    private static final String TEMP_DIR = "./temp";
    
    // Các ngôn ngữ được hỗ trợ
    public static final String LANG_AUTO = "auto";
    public static final String LANG_ENGLISH = "en";
    public static final String LANG_VIETNAMESE = "vi";
    public static final String LANG_JAPANESE = "ja";
    
    private static WhisperService instance;
    private boolean initialized = false;
    private AudioSegmentDAO segmentDAO;
    
    static {
        try {
            // Tạo thư mục tạm nếu không tồn tại
            File tempDir = new File(TEMP_DIR);
            if (!tempDir.exists()) {
                tempDir.mkdirs();
                System.err.println("Đã tạo thư mục tạm: " + tempDir.getAbsolutePath());
            }
            
            // Tạo thư mục models nếu không tồn tại
            File modelsDir = new File("./models");
            if (!modelsDir.exists()) {
                modelsDir.mkdirs();
                System.err.println("Đã tạo thư mục models: " + modelsDir.getAbsolutePath());
            }
            
            // Xóa các file tạm cũ
            cleanTempDirectory();
        } catch (Exception e) {
            System.err.println("Lỗi khởi tạo thư mục: " + e.getMessage());
        }
    }
    
    // Xóa các file tạm cũ
    private static void cleanTempDirectory() {
        try {
            File tempDir = new File(TEMP_DIR);
            if (tempDir.exists() && tempDir.isDirectory()) {
                File[] tempFiles = tempDir.listFiles();
                if (tempFiles != null) {
                    for (File file : tempFiles) {
                        if (file.isFile() && (
                            file.getName().startsWith("whisper_") || 
                            file.getName().endsWith(".wav") || 
                            file.getName().endsWith(".txt"))) {
                            boolean deleted = file.delete();
                            if (deleted) {
                                System.err.println("Đã xóa file tạm: " + file.getName());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi xóa file tạm: " + e.getMessage());
        }
    }
    
    private WhisperService() {
        try {
            // Khởi tạo DAO
            segmentDAO = new AudioSegmentDAO();
            
            // Kiểm tra xem whisper CLI có tồn tại không
            File whisperExe = new File(WHISPER_CLI_PATH);
            if (!whisperExe.exists()) {
                System.err.println("Không tìm thấy Whisper CLI: " + whisperExe.getAbsolutePath());
                System.err.println("Vui lòng cài đặt Whisper CLI và đặt tại: " + WHISPER_CLI_PATH);
            } else {
                System.err.println("Tìm thấy Whisper CLI: " + whisperExe.getAbsolutePath());
                
                // Kiểm tra model
                File modelFile = new File(MODEL_PATH);
                if (!modelFile.exists()) {
                    System.err.println("Không tìm thấy model file: " + modelFile.getAbsolutePath());
                    System.err.println("Vui lòng tải model và đặt tại: " + MODEL_PATH);
                } else {
                    System.err.println("Tìm thấy model file: " + modelFile.getAbsolutePath());
                    initialized = true;
                }
                
                // Kiểm tra phiên bản whisper
                testWhisperCLI();
            }
        } catch (Exception e) {
            System.err.println("Lỗi khởi tạo WhisperService: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Kiểm tra phiên bản whisper CLI
    private void testWhisperCLI() {
        try {
            ProcessBuilder pb = new ProcessBuilder(WHISPER_CLI_PATH, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Đọc output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println("[Whisper CLI] " + line);
                }
            }
            
            // Đợi cho process kết thúc
            boolean completed = process.waitFor(10, TimeUnit.SECONDS);
            if (!completed) {
                System.err.println("Không thể kiểm tra phiên bản Whisper CLI");
                process.destroyForcibly();
            } else {
                int exitCode = process.exitValue();
                if (exitCode == 0) {
                    System.err.println("Whisper CLI hoạt động bình thường");
                } else {
                    System.err.println("Whisper CLI trả về lỗi: " + exitCode);
                    initialized = false;
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi kiểm tra Whisper CLI: " + e.getMessage());
            e.printStackTrace();
            initialized = false;
        }
    }
    
    public static synchronized WhisperService getInstance() {
        if (instance == null) {
            instance = new WhisperService();
        }
        return instance;
    }
    
    public List<AudioSegment> transcribeAudio(String audioFilePath, int fileId) {
        return transcribeAudio(audioFilePath, fileId, false, LANG_AUTO);
    }
    
    public List<AudioSegment> transcribeAudio(String audioFilePath, int fileId, boolean force) {
        return transcribeAudio(audioFilePath, fileId, force, LANG_AUTO);
    }
    
    /**
     * Phiên âm file âm thanh với ngôn ngữ cụ thể
     * @param audioFilePath Đường dẫn file âm thanh
     * @param fileId ID của file âm thanh trong database
     * @param force Bắt buộc phân tích lại ngay cả khi đã có kết quả
     * @param language Ngôn ngữ cần phát hiện (auto, en, vi, ja)
     * @return Danh sách các phân đoạn âm thanh
     */
    public List<AudioSegment> transcribeAudio(String audioFilePath, int fileId, boolean force, String language) {
        return transcribeAudio(audioFilePath, fileId, force, language, 0);
    }
    
    /**
     * Phiên âm file âm thanh với ngôn ngữ cụ thể và độ dài phân đoạn
     * @param audioFilePath Đường dẫn file âm thanh
     * @param fileId ID của file âm thanh trong database
     * @param force Bắt buộc phân tích lại ngay cả khi đã có kết quả
     * @param language Ngôn ngữ cần phát hiện (auto, en, vi, ja)
     * @param segmentLengthSeconds Độ dài mỗi phân đoạn tính bằng giây (0 = tự động phân đoạn theo Whisper)
     * @return Danh sách các phân đoạn âm thanh
     */
    public List<AudioSegment> transcribeAudio(String audioFilePath, int fileId, boolean force, String language, int segmentLengthSeconds) {
        List<AudioSegment> segments = new ArrayList<>();
        
        if (!initialized) {
            System.err.println("Whisper chưa được khởi tạo");
            return segments;
        }
        
        try {
            // Kiểm tra file âm thanh
            File audioFile = new File(audioFilePath).getAbsoluteFile();
            if (!audioFile.exists() || !audioFile.canRead()) {
                System.err.println("Không thể đọc file âm thanh: " + audioFilePath);
                return segments;
            }
            
            // Kiểm tra định dạng file âm thanh
            if (!isValidAudioFormat(audioFilePath)) {
                System.err.println("Định dạng file âm thanh không được hỗ trợ: " + audioFilePath);
                System.err.println("Whisper chỉ hỗ trợ các định dạng: WAV, MP3, FLAC, OGG, M4A");
                return segments;
            }
            
            System.err.println("Đang xử lý file âm thanh: " + audioFilePath);
            
            // Nếu không bắt buộc phân tích lại, kiểm tra xem có phân đoạn nào trong database không
            if (!force) {
                List<AudioSegment> existingSegments = segmentDAO.getSegmentsByFileId(fileId);
                if (!existingSegments.isEmpty()) {
                    System.err.println("Đã tìm thấy " + existingSegments.size() + " phân đoạn trong database");
                    return existingSegments;
                }
            } else {
                // Xóa các phân đoạn cũ nếu bắt buộc phân tích lại
                int deleted = segmentDAO.deleteSegmentsByFileId(fileId);
                System.err.println("Đã xóa " + deleted + " phân đoạn cũ");
            }
            
            // Thiết lập độ dài segment (chuyển đổi từ giây sang mili giây)
            if (segmentLengthSeconds > 0) {
                setActiveSegmentLengthMs(segmentLengthSeconds * 1000);
                System.err.println("Thiết lập độ dài segment: " + segmentLengthSeconds + " giây (" + getActiveSegmentLengthMs() + " ms)");
            } else {
                setActiveSegmentLengthMs(0); // Không nhóm segment
            }
            
            // Tạo file tạm để lưu kết quả
            String uniqueId = UUID.randomUUID().toString();
            File outputFile = new File(TEMP_DIR, "whisper_" + uniqueId + "_output.txt");
            String outputFilePath = outputFile.getAbsolutePath();
            
            // Chạy Whisper CLI với ngôn ngữ được chỉ định
            // Lưu ý: Không cần truyền segmentLengthSeconds vào processAudioWithWhisperCLI nữa
            // vì chúng ta sẽ xử lý việc nhóm segment sau khi phân tích
            boolean success = processAudioWithWhisperCLI(audioFilePath, outputFilePath, language);
            
            if (!success) {
                System.err.println("Không thể xử lý file âm thanh - có thể do lỗi encoding hoặc lỗi định dạng file");
                return segments;
            }
            
            // Đọc kết quả từ file output
            if (!outputFile.exists()) {
                System.err.println("Không tìm thấy file kết quả tại: " + outputFile.getAbsolutePath());
                return segments;
            }
            
            // Kiểm tra kích thước file
            if (outputFile.length() == 0) {
                System.err.println("File kết quả có kích thước 0 byte");
                return segments;
            }
            
            // Phân tích kết quả và tạo các đoạn âm thanh
            segments = parseWhisperOutput(outputFile, fileId);
            
            // Lưu các phân đoạn vào database
            if (!segments.isEmpty()) {
                int savedCount = segmentDAO.saveSegments(segments);
                System.err.println("Đã lưu " + savedCount + " phân đoạn vào database");
            }
            
            // Xóa file kết quả tạm thời
            outputFile.delete();
            
            System.err.println("Đã xử lý thành công: " + segments.size() + " đoạn");
            
        } catch (Exception e) {
            System.err.println("Lỗi trong quá trình phiên âm: " + e.getMessage());
            e.printStackTrace();
        }
        
        return segments;
    }
    
    private boolean processAudioWithWhisperCLI(String audioFilePath, String outputFilePath) {
        return processAudioWithWhisperCLI(audioFilePath, outputFilePath, LANG_AUTO);
    }
    
    /**
     * Xử lý file âm thanh với Whisper CLI và ngôn ngữ cụ thể
     * @param audioFilePath Đường dẫn file âm thanh
     * @param outputFilePath Đường dẫn file output
     * @param language Ngôn ngữ cần phát hiện (auto, en, vi, ja)
     * @return true nếu xử lý thành công, false nếu có lỗi
     */
    private boolean processAudioWithWhisperCLI(String audioFilePath, String outputFilePath, String language) {
        return processAudioWithWhisperCLI(audioFilePath, outputFilePath, language, 0);
    }
    
    /**
     * Xử lý file âm thanh với Whisper CLI và ngôn ngữ cụ thể
     * @param audioFilePath Đường dẫn file âm thanh
     * @param outputFilePath Đường dẫn file output
     * @param language Ngôn ngữ cần phát hiện (auto, en, vi, ja)
     * @param segmentLengthSeconds Độ dài mỗi phân đoạn tính bằng giây (0 = tự động phân đoạn theo Whisper)
     * @return true nếu xử lý thành công, false nếu có lỗi
     */
    private boolean processAudioWithWhisperCLI(String audioFilePath, String outputFilePath, String language, int segmentLengthSeconds) {
        try {
            // Kiểm tra xem file có tồn tại không
            File audioFile = new File(audioFilePath);
            if (!audioFile.exists()) {
                System.err.println("File không tồn tại: " + audioFilePath);
                return false;
            }
            
            // Tạo file tạm nếu file gốc có ký tự đặc biệt
            File tempAudioFile = audioFile;
            boolean usingTempFile = false;
            
            if (audioFilePath.matches(".*[^\\x00-\\x7F].*")) {
                // File chứa ký tự Unicode (không phải ASCII), cần tạo symlink hoặc copy
                File tempFile = new File(TEMP_DIR, "temp_audio_" + UUID.randomUUID().toString() + getFileExtension(audioFilePath));
                try {
                    // Copy file để đảm bảo tên file không có ký tự đặc biệt
                    java.nio.file.Files.copy(audioFile.toPath(), tempFile.toPath(), 
                                         java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    tempAudioFile = tempFile;
                    usingTempFile = true;
                    System.err.println("Đã tạo file tạm: " + tempFile.getAbsolutePath());
                } catch (Exception e) {
                    System.err.println("Lỗi khi tạo file tạm: " + e.getMessage());
                    return false;
                }
            }
            
            // Xây dựng command line
            List<String> command = new ArrayList<>();
            command.add(WHISPER_CLI_PATH);
            
            // Chọn mô hình phù hợp với ngôn ngữ
            String modelToUse = MODEL_PATH;
            if (language.equals(LANG_ENGLISH)) {
                // Sử dụng mô hình tiếng Anh cho tiếng Anh
                modelToUse = MODEL_PATH_EN;
                System.err.println("Sử dụng mô hình tiếng Anh: " + modelToUse);
            } else if (language.equals(LANG_VIETNAMESE) || language.equals(LANG_JAPANESE) || language.equals(LANG_AUTO)) {
                // Sử dụng mô hình đa ngôn ngữ cho tiếng Việt, tiếng Nhật hoặc tự động phát hiện
                modelToUse = MODEL_PATH_MULTILINGUAL;
                System.err.println("Sử dụng mô hình đa ngôn ngữ (base-q8_0): " + modelToUse);
            }
            
            // Cấu hình Whisper
            command.add("-m"); // Model file
            command.add(modelToUse);
            
            command.add("-f"); // Input file
            command.add(tempAudioFile.getAbsolutePath()); // Sử dụng đường dẫn tuyệt đối của file tạm
            
            command.add("-otxt"); // Output text file format
            
            // Xử lý tham số output file - cần loại bỏ đuôi .txt nếu có
            String baseOutputPath = outputFilePath;
            if (baseOutputPath.toLowerCase().endsWith(".txt")) {
                baseOutputPath = baseOutputPath.substring(0, baseOutputPath.length() - 4);
            }
            
            command.add("-of"); // Output file prefix
            command.add(baseOutputPath);
            
            command.add("-l"); // Language
            
            // Nếu là auto nhưng đang xử lý file tiếng Việt, hãy chỉ định rõ là tiếng Việt
            if (language.equals(LANG_AUTO)) {
                System.err.println("Đang sử dụng tự động phát hiện ngôn ngữ, nhưng khuyến nghị chỉ định rõ ngôn ngữ");
                command.add(language);
            } else {
                command.add(language);
            }
            
            // In ra ngôn ngữ đang dùng
            System.err.println("Phát hiện ngôn ngữ: " + (language.equals(LANG_AUTO) ? "Tự động" : 
                               language.equals(LANG_ENGLISH) ? "Tiếng Anh" : 
                               language.equals(LANG_VIETNAMESE) ? "Tiếng Việt" : 
                               language.equals(LANG_JAPANESE) ? "Tiếng Nhật" : language));
            
            // Tùy chọn xuất từng phân đoạn riêng biệt
            command.add("-osrt"); // Output SRT format (timestamp + text)
                        
            // Tùy chọn sử dụng số lượng threads ít để tăng độ ổn định
            command.add("-t"); // Threads
            command.add("1");
            
            // Tùy chọn chia nhỏ văn bản
            command.add("-ml"); // Max segment length in tokens
            command.add("1");
            
            // Thêm tùy chọn đặc biệt cho tiếng Việt
            if (language.equals(LANG_VIETNAMESE)) {
                // Thêm các tham số tối ưu cho tiếng Việt
                command.add("--no-timestamps"); // Tắt timestamps để tập trung vào độ chính xác của văn bản
                command.add("--language"); // Chỉ định ngôn ngữ rõ ràng
                command.add("vi");
            }
            
            // Disable GPU
            command.add("-ng"); // No GPU
            
            // Chúng ta không còn sử dụng tham số -d (duration) nữa
            // vì chúng ta sẽ xử lý việc nhóm segment sau khi phân tích
            
            // In ra lệnh đang thực thi
            StringBuilder cmdString = new StringBuilder();
            for (String arg : command) {
                cmdString.append(arg).append(" ");
            }
            System.err.println("Thực thi lệnh: " + cmdString.toString());
            
            // Thực thi lệnh
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Đọc và in ra output với UTF-8
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println("[Whisper] " + line);
                }
            }
            
            // Đợi cho process kết thúc (tối đa 10 phút)
            boolean completed = process.waitFor(10, TimeUnit.MINUTES);
            if (!completed) {
                System.err.println("Quá thời gian xử lý, đang hủy...");
                process.destroyForcibly();
                
                // Xóa file tạm nếu đã tạo
                if (usingTempFile && tempAudioFile.exists()) {
                    tempAudioFile.delete();
                    System.err.println("Đã xóa file tạm: " + tempAudioFile.getAbsolutePath());
                }
                
                return false;
            }
            
            int exitCode = process.exitValue();
            System.err.println("Whisper CLI kết thúc với mã: " + exitCode);
            
            try {
                // Kiểm tra file output đã được tạo chưa
                // Whisper có thể tạo ra nhiều loại file output khác nhau (.txt, .srt, .vtt)
                File expectedOutput = new File(baseOutputPath + ".srt");
                if (exitCode == 0 && expectedOutput.exists()) {
                    // Kiểm tra kích thước file có hợp lệ không
                    if (expectedOutput.length() > 0) {
                        try {
                            // Đọc file SRT và chuyển đổi thành định dạng text
                            convertSrtToText(expectedOutput, new File(outputFilePath));
                            
                            // Kiểm tra file output đã được tạo và có nội dung
                            File outputTextFile = new File(outputFilePath);
                            if (outputTextFile.exists() && outputTextFile.length() > 0) {
                                // Xóa file tạm nếu đã tạo
                                if (usingTempFile && tempAudioFile.exists()) {
                                    tempAudioFile.delete();
                                    System.err.println("Đã xóa file tạm: " + tempAudioFile.getAbsolutePath());
                                }
                                
                                return true;
                            } else {
                                System.err.println("File output không có nội dung hợp lệ sau khi chuyển đổi");
                                // Hiển thị nội dung file SRT gốc để debug
                                try {
                                    byte[] content = Files.readAllBytes(expectedOutput.toPath());
                                    System.err.println("Nội dung file SRT gốc (hex): " + bytesToHex(content, 100));
                                } catch (Exception e) {
                                    System.err.println("Không thể đọc nội dung file SRT gốc: " + e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Lỗi khi chuyển đổi file SRT: " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        System.err.println("File SRT được tạo nhưng có kích thước 0 byte");
                    }
                }
                
                // Thử tìm file .txt nếu không có .srt
                expectedOutput = new File(baseOutputPath + ".txt");
                if (exitCode == 0 && expectedOutput.exists()) {
                    // Kiểm tra kích thước file có hợp lệ không
                    if (expectedOutput.length() > 0) {
                        try {
                            System.err.println("Đang đọc file TXT từ: " + expectedOutput.getAbsolutePath());
                            
                            // In một số thông tin về file
                            System.err.println("Kích thước file TXT: " + expectedOutput.length() + " bytes");
                            
                            // Đọc toàn bộ file dưới dạng mảng byte
                            byte[] fileContent = Files.readAllBytes(expectedOutput.toPath());
                            
                            // In 100 byte đầu tiên để debug
                            System.err.println("100 byte đầu tiên của file TXT: " + bytesToHex(fileContent, 100));
                            
                            // Tạo chuỗi từ mảng byte với ISO-8859-1 (không làm hỏng byte nào)
                            String contentStr = new String(fileContent, java.nio.charset.StandardCharsets.ISO_8859_1);
                            
                            // Tách dòng theo cả Windows và Unix line endings
                            String[] linesArray = contentStr.split("\\r?\\n");
                            List<String> textLines = Arrays.asList(linesArray);
                            
                            // Ghi ra file với UTF-8
                            Files.write(new File(outputFilePath).toPath(), textLines, java.nio.charset.StandardCharsets.UTF_8);
                            expectedOutput.delete(); // Xóa file tạm sau khi sao chép
                            
                            // Xóa file tạm nếu đã tạo
                            if (usingTempFile && tempAudioFile.exists()) {
                                tempAudioFile.delete();
                                System.err.println("Đã xóa file tạm: " + tempAudioFile.getAbsolutePath());
                            }
                            
                            return true;
                        } catch (Exception e) {
                            System.err.println("Lỗi khi xử lý file TXT: " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        System.err.println("File TXT được tạo nhưng có kích thước 0 byte");
                    }
                }
            } finally {
                // Đảm bảo xóa file tạm dù có lỗi hay không
                if (usingTempFile && tempAudioFile.exists()) {
                    tempAudioFile.delete();
                    System.err.println("Đã xóa file tạm: " + tempAudioFile.getAbsolutePath());
                }
            }
            
            return exitCode == 0;
            
        } catch (Exception e) {
            System.err.println("Lỗi khi xử lý với Whisper CLI: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Chuyển đổi file SRT sang định dạng text phù hợp cho phân tích
     */
    private void convertSrtToText(File srtFile, File textFile) throws IOException {
        System.err.println("Đang đọc file SRT từ: " + srtFile.getAbsolutePath());
        
        // In một số thông tin về file
        System.err.println("Kích thước file SRT: " + srtFile.length() + " bytes");
        
                    // Đọc toàn bộ file dưới dạng mảng byte
            byte[] fileContent = Files.readAllBytes(srtFile.toPath());
            
            // In 100 byte đầu tiên để debug
            System.err.println("100 byte đầu tiên của file SRT: " + bytesToHex(fileContent, 100));
            
            // Thử đọc với UTF-8 trước
            String contentStr;
            try {
                contentStr = new String(fileContent, java.nio.charset.StandardCharsets.UTF_8);
                System.err.println("Đọc file SRT với encoding UTF-8");
            } catch (Exception e) {
                // Nếu có lỗi, quay lại dùng ISO-8859-1
                contentStr = new String(fileContent, java.nio.charset.StandardCharsets.ISO_8859_1);
                System.err.println("Đọc file SRT với encoding ISO-8859-1");
            }
        
        // Tách dòng theo cả Windows và Unix line endings
        String[] linesArray = contentStr.split("\\r?\\n");
        List<String> lines = Arrays.asList(linesArray);
        
        List<String> outputLines = new ArrayList<>();
        
        System.err.println("Đọc file SRT với " + lines.size() + " dòng");
        
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i).trim();
            
            // Kiểm tra xem đây có phải là số thứ tự không (1, 2, 3, ...)
            if (line.matches("\\d+")) {
                int segmentNumber = Integer.parseInt(line);
                i++; // Bỏ qua số thứ tự
                
                // Dòng tiếp theo chứa timestamp (00:00:01,000 --> 00:00:02,000)
                if (i < lines.size()) {
                    String timestamp = lines.get(i).trim();
                    i++;
                    
                    System.err.println("Xử lý segment #" + segmentNumber + " với timestamp: " + timestamp);
                    
                    // Kiểm tra định dạng timestamp
                    if (timestamp.contains(" --> ")) {
                        // Cần chuyển dấu phẩy thành dấu chấm cho phù hợp với định dạng phân tích
                        timestamp = timestamp.replace(',', '.');
                        
                        // Dòng tiếp theo chứa text, có thể có nhiều dòng
                        StringBuilder text = new StringBuilder();
                        while (i < lines.size() && !lines.get(i).trim().isEmpty()) {
                            text.append(lines.get(i).trim()).append(" ");
                            i++;
                        }
                        
                        // Thêm dòng với format phù hợp cho parseWhisperOutput
                        if (text.length() > 0) {
                            String formattedLine = "[Whisper] [" + timestamp + "] " + text.toString().trim();
                            outputLines.add(formattedLine);
                            System.err.println("Đã chuyển đổi thành: " + formattedLine);
                        }
                    } else {
                        // Không phải định dạng timestamp hợp lệ, bỏ qua
                        System.err.println("Bỏ qua dòng không phải timestamp: " + timestamp);
                    }
                }
            } else {
                // Bỏ qua dòng không khớp với định dạng SRT
                i++;
            }
        }
        
                    // Ghi ra file text với UTF-8
            System.err.println("Ghi " + outputLines.size() + " dòng vào file text");
            
            // Đảm bảo ghi với UTF-8 và thêm BOM để các ứng dụng Windows nhận diện đúng
            try {
                java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(textFile), java.nio.charset.StandardCharsets.UTF_8);
                
                // Ghi từng dòng
                for (String line : outputLines) {
                    writer.write(line + "\n");
                }
                
                writer.close();
                System.err.println("Đã ghi file text với UTF-8 encoding");
            } catch (Exception e) {
                System.err.println("Lỗi khi ghi file: " + e.getMessage());
                // Fallback to basic method
                Files.write(textFile.toPath(), outputLines, java.nio.charset.StandardCharsets.UTF_8);
            }
    }
    
    private List<AudioSegment> parseWhisperOutput(File outputFile, int fileId) {
        List<AudioSegment> segments = new ArrayList<>();
        
        try {
            System.err.println("Đang đọc file output từ: " + outputFile.getAbsolutePath());
            
            // In một số thông tin về file
            System.err.println("Kích thước file output: " + outputFile.length() + " bytes");
            
            // Đọc toàn bộ file dưới dạng mảng byte
            byte[] fileContent = Files.readAllBytes(outputFile.toPath());
            
            // In 100 byte đầu tiên để debug
            System.err.println("100 byte đầu tiên của file output: " + bytesToHex(fileContent, 100));
            
            // Thử đọc với UTF-8 trước
            String contentStr;
            try {
                contentStr = new String(fileContent, java.nio.charset.StandardCharsets.UTF_8);
                System.err.println("Đọc file output với encoding UTF-8");
            } catch (Exception e) {
                // Nếu có lỗi, quay lại dùng ISO-8859-1
                contentStr = new String(fileContent, java.nio.charset.StandardCharsets.ISO_8859_1);
                System.err.println("Đọc file output với encoding ISO-8859-1");
            }
            
            // Tách dòng theo cả Windows và Unix line endings
            String[] linesArray = contentStr.split("\\r?\\n");
            List<String> lines = Arrays.asList(linesArray);
            
            System.err.println("Đọc file output với " + lines.size() + " dòng");
            
            // Định dạng sau khi chuyển đổi từ SRT:
            // [Whisper] [00:00:20.000 --> 00:00:22.000] text
            // Sử dụng biểu thức chính quy linh hoạt hơn để bắt nhiều định dạng timestamp
            Pattern pattern = Pattern.compile("\\[Whisper\\]\\s*\\[(\\d{1,2}):(\\d{1,2}):(\\d{1,2})\\.(\\d{1,3})\\s*-->\\s*(\\d{1,2}):(\\d{1,2}):(\\d{1,2})\\.(\\d{1,3})\\]\\s*(.*)");
            
            // In ra một vài dòng đầu để xem định dạng thực tế
            int debugLineCount = Math.min(10, lines.size());
            System.err.println("--- Debug: " + debugLineCount + " dòng đầu tiên ---");
            for (int i = 0; i < debugLineCount; i++) {
                System.err.println("[Line " + i + "]: " + lines.get(i));
            }
            System.err.println("--- End Debug ---");
            
            // Danh sách tạm thời để lưu các segment nhỏ từ Whisper
            List<AudioSegment> rawSegments = new ArrayList<>();
            
            for (String line : lines) {
                System.err.println("Phân tích dòng: " + line);
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    try {
                        // Parse start time
                        int startHours = Integer.parseInt(matcher.group(1));
                        int startMinutes = Integer.parseInt(matcher.group(2));
                        int startSeconds = Integer.parseInt(matcher.group(3));
                        int startMillis = Integer.parseInt(matcher.group(4));
                        
                        // Parse end time
                        int endHours = Integer.parseInt(matcher.group(5));
                        int endMinutes = Integer.parseInt(matcher.group(6));
                        int endSeconds = Integer.parseInt(matcher.group(7));
                        int endMillis = Integer.parseInt(matcher.group(8));
                        
                        // Chuyển đổi sang milliseconds
                        int startTimeMs = ((startHours * 60 + startMinutes) * 60 + startSeconds) * 1000 + startMillis;
                        int endTimeMs = ((endHours * 60 + endMinutes) * 60 + endSeconds) * 1000 + endMillis;
                        
                        // Trích xuất text
                        String text = matcher.group(9).trim();
                        
                        if (!text.isEmpty()) {
                            AudioSegment segment = new AudioSegment();
                            segment.setFileId(fileId);
                            segment.setStartTime(startTimeMs);
                            segment.setEndTime(endTimeMs);
                            segment.setText(text);
                            rawSegments.add(segment);
                            
                            System.err.println("Đã tạo segment: [" + segment.getFormattedStartTime() + "-" + 
                                              segment.getFormattedEndTime() + "]: '" + text + "' (" + 
                                              startTimeMs + "ms-" + endTimeMs + "ms)");
                        }
                    } catch (Exception e) {
                        System.err.println("Lỗi khi phân tích dòng: " + line);
                        e.printStackTrace();
                    }
                }
            }
            
            // Nếu không tìm thấy mẫu phù hợp nào, hiển thị cảnh báo
            if (rawSegments.isEmpty()) {
                System.err.println("Không tìm thấy định dạng timestamp phù hợp trong file output.");
                
                // Thử tìm text bằng cách lọc các dòng bắt đầu bằng [Whisper]
                List<String> filteredLines = new ArrayList<>();
                for (String line : lines) {
                    if (line.contains("[Whisper]")) {
                        // Trích xuất phần text sau [Whisper]
                        String text = line.substring(line.indexOf("[Whisper]") + 9).trim();
                        if (!text.isEmpty()) {
                            filteredLines.add(text);
                        }
                    }
                }
                
                // Nếu tìm được các dòng có ý nghĩa
                if (!filteredLines.isEmpty()) {
                    String fullText = String.join(" ", filteredLines);
                    System.err.println("Đã tìm thấy " + filteredLines.size() + " dòng có chứa text");
                    
                    // Tạo segment với toàn bộ text tìm được
                    AudioSegment segment = new AudioSegment();
                    segment.setFileId(fileId);
                    segment.setStartTime(0);
                    segment.setEndTime(0);
                    segment.setText(fullText.trim());
                    segments.add(segment);
                    
                    System.err.println("Xử lý " + filteredLines.size() + " dòng như một segment duy nhất");
                } else {
                    // Chuyển đổi toàn bộ nội dung thành một segment để không làm mất nội dung
                    String fullText = String.join(" ", lines);
                    if (!fullText.trim().isEmpty()) {
                        AudioSegment segment = new AudioSegment();
                        segment.setFileId(fileId);
                        segment.setStartTime(0);
                        segment.setEndTime(0);
                        segment.setText(fullText.trim());
                        segments.add(segment);
                        
                        System.err.println("Xử lý toàn bộ nội dung như một segment duy nhất: " + fullText.trim());
                    }
                }
                
                return segments;
            }
            
            System.err.println("Tổng số raw segments đã phân tích: " + rawSegments.size());
            
            // Kiểm tra xem có cần nhóm các segment lại không
            // Nhóm các segment lại theo segmentLengthSeconds
            int segmentLengthMs = getActiveSegmentLengthMs();
            if (segmentLengthMs > 0 && rawSegments.size() > 1) {
                System.err.println("Thực hiện nhóm segment với độ dài " + segmentLengthMs + " ms");
                segments = groupSegments(rawSegments, segmentLengthMs, fileId);
                System.err.println("Đã nhóm thành " + segments.size() + " segment lớn hơn");
            } else {
                segments = rawSegments;
            }
            
            System.err.println("Tổng số segments cuối cùng: " + segments.size());
            
        } catch (Exception e) {
            System.err.println("Lỗi khi phân tích file output: " + e.getMessage());
            e.printStackTrace();
        }
        
        return segments;
    }
    
    // Biến để lưu độ dài segment hiện tại đang được sử dụng
    private int activeSegmentLengthMs = 0;
    
    // Thiết lập độ dài segment đang sử dụng (milliseconds)
    public void setActiveSegmentLengthMs(int milliseconds) {
        this.activeSegmentLengthMs = milliseconds;
    }
    
    // Lấy độ dài segment đang sử dụng (milliseconds)
    public int getActiveSegmentLengthMs() {
        return this.activeSegmentLengthMs;
    }
    
    /**
     * Nhóm các segment nhỏ thành các segment lớn hơn theo thời lượng chỉ định
     * @param rawSegments Danh sách các segment nhỏ
     * @param segmentLengthMs Độ dài mong muốn của mỗi segment (milliseconds)
     * @param fileId ID của file âm thanh
     * @return Danh sách các segment đã được nhóm
     */
    private List<AudioSegment> groupSegments(List<AudioSegment> rawSegments, int segmentLengthMs, int fileId) {
        List<AudioSegment> groupedSegments = new ArrayList<>();
        
        if (rawSegments.isEmpty()) {
            return groupedSegments;
        }
        
        // Sắp xếp các segment theo thời gian bắt đầu
        rawSegments.sort(Comparator.comparingInt(AudioSegment::getStartTime));
        
        // Lấy thời gian bắt đầu của segment đầu tiên
        int currentStartTime = rawSegments.get(0).getStartTime();
        int nextEndTime = currentStartTime + segmentLengthMs;
        
        StringBuilder textBuilder = new StringBuilder();
        AudioSegment currentSegment = null;
        
        for (AudioSegment segment : rawSegments) {
            // Nếu segment hiện tại nằm trong khoảng thời gian cho phép
            if (segment.getStartTime() < nextEndTime) {
                // Thêm text vào segment hiện tại
                if (textBuilder.length() > 0) {
                    textBuilder.append(" ");
                }
                textBuilder.append(segment.getText());
                
                // Cập nhật segment hiện tại
                if (currentSegment == null) {
                    currentSegment = new AudioSegment();
                    currentSegment.setFileId(fileId);
                    currentSegment.setStartTime(currentStartTime);
                }
                
                // Cập nhật thời gian kết thúc
                currentSegment.setEndTime(segment.getEndTime());
            } else {
                // Tạo segment mới và thêm vào danh sách
                if (currentSegment != null) {
                    currentSegment.setText(textBuilder.toString().trim());
                    groupedSegments.add(currentSegment);
                    
                    System.err.println("Đã tạo grouped segment: [" + currentSegment.getFormattedStartTime() + "-" + 
                                      currentSegment.getFormattedEndTime() + "]: '" + currentSegment.getText() + "'");
                    
                    // Reset để tạo segment mới
                    textBuilder = new StringBuilder(segment.getText());
                    currentStartTime = segment.getStartTime();
                    nextEndTime = currentStartTime + segmentLengthMs;
                    
                    currentSegment = new AudioSegment();
                    currentSegment.setFileId(fileId);
                    currentSegment.setStartTime(currentStartTime);
                    currentSegment.setEndTime(segment.getEndTime());
                }
            }
        }
        
        // Thêm segment cuối cùng nếu có
        if (currentSegment != null) {
            currentSegment.setText(textBuilder.toString().trim());
            groupedSegments.add(currentSegment);
            
            System.err.println("Đã tạo grouped segment: [" + currentSegment.getFormattedStartTime() + "-" + 
                              currentSegment.getFormattedEndTime() + "]: '" + currentSegment.getText() + "'");
        }
        
        return groupedSegments;
    }
    
    // Kiểm tra định dạng file âm thanh có được hỗ trợ không
    private boolean isValidAudioFormat(String filePath) {
        String lowerPath = filePath.toLowerCase();
        
        // Danh sách các định dạng được Whisper hỗ trợ
        String[] supportedFormats = {".wav", ".mp3", ".flac", ".ogg", ".m4a"};
        
        for (String format : supportedFormats) {
            if (lowerPath.endsWith(format)) {
                return true;
            }
        }
        
        // Nếu không phải định dạng được hỗ trợ
        return false;
    }
    
    /**
     * Lấy phần mở rộng của tệp tin, bao gồm dấu chấm
     * @param filePath Đường dẫn tệp tin
     * @return Phần mở rộng của tệp tin (ví dụ: .mp3) hoặc chuỗi rỗng nếu không có
     */
    private String getFileExtension(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == filePath.length() - 1) {
            return "";
        }
        
        return filePath.substring(lastDotIndex);
    }
    
    /**
     * Chuyển đổi mảng byte thành chuỗi hex để debug
     * @param bytes Mảng byte cần chuyển đổi
     * @param maxBytes Số byte tối đa để hiển thị
     * @return Chuỗi hex
     */
    private String bytesToHex(byte[] bytes, int maxBytes) {
        if (bytes == null) {
            return "null";
        }
        
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(bytes.length, maxBytes);
        
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("%02X ", bytes[i]));
            
            // Thêm xuống dòng sau mỗi 16 byte
            if ((i + 1) % 16 == 0) {
                sb.append("\n");
            }
        }
        
        if (bytes.length > maxBytes) {
            sb.append("... (").append(bytes.length - maxBytes).append(" bytes more)");
        }
        
        return sb.toString();
    }
    
    // Hướng dẫn cài đặt whisper
    public static String getInstallationGuide() {
        return "# Hướng dẫn cài đặt Whisper\n\n" +
               "1. Tải Whisper CLI từ GitHub: https://github.com/ggerganov/whisper.cpp/releases\n" +
               "2. Giải nén và sao chép file whisper.exe vào thư mục lib/\n" +
               "3. Tải model file từ: https://huggingface.co/ggerganov/whisper.cpp/tree/main\n" +
               "   - ggml-tiny.en.bin (cho tiếng Anh)\n" +
               "   - ggml-base-q8_0.bin (cho đa ngôn ngữ, bao gồm tiếng Việt và tiếng Nhật)\n" +
               "4. Đặt model file vào thư mục models/\n\n" +
               "Lưu ý: Đảm bảo đã cài đặt Microsoft Visual C++ Redistributable\n";
    }
    
    // Kiểm tra xem whisper đã được cài đặt đúng chưa
    /**
     * Kiểm tra xem Whisper đã được cài đặt đúng chưa
     * @return true nếu đã cài đặt đúng, false nếu chưa
     */
    public static boolean isWhisperInstalled() {
        File whisperExe = new File(WHISPER_CLI_PATH);
        File modelFile = new File(MODEL_PATH);
        
        boolean whisperExists = whisperExe.exists();
        boolean modelExists = modelFile.exists();
        
        System.err.println("Kiểm tra cài đặt Whisper:");
        System.err.println("- Whisper CLI tồn tại: " + whisperExists);
        System.err.println("- Model tồn tại: " + modelExists);
        
        return whisperExists && modelExists;
    }
    
    /**
     * Lấy danh sách các ngôn ngữ được hỗ trợ
     * @return Map chứa mã ngôn ngữ và tên hiển thị
     */
    public static Map<String, String> getSupportedLanguages() {
        Map<String, String> languages = new LinkedHashMap<>(); // LinkedHashMap để giữ thứ tự
        
        languages.put(LANG_AUTO, "Tự động phát hiện");
        languages.put(LANG_ENGLISH, "Tiếng Anh");
        languages.put(LANG_VIETNAMESE, "Tiếng Việt");
        languages.put(LANG_JAPANESE, "Tiếng Nhật");
        
        return languages;
    }
    
    /**
     * Lấy tên hiển thị của ngôn ngữ theo mã
     * @param langCode Mã ngôn ngữ
     * @return Tên hiển thị của ngôn ngữ
     */
    public static String getLanguageDisplayName(String langCode) {
        Map<String, String> languages = getSupportedLanguages();
        return languages.getOrDefault(langCode, langCode);
    }
    
    /**
     * Lấy danh sách các phân đoạn từ database
     * @param fileId ID của file âm thanh
     * @return Danh sách các phân đoạn
     */
    public List<AudioSegment> getSegments(int fileId) {
        return segmentDAO.getSegmentsByFileId(fileId);
    }
    
    /**
     * Cập nhật một phân đoạn
     * @param segment Phân đoạn cần cập nhật
     * @return true nếu cập nhật thành công, false nếu có lỗi
     */
    public boolean updateSegment(AudioSegment segment) {
        return segmentDAO.updateSegment(segment);
    }
    
    /**
     * Cập nhật nội dung văn bản của một phân đoạn
     * @param segmentId ID của phân đoạn
     * @param newText Nội dung văn bản mới
     * @return true nếu cập nhật thành công, false nếu có lỗi
     */
    public boolean updateSegmentText(int segmentId, String newText) {
        AudioSegment segment = segmentDAO.getSegmentById(segmentId);
        if (segment != null) {
            segment.setText(newText);
            return segmentDAO.updateSegment(segment);
        }
        return false;
    }
} 