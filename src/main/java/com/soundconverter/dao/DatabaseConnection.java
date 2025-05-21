package com.soundconverter.dao;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DatabaseConnection {
    private static final String CONFIG_FILE = "/config.properties";
    
    private static String DB_URL;
    private static String DB_USER;
    private static String DB_PASSWORD;

    private static DatabaseConnection instance;
    private Connection connection;

    private DatabaseConnection() {
        loadConfig();
        try {
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            initializeDatabase();
        } catch (SQLException e) {
            System.err.println("Lỗi kết nối cơ sở dữ liệu: " + e.getMessage());
        }
    }
    
    private void loadConfig() {
        Properties props = new Properties();
        try (InputStream input = getClass().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                System.err.println("Không thể tìm thấy file " + CONFIG_FILE);
                // Sử dụng giá trị mặc định nếu không tìm thấy file cấu hình
                DB_URL = "jdbc:mysql://localhost:3306/soundconverter?createDatabaseIfNotExist=true";
                DB_USER = "root";
                DB_PASSWORD = "";
                return;
            }
            
            // Đọc file cấu hình
            props.load(input);
            
            // Lấy thông tin kết nối từ file cấu hình
            DB_URL = props.getProperty("db.url");
            DB_USER = props.getProperty("db.user");
            DB_PASSWORD = props.getProperty("db.password");
            
        } catch (IOException ex) {
            System.err.println("Lỗi khi đọc file cấu hình: " + ex.getMessage());
            // Sử dụng giá trị mặc định nếu có lỗi
            DB_URL = "jdbc:mysql://localhost:3306/soundconverter?createDatabaseIfNotExist=true";
            DB_USER = "root";
            DB_PASSWORD = "";
        }
    }

    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }

    private void initializeDatabase() {
        try (Statement stmt = connection.createStatement()) {
            // Tạo các bảng nếu chúng chưa tồn tại
            String createAudioFilesTable = "CREATE TABLE IF NOT EXISTS audio_files (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "file_name VARCHAR(255) NOT NULL," +
                    "file_path VARCHAR(1024) NOT NULL" +
                    ")";
            
            String createAudioSegmentsTable = "CREATE TABLE IF NOT EXISTS audio_segments (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "file_id INT NOT NULL," +
                    "start_time INT NOT NULL," + // Lưu trữ mili giây
                    "end_time INT NOT NULL," +   // Lưu trữ mili giây
                    "text TEXT," +
                    "FOREIGN KEY (file_id) REFERENCES audio_files(id) ON DELETE CASCADE" +
                    ")";
            
            String createMergedAudioTable = "CREATE TABLE IF NOT EXISTS merged_audio (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "file_name VARCHAR(255) NOT NULL," +
                    "file_path VARCHAR(1024) NOT NULL" +
                    ")";
            
            String createMergedSegmentsTable = "CREATE TABLE IF NOT EXISTS merged_segments (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "merged_id INT NOT NULL," +
                    "source_file_id INT NOT NULL," +
                    "start_time INT NOT NULL," + // Lưu trữ mili giây
                    "end_time INT NOT NULL," +   // Lưu trữ mili giây
                    "sequence_order INT NOT NULL," +
                    "FOREIGN KEY (merged_id) REFERENCES merged_audio(id) ON DELETE CASCADE," +
                    "FOREIGN KEY (source_file_id) REFERENCES audio_files(id)" +
                    ")";
            
            stmt.executeUpdate(createAudioFilesTable);
            stmt.executeUpdate(createAudioSegmentsTable);
            stmt.executeUpdate(createMergedAudioTable);
            stmt.executeUpdate(createMergedSegmentsTable);
            
        } catch (SQLException e) {
            System.err.println("Lỗi khi khởi tạo cơ sở dữ liệu: " + e.getMessage());
        }
    }
    
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi đóng kết nối: " + e.getMessage());
        }
    }
} 