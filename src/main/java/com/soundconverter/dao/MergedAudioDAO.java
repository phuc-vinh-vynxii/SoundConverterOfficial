package com.soundconverter.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.soundconverter.models.MergedAudio;
import com.soundconverter.models.MergedAudio.MergeSegment;

public class MergedAudioDAO {
    private final Connection connection;

    public MergedAudioDAO() {
        connection = DatabaseConnection.getInstance().getConnection();
    }

    public int addMergedAudio(MergedAudio mergedAudio) {
        String sql = "INSERT INTO merged_audio (file_name, file_path) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, mergedAudio.getFileName());
            pstmt.setString(2, mergedAudio.getFilePath());
            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    mergedAudio.setId(id);
                    
                    // Thêm tất cả các phân đoạn
                    List<MergeSegment> segments = mergedAudio.getSegments();
                    for (int i = 0; i < segments.size(); i++) {
                        MergeSegment segment = segments.get(i);
                        addMergeSegment(id, segment, i);
                    }
                    return id;
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi thêm audio đã trộn: " + e.getMessage());
        }
        return -1;
    }

    private boolean addMergeSegment(int mergedId, MergeSegment segment, int order) {
        String sql = "INSERT INTO merged_segments (merged_id, source_file_id, start_time, end_time, sequence_order) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, mergedId);
            pstmt.setInt(2, segment.getSourceFileId());
            pstmt.setInt(3, segment.getStartTime());
            pstmt.setInt(4, segment.getEndTime());
            pstmt.setInt(5, order);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi thêm phân đoạn trộn: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteMergedAudio(int id) {
        String sql = "DELETE FROM merged_audio WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi xóa audio đã trộn: " + e.getMessage());
            return false;
        }
    }

    public MergedAudio getMergedAudioById(int id) {
        String sql = "SELECT * FROM merged_audio WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    MergedAudio mergedAudio = new MergedAudio(
                            rs.getInt("id"),
                            rs.getString("file_name"),
                            rs.getString("file_path")
                    );
                    // Tải các phân đoạn
                    loadMergeSegments(mergedAudio);
                    return mergedAudio;
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy audio đã trộn: " + e.getMessage());
        }
        return null;
    }

    public List<MergedAudio> getAllMergedAudio() {
        List<MergedAudio> mergedAudios = new ArrayList<>();
        String sql = "SELECT * FROM merged_audio";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                MergedAudio mergedAudio = new MergedAudio(
                        rs.getInt("id"),
                        rs.getString("file_name"),
                        rs.getString("file_path")
                );
                // Tùy chọn: Tải các phân đoạn cho mỗi file
                // loadMergeSegments(mergedAudio);
                mergedAudios.add(mergedAudio);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy tất cả audio đã trộn: " + e.getMessage());
        }
        return mergedAudios;
    }

    public void loadMergeSegments(MergedAudio mergedAudio) {
        String sql = "SELECT * FROM merged_segments WHERE merged_id = ? ORDER BY sequence_order";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, mergedAudio.getId());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    MergeSegment segment = new MergeSegment(
                            rs.getInt("source_file_id"),
                            rs.getInt("start_time"),
                            rs.getInt("end_time")
                    );
                    mergedAudio.addSegment(segment);
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi tải các phân đoạn trộn: " + e.getMessage());
        }
    }
} 