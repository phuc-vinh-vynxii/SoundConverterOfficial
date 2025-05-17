package com.soundconverter.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.soundconverter.models.AudioFile;
import com.soundconverter.models.AudioSegment;

public class AudioFileDAO {
    private final Connection connection;

    public AudioFileDAO() {
        connection = DatabaseConnection.getInstance().getConnection();
    }

    public int addAudioFile(AudioFile audioFile) {
        String sql = "INSERT INTO audio_files (file_name, file_path) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, audioFile.getFileName());
            pstmt.setString(2, audioFile.getFilePath());
            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    audioFile.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error adding audio file: " + e.getMessage());
        }
        return -1;
    }

    public boolean updateAudioFile(AudioFile audioFile) {
        String sql = "UPDATE audio_files SET file_name = ?, file_path = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, audioFile.getFileName());
            pstmt.setString(2, audioFile.getFilePath());
            pstmt.setInt(3, audioFile.getId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating audio file: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteAudioFile(int id) {
        String sql = "DELETE FROM audio_files WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting audio file: " + e.getMessage());
            return false;
        }
    }

    public AudioFile getAudioFileById(int id) {
        String sql = "SELECT * FROM audio_files WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    AudioFile audioFile = new AudioFile(
                            rs.getInt("id"),
                            rs.getString("file_name"),
                            rs.getString("file_path")
                    );
                    // Load segments
                    loadSegments(audioFile);
                    return audioFile;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting audio file: " + e.getMessage());
        }
        return null;
    }

    public List<AudioFile> getAllAudioFiles() {
        List<AudioFile> audioFiles = new ArrayList<>();
        String sql = "SELECT * FROM audio_files";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                AudioFile audioFile = new AudioFile(
                        rs.getInt("id"),
                        rs.getString("file_name"),
                        rs.getString("file_path")
                );
                // Optional: Load segments for each file
                // loadSegments(audioFile);
                audioFiles.add(audioFile);
            }
        } catch (SQLException e) {
            System.err.println("Error getting all audio files: " + e.getMessage());
        }
        return audioFiles;
    }

    public void loadSegments(AudioFile audioFile) {
        String sql = "SELECT * FROM audio_segments WHERE file_id = ? ORDER BY start_time";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, audioFile.getId());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    AudioSegment segment = new AudioSegment(
                            rs.getInt("id"),
                            rs.getInt("file_id"),
                            rs.getInt("start_time"),
                            rs.getInt("end_time"),
                            rs.getString("text")
                    );
                    audioFile.addSegment(segment);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading segments: " + e.getMessage());
        }
    }

    public int addSegment(AudioSegment segment) {
        String sql = "INSERT INTO audio_segments (file_id, start_time, end_time, text) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, segment.getFileId());
            pstmt.setInt(2, segment.getStartTime());
            pstmt.setInt(3, segment.getEndTime());
            pstmt.setString(4, segment.getText());
            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    segment.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error adding segment: " + e.getMessage());
        }
        return -1;
    }

    public boolean updateSegment(AudioSegment segment) {
        String sql = "UPDATE audio_segments SET start_time = ?, end_time = ?, text = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, segment.getStartTime());
            pstmt.setInt(2, segment.getEndTime());
            pstmt.setString(3, segment.getText());
            pstmt.setInt(4, segment.getId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating segment: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteSegment(int id) {
        String sql = "DELETE FROM audio_segments WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting segment: " + e.getMessage());
            return false;
        }
    }

    public List<AudioSegment> getSegmentsByFileId(int fileId) {
        List<AudioSegment> segments = new ArrayList<>();
        String sql = "SELECT * FROM audio_segments WHERE file_id = ? ORDER BY start_time";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, fileId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    AudioSegment segment = new AudioSegment(
                            rs.getInt("id"),
                            rs.getInt("file_id"),
                            rs.getInt("start_time"),
                            rs.getInt("end_time"),
                            rs.getString("text")
                    );
                    segments.add(segment);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting segments by file ID: " + e.getMessage());
        }
        return segments;
    }
} 