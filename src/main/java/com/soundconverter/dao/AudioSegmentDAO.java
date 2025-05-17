package com.soundconverter.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.soundconverter.models.AudioSegment;

public class AudioSegmentDAO {
    private Connection connection;
    
    public AudioSegmentDAO() {
        connection = DatabaseConnection.getInstance().getConnection();
    }
    
    /**
     * Lưu một phân đoạn âm thanh vào database
     * @param segment Đối tượng phân đoạn cần lưu
     * @return id của phân đoạn đã lưu, -1 nếu có lỗi
     */
    public int saveSegment(AudioSegment segment) {
        String sql = "INSERT INTO audio_segments (file_id, start_time, end_time, text) VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, segment.getFileId());
            stmt.setInt(2, segment.getStartTime());
            stmt.setInt(3, segment.getEndTime());
            stmt.setString(4, segment.getText());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                return -1;
            }
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    segment.setId(rs.getInt(1));
                    return segment.getId();
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Lỗi khi lưu phân đoạn: " + e.getMessage());
        }
        
        return -1;
    }
    
    /**
     * Lưu nhiều phân đoạn âm thanh cùng lúc
     * @param segments Danh sách các phân đoạn cần lưu
     * @return Số lượng phân đoạn đã lưu thành công
     */
    public int saveSegments(List<AudioSegment> segments) {
        int savedCount = 0;
        
        // Xóa các phân đoạn cũ của file này (nếu có)
        if (!segments.isEmpty()) {
            int fileId = segments.get(0).getFileId();
            deleteSegmentsByFileId(fileId);
        }
        
        // Lưu các phân đoạn mới
        for (AudioSegment segment : segments) {
            if (saveSegment(segment) > 0) {
                savedCount++;
            }
        }
        
        return savedCount;
    }
    
    /**
     * Cập nhật một phân đoạn âm thanh đã có trong database
     * @param segment Đối tượng phân đoạn cần cập nhật
     * @return true nếu cập nhật thành công, false nếu có lỗi
     */
    public boolean updateSegment(AudioSegment segment) {
        String sql = "UPDATE audio_segments SET start_time = ?, end_time = ?, text = ? WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, segment.getStartTime());
            stmt.setInt(2, segment.getEndTime());
            stmt.setString(3, segment.getText());
            stmt.setInt(4, segment.getId());
            
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
            
        } catch (SQLException e) {
            System.err.println("Lỗi khi cập nhật phân đoạn: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Xóa một phân đoạn âm thanh theo id
     * @param segmentId ID của phân đoạn cần xóa
     * @return true nếu xóa thành công, false nếu có lỗi
     */
    public boolean deleteSegment(int segmentId) {
        String sql = "DELETE FROM audio_segments WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, segmentId);
            
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
            
        } catch (SQLException e) {
            System.err.println("Lỗi khi xóa phân đoạn: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Xóa tất cả phân đoạn của một file âm thanh
     * @param fileId ID của file âm thanh
     * @return Số lượng phân đoạn đã xóa
     */
    public int deleteSegmentsByFileId(int fileId) {
        String sql = "DELETE FROM audio_segments WHERE file_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, fileId);
            
            return stmt.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("Lỗi khi xóa các phân đoạn của file: " + e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Lấy tất cả phân đoạn của một file âm thanh
     * @param fileId ID của file âm thanh
     * @return Danh sách các phân đoạn
     */
    public List<AudioSegment> getSegmentsByFileId(int fileId) {
        List<AudioSegment> segments = new ArrayList<>();
        String sql = "SELECT * FROM audio_segments WHERE file_id = ? ORDER BY start_time";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, fileId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    AudioSegment segment = new AudioSegment();
                    segment.setId(rs.getInt("id"));
                    segment.setFileId(rs.getInt("file_id"));
                    segment.setStartTime(rs.getInt("start_time"));
                    segment.setEndTime(rs.getInt("end_time"));
                    segment.setText(rs.getString("text"));
                    
                    segments.add(segment);
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy phân đoạn: " + e.getMessage());
        }
        
        return segments;
    }
    
    /**
     * Lấy một phân đoạn theo ID
     * @param segmentId ID của phân đoạn cần lấy
     * @return Đối tượng phân đoạn, null nếu không tìm thấy
     */
    public AudioSegment getSegmentById(int segmentId) {
        String sql = "SELECT * FROM audio_segments WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, segmentId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    AudioSegment segment = new AudioSegment();
                    segment.setId(rs.getInt("id"));
                    segment.setFileId(rs.getInt("file_id"));
                    segment.setStartTime(rs.getInt("start_time"));
                    segment.setEndTime(rs.getInt("end_time"));
                    segment.setText(rs.getString("text"));
                    
                    return segment;
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy phân đoạn theo ID: " + e.getMessage());
        }
        
        return null;
    }
} 