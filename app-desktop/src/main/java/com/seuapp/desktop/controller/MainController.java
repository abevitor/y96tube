package com.seuapp.desktop.controller;

import com.seuapp.core.YtDlpService;
import com.seuapp.core.model.OutputType;
import com.seuapp.core.model.VideoFormat;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MainController {

    private static final Pattern YOUTUBE_URL_PATTERN = Pattern.compile(
            "^(https?://)?(www\\.)?(youtube\\.com/(watch\\?v=|shorts/)|youtu\\.be/)[\\w-]+.*$",
            Pattern.CASE_INSENSITIVE
    );

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
            YtDlpService.withDefaultLocations(Paths.get(System.getProperty("user.dir")));

    private List<VideoFormat> todosFormatos = List.of();

    private final PauseTransition buscaAutomatica = new PauseTransition(Duration.millis(800));

    @FXML
    public void initialize() {
        mp3Radio.setSelected(true);

        outputTypeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> atualizarComboFiltrado());

        buscaAutomatica.setOnFinished(e -> buscarFormatos());

        urlField.textProperty().addListener((obs, oldText, newText) -> {
            buscaAutomatica.stop();
            if (!newText.trim().isEmpty()) {
                buscaAutomatica.playFromStart();
            }
        });

        verificarBinarios();
    }

    /**
     * Roda uma vez ao abrir o app: confirma que yt-dlp/ffmpeg estão acessíveis.
     * Se não estiverem, trava a interface com uma mensagem clara em vez de deixar
     * o usuário descobrir isso só quando clicar em algo.
     */
    private void verificarBinarios() {
        boolean ok = ytDlpService.binariosDisponiveis();
        if (!ok) {
            statusLabel.setText("yt-dlp ou ffmpeg não encontrados. Coloque os dois dentro da pasta tools/ e reabra o app.");
            listFormatsButton.setDisable(true);
            downloadButton.setDisable(true);
            urlField.setDisable(true);
        }
    }

    @FXML
    private void onListFormats() {
        buscarFormatos();
    }

    private void buscarFormatos() {
        String url = urlField.getText().trim();

        if (url.isEmpty()) {
            return;
        }

        if (!YOUTUBE_URL_PATTERN.matcher(url).matches()) {
            statusLabel.setText("Isso não parece um link válido do YouTube.");
            return;
        }

        statusLabel.setText("Consultando formatos disponíveis...");
        listFormatsButton.setDisable(true);

        Task<List<VideoFormat>> task = new Task<>() {
            @Override
            protected List<VideoFormat> call() throws Exception {
                return ytDlpService.listFormats(url);
            }
        };

        task.setOnSucceeded(e -> {
            todosFormatos = task.getValue();
            atualizarComboFiltrado();
            statusLabel.setText("Formatos carregados.");
            listFormatsButton.setDisable(false);
        });

        task.setOnFailed(e -> {
            statusLabel.setText(mapearErro(task.getException()));
            listFormatsButton.setDisable(false);
        });

        new Thread(task, "list-formats").start();
    }

    private void atualizarComboFiltrado() {
        boolean queroAudio = mp3Radio.isSelected();

        List<VideoFormat> filtrados = todosFormatos.stream()
                .filter(f -> f.isAudioOnly() == queroAudio)
                .collect(Collectors.toList());

        formatComboBox.getItems().setAll(filtrados);
        if (!filtrados.isEmpty()) {
            formatComboBox.getSelectionModel().selectFirst();
        }

        statusLabel.setText("Formatos disponíveis: " + filtrados.size());
    }

    @FXML
    private void onDownload() {
        String url = urlField.getText().trim();

        if (url.isEmpty()) {
            statusLabel.setText("Cole um link do YouTube primeiro.");
            return;
        }

        if (!YOUTUBE_URL_PATTERN.matcher(url).matches()) {
            statusLabel.setText("Isso não parece um link válido do YouTube.");
            return;
        }

        OutputType outputType = mp3Radio.isSelected() ? OutputType.MP3 : OutputType.MP4;
        VideoFormat selectedFormat = formatComboBox.getSelectionModel().getSelectedItem();
        String formatId = selectedFormat != null ? selectedFormat.getFormatId() : null;

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Escolha a pasta de destino");
        File destination = chooser.showDialog(downloadButton.getScene().getWindow());
        if (destination == null) {
            return;
        }
        Path outputDir = destination.toPath();

        downloadButton.setDisable(true);
        progressBar.setProgress(0);
        statusLabel.setText("Baixando...");

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return ytDlpService.download(url, formatId, outputType, outputDir,
                        percent -> Platform.runLater(() -> progressBar.setProgress(percent / 100.0)));
            }
        };

        task.setOnSucceeded(e -> {
            int exitCode = task.getValue();
            statusLabel.setText(exitCode == 0 ? "Download concluído!" : "yt-dlp terminou com erro (código " + exitCode + ")");
            downloadButton.setDisable(false);
        });

        task.setOnFailed(e -> {
            statusLabel.setText(mapearErro(task.getException()));
            downloadButton.setDisable(false);
        });

        new Thread(task, "download").start();
    }

    /**
     * Traduz os erros mais comuns do yt-dlp pra mensagens que fazem sentido
     * pra quem está usando o app, em vez de mostrar a exception técnica crua.
     */
    private String mapearErro(Throwable erro) {
        String mensagem = erro != null && erro.getMessage() != null ? erro.getMessage() : "";

        if (mensagem.contains("Private video")) {
            return "Este vídeo é privado e não pode ser baixado.";
        }
        if (mensagem.contains("Video unavailable") || mensagem.contains("This video is unavailable")) {
            return "Vídeo indisponível (pode ter sido removido).";
        }
        if (mensagem.contains("Sign in to confirm your age")) {
            return "Este vídeo tem restrição de idade e não pode ser baixado assim.";
        }
        if (mensagem.contains("Video unavailable. This video contains content")) {
            return "Vídeo bloqueado por direitos autorais.";
        }
        if (mensagem.toLowerCase().contains("temporary failure") || mensagem.toLowerCase().contains("name or service not known")) {
            return "Sem conexão com a internet. Verifique sua rede e tente de novo.";
        }
        if (mensagem.contains("Incomplete YouTube ID") || mensagem.contains("looks truncated")) {
            return "O link parece estar incompleto ou incorreto.";
        }

        return "Erro: " + (mensagem.isEmpty() ? "algo deu errado." : mensagem);
    }
}