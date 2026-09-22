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
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MainController {

    private static final Pattern YOUTUBE_URL_PATTERN =
            Pattern.compile(
                    "^(https?://)?(www\\.)?"
                            + "(youtube\\.com/(watch\\?v=|shorts/)"
                            + "|youtu\\.be/)"
                            + "[\\w-]+.*$",
                    Pattern.CASE_INSENSITIVE
            );

    @FXML private TextField urlField;
    @FXML private Button listFormatsButton;
    @FXML private ComboBox<VideoFormat> formatComboBox;

    @FXML private RadioButton mp3Radio;
    @FXML private RadioButton mp4Radio;

    @FXML private ToggleGroup outputTypeGroup;

    @FXML private Button downloadButton;
    @FXML private Button cancelButton;

    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;

    private final YtDlpService ytDlpService =
            YtDlpService.withDefaultLocations(
                    Paths.get(
                            System.getProperty(
                                    "user.dir"
                            )
                    )
            );

    private List<VideoFormat> todosFormatos =
            List.of();

    private final PauseTransition buscaAutomatica =
            new PauseTransition(
                    Duration.millis(800)
            );

    private volatile Process processoDownloadAtual;

    private volatile boolean cancelamentoSolicitado =
            false;

    @FXML
    public void initialize() {

        mp3Radio.setSelected(true);

        cancelButton.setDisable(true);

        outputTypeGroup
                .selectedToggleProperty()
                .addListener(
                        (obs, oldVal, newVal)
                                -> atualizarComboFiltrado()
                );

        buscaAutomatica.setOnFinished(
                e -> buscarFormatos()
        );

        urlField.textProperty()
                .addListener(
                        (obs, oldText, newText) -> {

                            buscaAutomatica.stop();

                            if (!newText
                                    .trim()
                                    .isEmpty()) {

                                buscaAutomatica.playFromStart();
                            }
                        }
                );

        verificarBinarios();
    }

    private void verificarBinarios() {

        boolean ok =
                ytDlpService.binariosDisponiveis();

        if (!ok) {

            statusLabel.setText(
                    "yt-dlp ou ffmpeg não encontrados. "
                            + "Coloque os dois dentro da pasta tools/ "
                            + "e reabra o app."
            );

            listFormatsButton.setDisable(true);
            downloadButton.setDisable(true);
            cancelButton.setDisable(true);
            urlField.setDisable(true);
        }
    }

    @FXML
    private void onListFormats() {
        buscarFormatos();
    }

    private void buscarFormatos() {

        String url =
                urlField.getText().trim();

        if (url.isEmpty()) {
            return;
        }

        if (!YOUTUBE_URL_PATTERN
                .matcher(url)
                .matches()) {

            statusLabel.setText(
                    "Isso não parece um link válido do YouTube."
            );

            return;
        }

        statusLabel.setText(
                "Consultando formatos disponíveis..."
        );

        travarInterface(true);

        Task<List<VideoFormat>> task =
                new Task<>() {

                    @Override
                    protected List<VideoFormat> call()
                            throws Exception {

                        return ytDlpService
                                .listFormats(url);
                    }
                };

        task.setOnSucceeded(
                e -> {

                    todosFormatos =
                            task.getValue();

                    atualizarComboFiltrado();

                    statusLabel.setText(
                            "Formatos carregados."
                    );

                    travarInterface(false);
                }
        );

        task.setOnFailed(
                e -> {

                    statusLabel.setText(
                            mapearErro(
                                    task.getException()
                            )
                    );

                    travarInterface(false);
                }
        );

        new Thread(
                task,
                "list-formats"
        ).start();
    }

    private void atualizarComboFiltrado() {

        boolean queroAudio =
                mp3Radio.isSelected();

        List<VideoFormat> filtrados;

        if (queroAudio) {

            /*
             * MP3:
             * somente formatos que possuem apenas áudio.
             */
            filtrados =
                    todosFormatos.stream()
                            .filter(
                                    VideoFormat::isAudioOnly
                            )
                            .collect(
                                    Collectors.toList()
                            );

        } else {

            /*
             * MP4:
             * somente formatos de vídeo cujo container
             * original já é MP4.
             *
             * Isso impede o usuário de selecionar
             * WebM e depois receber um resultado inesperado.
             */
            filtrados =
                    todosFormatos.stream()
                            .filter(
                                    f ->
                                            !f.isAudioOnly()
                                                    && f.getExt() != null
                                                    && f.getExt()
                                                    .equalsIgnoreCase("mp4")
                            )
                            .collect(
                                    Collectors.toList()
                            );
        }

        formatComboBox
                .getItems()
                .setAll(filtrados);

        if (!filtrados.isEmpty()) {

            formatComboBox
                    .getSelectionModel()
                    .selectFirst();
        }

        statusLabel.setText(
                "Formatos disponíveis: "
                        + filtrados.size()
        );
    }

    private void travarInterface(
            boolean travar) {

        urlField.setDisable(travar);
        listFormatsButton.setDisable(travar);
        downloadButton.setDisable(travar);

        mp3Radio.setDisable(travar);
        mp4Radio.setDisable(travar);

        formatComboBox.setDisable(travar);
    }

    @FXML
    private void onDownload() {

        String url =
                urlField.getText().trim();

        if (url.isEmpty()) {

            statusLabel.setText(
                    "Cole um link do YouTube primeiro."
            );

            return;
        }

        if (!YOUTUBE_URL_PATTERN
                .matcher(url)
                .matches()) {

            statusLabel.setText(
                    "Isso não parece um link válido do YouTube."
            );

            return;
        }

        OutputType outputType =
                mp3Radio.isSelected()
                        ? OutputType.MP3
                        : OutputType.MP4;

        VideoFormat selectedFormat =
                formatComboBox
                        .getSelectionModel()
                        .getSelectedItem();

        String formatId =
                selectedFormat != null
                        ? selectedFormat.getFormatId()
                        : null;

        DirectoryChooser chooser =
                new DirectoryChooser();

        chooser.setTitle(
                "Escolha a pasta de destino"
        );

        File destination =
                chooser.showDialog(
                        downloadButton
                                .getScene()
                                .getWindow()
                );

        if (destination == null) {
            return;
        }

        Path outputDir =
                destination.toPath();

        cancelamentoSolicitado = false;
        processoDownloadAtual = null;

        travarInterface(true);

        /*
         * Ainda não habilita.
         * Só habilitamos quando o Process realmente iniciar.
         */
        cancelButton.setDisable(true);

        progressBar.setProgress(0);

        statusLabel.setText(
                "Baixando..."
        );

        Task<Integer> task =
                new Task<>() {

                    @Override
                    protected Integer call()
                            throws Exception {

                        return ytDlpService.download(
                                url,
                                formatId,
                                outputType,
                                outputDir,

                                percent ->
                                        Platform.runLater(
                                                () ->
                                                        progressBar
                                                                .setProgress(
                                                                        percent
                                                                                / 100.0
                                                                )
                                        ),

                                processo -> {

                                    processoDownloadAtual =
                                            processo;

                                    Platform.runLater(
                                            () ->
                                                    cancelButton
                                                            .setDisable(false)
                                    );
                                }
                        );
                    }
                };

        task.setOnSucceeded(
                e -> {

                    int exitCode =
                            task.getValue();

                    if (cancelamentoSolicitado) {

                        statusLabel.setText(
                                "Download cancelado."
                        );

                    } else {

                        statusLabel.setText(
                                exitCode == 0
                                        ? "Download concluído!"
                                        : "yt-dlp terminou com erro "
                                        + "(código "
                                        + exitCode
                                        + ")"
                        );
                    }

                    finalizarDownload();
                }
        );

        task.setOnFailed(
                e -> {

                    if (cancelamentoSolicitado) {

                        statusLabel.setText(
                                "Download cancelado."
                        );

                    } else {

                        statusLabel.setText(
                                mapearErro(
                                        task.getException()
                                )
                        );
                    }

                    finalizarDownload();
                }
        );

        new Thread(
                task,
                "download"
        ).start();
    }

    @FXML
    private void onCancelarDownload() {

        cancelamentoSolicitado = true;

        Process processo =
                processoDownloadAtual;

        if (processo == null
                || !processo.isAlive()) {

            return;
        }

        statusLabel.setText(
                "Cancelando..."
        );

        try {

            boolean windows =
                    System.getProperty(
                            "os.name",
                            ""
                    )
                            .toLowerCase()
                            .contains("win");

            if (windows) {

                /*
                 * /T = mata os processos filhos também.
                 * /F = força encerramento.
                 */
                Process killer =
                        new ProcessBuilder(
                                "taskkill",
                                "/PID",
                                String.valueOf(
                                        processo.pid()
                                ),
                                "/T",
                                "/F"
                        )
                                .redirectErrorStream(true)
                                .start();

                killer.waitFor(
                        10,
                        java.util.concurrent.TimeUnit.SECONDS
                );

            } else {

                processo.toHandle()
                        .descendants()
                        .forEach(
                                ProcessHandle::destroyForcibly
                        );

                processo.destroyForcibly();
            }

        } catch (IOException e) {

            System.out.println(
                    "Erro ao cancelar: "
                            + e.getMessage()
            );

            processo.toHandle()
                    .descendants()
                    .forEach(
                            ProcessHandle::destroyForcibly
                    );

            processo.destroyForcibly();

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            processo.toHandle()
                    .descendants()
                    .forEach(
                            ProcessHandle::destroyForcibly
                    );

            processo.destroyForcibly();
        }
    }

    private void finalizarDownload() {

        processoDownloadAtual = null;

        cancelButton.setDisable(true);

        travarInterface(false);
    }

    private String mapearErro(
            Throwable erro) {

        if (erro == null) {

            return "Ocorreu um erro desconhecido.";
        }

        String mensagem =
                erro.getMessage();

        if (mensagem == null
                || mensagem.isBlank()) {

            mensagem =
                    erro.getClass()
                            .getSimpleName();
        }

        if (mensagem.contains(
                "Private video")) {

            return "Este vídeo é privado e não pode ser baixado.";
        }

        if (mensagem.contains(
                "Video unavailable")
                || mensagem.contains(
                "This video is unavailable")) {

            return "Vídeo indisponível (pode ter sido removido).";
        }

        if (mensagem.contains(
                "Sign in to confirm your age")) {

            return "Este vídeo tem restrição de idade e não pode ser baixado assim.";
        }

        if (mensagem.contains(
                "Video unavailable. This video contains content")) {

            return "Vídeo bloqueado por direitos autorais.";
        }

        String lower =
                mensagem.toLowerCase();

        if (lower.contains(
                "temporary failure")
                || lower.contains(
                "name or service not known")) {

            return "Sem conexão com a internet. "
                    + "Verifique sua rede e tente de novo.";
        }

        if (mensagem.contains(
                "Incomplete YouTube ID")
                || mensagem.contains(
                "looks truncated")) {

            return "O link parece estar incompleto ou incorreto.";
        }

        return "Erro: " + mensagem;
    }
}