package com.soundconverter.controllers;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import com.soundconverter.ai.WhisperService;
import com.soundconverter.dao.AudioFileDAO;
import com.soundconverter.dao.MergedAudioDAO;
import com.soundconverter.models.AudioFile;
import com.soundconverter.models.AudioSegment;
import com.soundconverter.models.MergedAudio;
import com.soundconverter.models.MergedAudio.MergeSegment;
import com.soundconverter.services.AudioProcessingService;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class MainController implements Initializable {

    @FXML private ListView<AudioFile> audioFilesList;
    @FXML private TableView<AudioSegment> segmentsTable;
    @FXML private TableColumn<AudioSegment, String> startTimeColumn;
    @FXML private TableColumn<AudioSegment, String> endTimeColumn;
    @FXML private TableColumn<AudioSegment, String> textColumn;
    
    @FXML private TableView<MergeItem> mergeSegmentsTable;
    @FXML private TableColumn<MergeItem, String> mergeFileColumn;
    @FXML private TableColumn<MergeItem, String> mergeStartColumn;
    @FXML private TableColumn<MergeItem, String> mergeEndColumn;
    @FXML private TableColumn<MergeItem, String> mergeTextColumn;
    
    @FXML private TextField mergeFilenameField;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    
    @FXML private ComboBox<String> languageComboBox;
    @FXML private TextField segmentLengthField;
    
    private final AudioFileDAO audioFileDAO = new AudioFileDAO();
    private final MergedAudioDAO mergedAudioDAO = new MergedAudioDAO();
    private final AudioProcessingService audioProcessingService = AudioProcessingService.getInstance();
    
    private MergedAudio currentMerge;
    private final ObservableList<MergeItem> mergeItems = FXCollections.observableArrayList();
    
    // Output directory for temporary and final files
    private static final String OUTPUT_DIR = "./output/";
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize audio files list
        refreshAudioFilesList();
        
        // Thiết lập cho phép chỉnh sửa trên TableView
        segmentsTable.setEditable(true);
        
        // Initialize segments table
        startTimeColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getFormattedStartTime()));
        endTimeColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getFormattedEndTime()));
        textColumn.setCellValueFactory(new PropertyValueFactory<>("text"));
        
        // Thiết lập editable cho cột text
        textColumn.setEditable(true);
        
        // Thiết lập chỉnh sửa cell cho cột text
        textColumn.setCellFactory(tc -> new javafx.scene.control.TableCell<AudioSegment, String>() {
            private final javafx.scene.control.TextField textField = new javafx.scene.control.TextField();
            
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setGraphic(null);
                }
            }
            
            @Override
            public void startEdit() {
                super.startEdit();
                textField.setText(getText());
                setText(null);
                setGraphic(textField);
                textField.requestFocus();
                
                textField.setOnAction(e -> {
                    commitEdit(textField.getText());
                });
                
                textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                    if (!isNowFocused) {
                        commitEdit(textField.getText());
                    }
                });
            }
            
            @Override
            public void commitEdit(String newValue) {
                super.commitEdit(newValue);
                
                AudioSegment segment = (AudioSegment) getTableRow().getItem();
                if (segment != null) {
                    WhisperService whisperService = WhisperService.getInstance();
                    if (whisperService.updateSegmentText(segment.getId(), newValue)) {
                        updateStatus("Updated segment text");
                    } else {
                        showError("Failed to update segment");
                    }
                }
            }
            
            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }
        });
        
        // Setup audio file selection listener
        audioFilesList.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> loadAudioSegments(newValue));
        
        // Setup merge segments table
        mergeFileColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        mergeStartColumn.setCellValueFactory(new PropertyValueFactory<>("startTime"));
        mergeEndColumn.setCellValueFactory(new PropertyValueFactory<>("endTime"));
        mergeTextColumn.setCellValueFactory(new PropertyValueFactory<>("text"));
        
        mergeSegmentsTable.setItems(mergeItems);
        
        // Initialize a new merge
        handleNewMerge();
        
        // Initialize language combo box
        initLanguageComboBox();
    }
    
    private void refreshAudioFilesList() {
        List<AudioFile> files = audioFileDAO.getAllAudioFiles();
        audioFilesList.setItems(FXCollections.observableArrayList(files));
    }
    
    private void loadAudioSegments(AudioFile audioFile) {
        if (audioFile != null) {
            // Tải danh sách segment từ WhisperService thay vì AudioFileDAO
            WhisperService whisperService = WhisperService.getInstance();
            List<AudioSegment> segments = whisperService.getSegments(audioFile.getId());
            
            // Cập nhật model và hiển thị
            audioFile.setSegments(segments);
            segmentsTable.setItems(FXCollections.observableArrayList(segments));
        } else {
            segmentsTable.getItems().clear();
        }
    }
    
    @FXML
    private void handleImportAudio() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Audio File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.ogg", "*.aac")
        );
        
        Stage stage = (Stage) audioFilesList.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);
        
        if (selectedFile != null) {
            AudioFile audioFile = new AudioFile();
            audioFile.setFileName(selectedFile.getName());
            audioFile.setFilePath(selectedFile.getAbsolutePath());
            
            int fileId = audioFileDAO.addAudioFile(audioFile);
            if (fileId > 0) {
                refreshAudioFilesList();
                audioFilesList.getSelectionModel().select(audioFile);
                updateStatus("Imported audio file: " + selectedFile.getName());
            } else {
                showError("Failed to import audio file");
            }
        }
    }
    
    @FXML
    private void handleAnalyzeAudio() {
        AudioFile selectedFile = audioFilesList.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            showError("Please select an audio file to analyze");
            return;
        }
        
        // Hiển thị hộp thoại hỏi người dùng có muốn phân tích lại không
        boolean forceAnalyze = false;
        List<AudioSegment> existingSegments = WhisperService.getInstance().getSegments(selectedFile.getId());
        if (!existingSegments.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Re-analyze Confirmation");
            alert.setHeaderText("Audio file already has " + existingSegments.size() + " segments");
            alert.setContentText("Do you want to re-analyze and replace existing segments?");
            
            ButtonType buttonReanalyze = new ButtonType("Re-analyze");
            ButtonType buttonUseExisting = new ButtonType("Use Existing");
            
            alert.getButtonTypes().setAll(buttonReanalyze, buttonUseExisting);
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == buttonReanalyze) {
                forceAnalyze = true;
            } else {
                // Hiển thị các phân đoạn hiện có
                selectedFile.setSegments(existingSegments);
                segmentsTable.setItems(FXCollections.observableArrayList(existingSegments));
                segmentsTable.refresh();
                updateStatus("Using " + existingSegments.size() + " existing segments");
                return;
            }
        }
        
        // Lấy ngôn ngữ đã chọn
        final String selectedLanguage = getSelectedLanguageCode();
        
        // Lấy độ dài segment (seconds)
        int segmentLength = 0; // Mặc định 0 = tự động phân đoạn theo Whisper
        try {
            segmentLength = Integer.parseInt(segmentLengthField.getText().trim());
            if (segmentLength < 0) {
                segmentLength = 0;
                segmentLengthField.setText("0");
            }
        } catch (NumberFormatException e) {
            segmentLength = 0;
            segmentLengthField.setText("0");
        }
        
        final int finalSegmentLength = segmentLength;
        
        updateStatus("Analyzing audio file: " + selectedFile.getFileName() + 
                     " with language: " + WhisperService.getLanguageDisplayName(selectedLanguage) +
                     (segmentLength > 0 ? ", segment length: " + segmentLength + " seconds" : ""));
        showProgress(true);
        
        final boolean forceFinal = forceAnalyze; // For use in lambda
        
        Task<List<AudioSegment>> task = new Task<>() {
            @Override
            protected List<AudioSegment> call() throws Exception {
                // Get the Whisper service instance
                WhisperService whisperService = WhisperService.getInstance();
                
                // Transcribe audio - Service tự động lưu vào database
                // Truyền tham số force để ép phân tích lại nếu cần
                // Thêm tham số ngôn ngữ được chọn
                return whisperService.transcribeAudio(
                    selectedFile.getFilePath(), 
                    selectedFile.getId(), 
                    forceFinal,
                    selectedLanguage,
                    finalSegmentLength
                );
            }
            
            @Override
            protected void succeeded() {
                List<AudioSegment> segments = getValue();
                
                Platform.runLater(() -> {
                    // Set segments to model và hiển thị trực tiếp
                    selectedFile.setSegments(segments);
                    segmentsTable.setItems(FXCollections.observableArrayList(segments));
                    segmentsTable.refresh();
                    
                    showProgress(false);
                    updateStatus("Analysis complete: " + segments.size() + " segments found for: " + selectedFile.getFileName());
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    showProgress(false);
                    showError("Failed to analyze audio: " + getException().getMessage());
                });
            }
        };
        
        new Thread(task).start();
    }
    
    @FXML
    private void handleRemoveAudio() {
        AudioFile selectedFile = audioFilesList.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            showError("Please select an audio file to remove");
            return;
        }
        
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText("Delete Audio File");
        alert.setContentText("Are you sure you want to delete this audio file and all its segments?");
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (audioFileDAO.deleteAudioFile(selectedFile.getId())) {
                refreshAudioFilesList();
                segmentsTable.getItems().clear();
                updateStatus("Removed audio file: " + selectedFile.getFileName());
            } else {
                showError("Failed to remove audio file");
            }
        }
    }
    
    @FXML
    private void handlePlaySegment() {
        showError("Playback not implemented in this version");
    }
    
    @FXML
    private void handleEditSegment() {
        AudioSegment selectedSegment = segmentsTable.getSelectionModel().getSelectedItem();
        if (selectedSegment == null) {
            showError("Please select a segment to edit");
            return;
        }
        
        TextInputDialog dialog = new TextInputDialog(selectedSegment.getText());
        dialog.setTitle("Edit Segment");
        dialog.setHeaderText("Edit the text for this segment");
        dialog.setContentText("Text:");
        
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String newText = result.get();
            selectedSegment.setText(newText);
            
            // Cập nhật trực tiếp qua WhisperService
            WhisperService whisperService = WhisperService.getInstance();
            if (whisperService.updateSegmentText(selectedSegment.getId(), newText)) {
                segmentsTable.refresh();
                updateStatus("Updated segment text");
            } else {
                showError("Failed to update segment");
            }
        }
    }
    
    @FXML
    private void handleAddToMerge() {
        AudioFile selectedFile = audioFilesList.getSelectionModel().getSelectedItem();
        AudioSegment selectedSegment = segmentsTable.getSelectionModel().getSelectedItem();
        
        if (selectedFile == null || selectedSegment == null) {
            showError("Please select a file and segment to add to merge");
            return;
        }
        
        // Add to merge list
        MergeItem item = new MergeItem(
            selectedFile.getId(),
            selectedFile.getFileName(),
            selectedSegment.getStartTime(),
            selectedSegment.getEndTime(),
            selectedSegment.getText()
        );
        
        mergeItems.add(item);
        
        // Add to merge model
        currentMerge.addSegment(new MergeSegment(
            selectedFile.getId(),
            selectedSegment.getStartTime(),
            selectedSegment.getEndTime()
        ));
        
        updateStatus("Added segment to merge list");
    }
    
    @FXML
    private void handleNewMerge() {
        mergeItems.clear();
        currentMerge = new MergedAudio();
        currentMerge.setFileName("merged_output.mp3");
        mergeFilenameField.setText(currentMerge.getFileName());
        updateStatus("Created new merge");
    }
    
    @FXML
    private void handleMergeAudio() {
        if (mergeItems.isEmpty()) {
            showError("Please add segments to merge");
            return;
        }
        
        // Get the filename from the text field
        String filename = mergeFilenameField.getText().trim();
        if (filename.isEmpty()) {
            filename = "merged_output.mp3";
            mergeFilenameField.setText(filename);
        }
        
        // Make sure filename has .mp3 extension
        if (!filename.toLowerCase().endsWith(".mp3")) {
            filename += ".mp3";
            mergeFilenameField.setText(filename);
        }
        
        // Ask user where to save the file
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Merged Audio");
        fileChooser.setInitialFileName(filename);
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("MP3 Files", "*.mp3")
        );
        
        Stage stage = (Stage) mergeFilenameField.getScene().getWindow();
        File selectedFile = fileChooser.showSaveDialog(stage);
        
        if (selectedFile == null) {
            // User cancelled the save dialog
            return;
        }
        
        // Update the filename and path in the model
        filename = selectedFile.getName();
        mergeFilenameField.setText(filename);
        currentMerge.setFileName(filename);
        currentMerge.setFilePath(selectedFile.getAbsolutePath());
        
        // Get all audio files (for the merge operation)
        List<AudioFile> allFiles = audioFileDAO.getAllAudioFiles();
        
        updateStatus("Merging audio segments...");
        showProgress(true);
        
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                // We'll use the user-selected path directly
                String outputPath = currentMerge.getFilePath();
                
                // Extract the directory and filename
                File outputFile = new File(outputPath);
                File parentDir = outputFile.getParentFile();
                
                // Create the parent directory if it doesn't exist
                if (parentDir != null && !parentDir.exists()) {
                    boolean created = parentDir.mkdirs();
                    if (!created) {
                        throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
                    }
                }
                
                // Pass the parent directory to the AudioProcessingService
                return audioProcessingService.mergeSegments(currentMerge, allFiles);
            }
            
            @Override
            protected void succeeded() {
                String outputPath = getValue();
                currentMerge.setFilePath(outputPath);
                
                // Save to database
                int mergeId = mergedAudioDAO.addMergedAudio(currentMerge);
                
                showProgress(false);
                if (mergeId > 0) {
                    updateStatus("Merge complete: " + outputPath);
                    
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Merge Complete");
                    alert.setHeaderText("Audio merged successfully");
                    alert.setContentText("Output file: " + outputPath);
                    
                    // Add button to open the file location
                    ButtonType openLocationButton = new ButtonType("Open Location");
                    alert.getButtonTypes().add(openLocationButton);
                    
                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent() && result.get() == openLocationButton) {
                        // Open the file location in explorer
                        try {
                            File file = new File(outputPath);
                            Runtime.getRuntime().exec("explorer.exe /select," + file.getAbsolutePath());
                        } catch (Exception e) {
                            showError("Failed to open file location: " + e.getMessage());
                        }
                    }
                } else {
                    updateStatus("Merge complete but failed to save to database");
                }
            }
            
            @Override
            protected void failed() {
                showProgress(false);
                Throwable exception = getException();
                String errorMessage = exception != null ? exception.getMessage() : "Unknown error";
                showError("Failed to merge audio: " + errorMessage);
                exception.printStackTrace();
            }
        };
        
        new Thread(task).start();
    }
    
    @FXML
    private void handleSaveMerge() {
        if (mergeItems.isEmpty()) {
            showError("Please add segments to merge before saving");
            return;
        }
        
        // Get the filename from the text field
        String filename = mergeFilenameField.getText().trim();
        if (filename.isEmpty()) {
            filename = "merged_output.mp3";
            mergeFilenameField.setText(filename);
        }
        
        // Make sure filename has .mp3 extension
        if (!filename.toLowerCase().endsWith(".mp3")) {
            filename += ".mp3";
            mergeFilenameField.setText(filename);
        }
        
        // Update the filename in the model
        currentMerge.setFileName(filename);
        
        // Show dialog to get a name for the merge configuration
        TextInputDialog dialog = new TextInputDialog(filename.replace(".mp3", ""));
        dialog.setTitle("Save Merge Configuration");
        dialog.setHeaderText("Enter a name for this merge configuration");
        dialog.setContentText("Name:");
        
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String mergeName = result.get().trim();
            if (!mergeName.isEmpty()) {
                // Update the filename
                String mergeFilename = mergeName + ".mp3";
                currentMerge.setFileName(mergeFilename);
                mergeFilenameField.setText(mergeFilename);
                
                // Save to database
                int mergeId = mergedAudioDAO.addMergedAudio(currentMerge);
                
                if (mergeId > 0) {
                    updateStatus("Saved merge configuration: " + mergeName);
                    
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Save Successful");
                    alert.setHeaderText("Merge configuration saved");
                    alert.setContentText("Configuration saved with ID: " + mergeId);
                    alert.showAndWait();
                } else {
                    showError("Failed to save merge configuration");
                }
            } else {
                showError("Merge configuration name cannot be empty");
            }
        }
    }
    
    @FXML
    private void handleLoadMerge() {
        // Get all saved merge configurations
        List<MergedAudio> savedMerges = mergedAudioDAO.getAllMergedAudio();
        
        if (savedMerges.isEmpty()) {
            showError("No saved merge configurations found");
            return;
        }
        
        // Create a dialog to select a merge configuration
        ChoiceDialog<MergedAudio> dialog = new ChoiceDialog<>(savedMerges.get(0), savedMerges);
        dialog.setTitle("Load Merge Configuration");
        dialog.setHeaderText("Select a merge configuration to load");
        dialog.setContentText("Configuration:");
        
        Optional<MergedAudio> result = dialog.showAndWait();
        if (result.isPresent()) {
            MergedAudio selectedMerge = result.get();
            
            // Load the full merge configuration with segments
            MergedAudio loadedMerge = mergedAudioDAO.getMergedAudioById(selectedMerge.getId());
            if (loadedMerge != null) {
                // Clear current merge items
                mergeItems.clear();
                
                // Set the current merge to the loaded one
                currentMerge = loadedMerge;
                mergeFilenameField.setText(currentMerge.getFileName());
                
                // Populate the merge items list for display
                for (MergeSegment segment : currentMerge.getSegments()) {
                    // Find the source audio file
                    AudioFile sourceFile = null;
                    for (AudioFile file : audioFileDAO.getAllAudioFiles()) {
                        if (file.getId() == segment.getSourceFileId()) {
                            sourceFile = file;
                            break;
                        }
                    }
                    
                    if (sourceFile != null) {
                        // Find the segment text from the original audio file
                        String segmentText = "";
                        WhisperService whisperService = WhisperService.getInstance();
                        List<AudioSegment> segments = whisperService.getSegments(sourceFile.getId());
                        for (AudioSegment audioSegment : segments) {
                            if (audioSegment.getStartTime() == segment.getStartTime() && 
                                audioSegment.getEndTime() == segment.getEndTime()) {
                                segmentText = audioSegment.getText();
                                break;
                            }
                        }
                        
                        // Add to merge items list
                        MergeItem item = new MergeItem(
                            segment.getSourceFileId(),
                            sourceFile.getFileName(),
                            segment.getStartTime(),
                            segment.getEndTime(),
                            segmentText
                        );
                        mergeItems.add(item);
                    }
                }
                
                updateStatus("Loaded merge configuration: " + currentMerge.getFileName());
            } else {
                showError("Failed to load merge configuration");
            }
        }
    }
    
    @FXML
    private void handleRemoveMergeSegment() {
        MergeItem selectedItem = mergeSegmentsTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showError("Please select a segment to remove");
            return;
        }
        
        int index = mergeSegmentsTable.getSelectionModel().getSelectedIndex();
        mergeItems.remove(index);
        currentMerge.getSegments().remove(index);
        updateStatus("Removed segment from merge list");
    }
    
    @FXML
    private void handleMoveUp() {
        int selectedIndex = mergeSegmentsTable.getSelectionModel().getSelectedIndex();
        if (selectedIndex <= 0 || selectedIndex >= mergeItems.size()) {
            return;
        }
        
        // Swap in the observable list
        MergeItem item = mergeItems.remove(selectedIndex);
        mergeItems.add(selectedIndex - 1, item);
        
        // Swap in the model
        MergeSegment segment = currentMerge.getSegments().remove(selectedIndex);
        currentMerge.getSegments().add(selectedIndex - 1, segment);
        
        // Reselect the item
        mergeSegmentsTable.getSelectionModel().select(selectedIndex - 1);
    }
    
    @FXML
    private void handleMoveDown() {
        int selectedIndex = mergeSegmentsTable.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= mergeItems.size() - 1) {
            return;
        }
        
        // Swap in the observable list
        MergeItem item = mergeItems.remove(selectedIndex);
        mergeItems.add(selectedIndex + 1, item);
        
        // Swap in the model
        MergeSegment segment = currentMerge.getSegments().remove(selectedIndex);
        currentMerge.getSegments().add(selectedIndex + 1, segment);
        
        // Reselect the item
        mergeSegmentsTable.getSelectionModel().select(selectedIndex + 1);
    }
    
    @FXML
    private void handlePreview() {
        if (mergeItems.isEmpty()) {
            showError("Please add segments to preview");
            return;
        }
        
        // Ask user where to save the preview file
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Preview Audio");
        fileChooser.setInitialFileName("preview_" + System.currentTimeMillis() + ".mp3");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("MP3 Files", "*.mp3")
        );
        
        Stage stage = (Stage) mergeFilenameField.getScene().getWindow();
        File selectedFile = fileChooser.showSaveDialog(stage);
        
        if (selectedFile == null) {
            // User cancelled the save dialog
            return;
        }
        
        // Create a temporary merge configuration
        MergedAudio previewMerge = new MergedAudio();
        previewMerge.setFileName(selectedFile.getName());
        previewMerge.setFilePath(selectedFile.getAbsolutePath());
        
        // Copy segments from current merge
        for (MergeSegment segment : currentMerge.getSegments()) {
            previewMerge.addSegment(new MergeSegment(
                segment.getSourceFileId(),
                segment.getStartTime(),
                segment.getEndTime()
            ));
        }
        
        // Get all audio files (for the merge operation)
        List<AudioFile> allFiles = audioFileDAO.getAllAudioFiles();
        
        updateStatus("Generating preview...");
        showProgress(true);
        
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                // Extract the directory and filename
                File outputFile = new File(previewMerge.getFilePath());
                File parentDir = outputFile.getParentFile();
                
                // Create the parent directory if it doesn't exist
                if (parentDir != null && !parentDir.exists()) {
                    boolean created = parentDir.mkdirs();
                    if (!created) {
                        throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
                    }
                }
                
                // Merge the segments
                return audioProcessingService.mergeSegments(previewMerge, allFiles);
            }
            
            @Override
            protected void succeeded() {
                String outputPath = getValue();
                showProgress(false);
                updateStatus("Preview generated: " + outputPath);
                
                // Play the preview using the default media player
                try {
                    File file = new File(outputPath);
                    if (file.exists()) {
                        java.awt.Desktop.getDesktop().open(file);
                    } else {
                        showError("Preview file not found: " + outputPath);
                    }
                } catch (Exception e) {
                    showError("Failed to play preview: " + e.getMessage());
                }
            }
            
            @Override
            protected void failed() {
                showProgress(false);
                Throwable exception = getException();
                String errorMessage = exception != null ? exception.getMessage() : "Unknown error";
                showError("Failed to generate preview: " + errorMessage);
                exception.printStackTrace();
            }
        };
        
        new Thread(task).start();
    }
    
    @FXML
    private void handleSetupWhisper() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Whisper Setup");
        alert.setHeaderText("Whisper Installation Instructions");
        
        TextArea textArea = new TextArea(WhisperService.getInstallationGuide());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        
        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }
    
    @FXML
    private void handleSetupFFmpeg() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("FFmpeg Setup");
        alert.setHeaderText("FFmpeg Installation Instructions");
        
        TextArea textArea = new TextArea(AudioProcessingService.getInstallationGuide());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        
        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }
    
    @FXML
    private void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Sound Converter");
        alert.setHeaderText("Sound Converter - AI Audio Tool");
        alert.setContentText("Version 1.0\n\nAn application for managing, analyzing, and merging audio files with AI assistance.");
        alert.showAndWait();
    }
    
    @FXML
    private void handleExit() {
        Platform.exit();
    }
    
    private void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }
    
    private void showProgress(boolean show) {
        Platform.runLater(() -> progressBar.setVisible(show));
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Khởi tạo ComboBox lựa chọn ngôn ngữ
     */
    private void initLanguageComboBox() {
        Map<String, String> languages = WhisperService.getSupportedLanguages();
        ObservableList<String> items = FXCollections.observableArrayList();
        
        // Thêm các ngôn ngữ có sẵn vào ComboBox
        for (Map.Entry<String, String> entry : languages.entrySet()) {
            items.add(entry.getValue());
        }
        
        // Thiết lập các giá trị cho ComboBox
        languageComboBox.setItems(items);
        
        // Chọn "Tiếng Việt" làm mặc định thay vì "Tự động phát hiện"
        languageComboBox.getSelectionModel().select(
            WhisperService.getLanguageDisplayName(WhisperService.LANG_VIETNAMESE)
        );
    }
    
    /**
     * Lấy mã ngôn ngữ từ tên hiển thị được chọn trong ComboBox
     * @return Mã ngôn ngữ (auto, en, vi, ja)
     */
    private String getSelectedLanguageCode() {
        String selectedLanguage = languageComboBox.getSelectionModel().getSelectedItem();
        
        if (selectedLanguage == null) {
            return WhisperService.LANG_AUTO;
        }
        
        for (Map.Entry<String, String> entry : WhisperService.getSupportedLanguages().entrySet()) {
            if (entry.getValue().equals(selectedLanguage)) {
                return entry.getKey();
            }
        }
        
        return WhisperService.LANG_AUTO;
    }
    
    // Helper class for displaying merge items in the table
    public static class MergeItem {
        private final int sourceFileId;
        private final String fileName;
        private final int startTime;
        private final int endTime;
        private final String text;
        
        public MergeItem(int sourceFileId, String fileName, int startTime, int endTime, String text) {
            this.sourceFileId = sourceFileId;
            this.fileName = fileName;
            this.startTime = startTime;
            this.endTime = endTime;
            this.text = text;
        }
        
        public int getSourceFileId() {
            return sourceFileId;
        }
        
        public String getFileName() {
            return fileName;
        }
        
        public String getStartTime() {
            int hours = startTime / 3600000;
            int minutes = (startTime % 3600000) / 60000;
            int seconds = (startTime % 60000) / 1000;
            int millis = startTime % 1000;
            return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
        }
        
        public String getEndTime() {
            int hours = endTime / 3600000;
            int minutes = (endTime % 3600000) / 60000;
            int seconds = (endTime % 60000) / 1000;
            int millis = endTime % 1000;
            return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
        }
        
        public String getText() {
            return text;
        }
    }
} 