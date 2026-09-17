package com.seuapp.desktop.controller;

import com.seuapp.core.YtDlpService;
import com.seuapp.core.model.OutputType;
import com.seuapp.core.model.VideoFormat;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class MainController {
    @FXML private TextField urlField;
    @FXML private Button listFormatsButton;
    @FXML private ComboBox<VideoFormat> formatComboBox;
    @FXML private RadioButton mp3Radio;
    @FXML private RadioButton mp4Radio;
    @FXML private ToggleGroup outputTypeGroup;
    @FXML private Button downloadButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;

    private final YtDlpService ytDlpService = 
               YtDlpService.withDefaultLocations(Paths.get(System.getProperty("user.dir ")));

    @FXML 
    public void initialize() {
        mp3Radio.setSelected(true);
        formatComboBox.setDisable(true);

         outputTypeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) ->
                formatComboBox.setDisable(mp3Radio.isSelected()));
    }

    @FXML 
    private void onListFormats() {
        String url = urlField.getText().trim();

        if(url.isEmpty()) {
            statusLabel.setText("Cole um link do YouTube primeiro.");
            return;
        }

        statusLabel.setText("Consultando formatos disponiveis...");
        listFormatsButton.setDisable(true);

        Task<List<VideoFormat>> task = new Task<>() {
            @Override 
            protected  List<VideoFormat> call() throws Exception {
                return ytDlpService.listFormats(url);
            }
        };

        task.setOnSucceeded(e -> {
            formatComboBox.getItems().setAll(task.getValue());
            if(!formatComboBox.getItems().isEmpty()) {
                formatComboBox.getSelectionModel().selectFirst(); 
            }
            statusLabel.setText("Formatos carregados.");
            listFormatsButton.setDisable(false);
        });

        task.setOnFailed(e -> {
            statusLabel.setText("Erro: " + task.getException().getMessage());
            listFormatsButton.setDisable(false);
        });

        new Thread(task, "list-formats").start();
    }
}
