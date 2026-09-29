package com.seuapp.desktop.controller;

import com.seuapp.core.YtDlpService;
import com.seuapp.core.YtDlpService.VideoDetails;
import com.seuapp.core.model.OutputType;
import com.seuapp.core.model.VideoFormat;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;
import javafx.scene.control.ProgressBar;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MainController {

    // =====================================================
    // VALIDAÇÃO DO LINK DO YOUTUBE
    // =====================================================

    private static final Pattern YOUTUBE_URL_PATTERN =
            Pattern.compile(
                    "^(https?://)?(www\\.)?"
                            + "(youtube\\.com/(watch\\?v=|shorts/)"
                            + "|youtu\\.be/)"
                            + "[\\w-]+.*$",
                    Pattern.CASE_INSENSITIVE
            );


    // =====================================================
    // COMPONENTES FXML
    // =====================================================

    @FXML
    private TextField urlField;

    @FXML
    private Button listFormatsButton;

    @FXML
    private ComboBox<VideoFormat> formatComboBox;

    @FXML
    private RadioButton mp3Radio;

    @FXML
    private RadioButton mp4Radio;

    @FXML
    private ToggleGroup outputTypeGroup;

    @FXML
    private Button downloadButton;

    @FXML
    private Button cancelButton;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label statusLabel;


    // =====================================================
    // PREVIEW DO VÍDEO
    // =====================================================

    @FXML
    private VBox videoPreviewBox;

    @FXML
    private ImageView thumbnailImage;

    @FXML
    private Label videoTitleLabel;

    @FXML
    private Label videoChannelLabel;


    // =====================================================
    // SERVIÇO
    // =====================================================

    private final YtDlpService ytDlpService =
            YtDlpService.withDefaultLocations();


    // =====================================================
    // FORMATOS
    // =====================================================

    private List<VideoFormat> todosFormatos =
            List.of();


    // =====================================================
    // BUSCA AUTOMÁTICA
    // =====================================================

    private final PauseTransition buscaAutomatica =
            new PauseTransition(
                    Duration.millis(800)
            );


    /*
     * Identifica qual busca é a mais recente.
     *
     * Isso evita que uma consulta antiga termine depois
     * de uma nova consulta e sobrescreva a interface.
     */
    private final AtomicLong idDaBusca =
            new AtomicLong();


    // =====================================================
    // DOWNLOAD / CANCELAMENTO
    // =====================================================

    private volatile Process processoDownloadAtual;

    private volatile boolean cancelamentoSolicitado =
            false;


    // =====================================================
    // INITIALIZE
    // =====================================================

    @FXML
    public void initialize() {

        // -------------------------------------------------
        // MP3 como padrão
        // -------------------------------------------------

        mp3Radio.setSelected(true);


        // -------------------------------------------------
        // Cancelar começa desabilitado
        // -------------------------------------------------

        cancelButton.setDisable(true);


        // -------------------------------------------------
        // Preview começa escondido
        // -------------------------------------------------

        limparPreview();


        // -------------------------------------------------
        // Combo começa vazio
        // -------------------------------------------------

        formatComboBox
                .getItems()
                .clear();


        // -------------------------------------------------
        // Botão de download começa desabilitado
        // -------------------------------------------------

        downloadButton.setDisable(true);


        // -------------------------------------------------
        // Troca MP3 / MP4
        // -------------------------------------------------

        outputTypeGroup
                .selectedToggleProperty()
                .addListener(
                        (obs, oldVal, newVal) ->
                                atualizarComboFiltrado()
                );


        // -------------------------------------------------
        // Busca automática
        // -------------------------------------------------

        buscaAutomatica.setOnFinished(
                e -> buscarVideo()
        );


        // -------------------------------------------------
        // Listener do campo URL
        // -------------------------------------------------

        urlField.textProperty()
                .addListener(
                        (obs, oldText, newText) -> {

                            /*
                             * Invalida qualquer consulta anterior.
                             */
                            idDaBusca.incrementAndGet();


                            /*
                             * Cancela o timer anterior.
                             */
                            buscaAutomatica.stop();


                            String url =
                                    newText.trim();


                            /*
                             * Campo vazio.
                             */
                            if (url.isEmpty()) {

                                limparPreview();

                                todosFormatos =
                                        List.of();

                                formatComboBox
                                        .getItems()
                                        .clear();

                                downloadButton
                                        .setDisable(true);

                                statusLabel.setText(
                                        "Pronto."
                                );

                                return;
                            }


                            /*
                             * Enquanto o usuário está alterando
                             * o link, limpamos a seleção anterior.
                             */
                            todosFormatos =
                                    List.of();

                            formatComboBox
                                    .getItems()
                                    .clear();

                            limparPreview();

                            downloadButton
                                    .setDisable(true);


                            /*
                             * Só agenda a busca se o texto
                             * parecer um link válido.
                             */
                            if (YOUTUBE_URL_PATTERN
                                    .matcher(url)
                                    .matches()) {

                                statusLabel.setText(
                                        "Aguardando..."
                                );

                                buscaAutomatica
                                        .playFromStart();

                            } else {

                                statusLabel.setText(
                                        "Cole um link válido do YouTube."
                                );
                            }
                        }
                );


        // -------------------------------------------------
        // Verificar binários
        // -------------------------------------------------

        verificarBinarios();
    }


    // =====================================================
    // ENCERRAR APLICAÇÃO
    // =====================================================

    /*
     * Chamado pelo Launcher antes de fechar a janela.
     *
     * Garante que nenhum processo externo fique executando
     * depois que o Y96TUBE for encerrado.
     */
    public void encerrarAplicacao() {

        /*
         * Para a busca automática agendada.
         */
        buscaAutomatica.stop();


        /*
         * Invalida uma busca pendente.
         */
        idDaBusca.incrementAndGet();


        /*
         * Marca cancelamento.
         */
        cancelamentoSolicitado = true;


        /*
         * Captura o processo atual.
         */
        Process processo =
                processoDownloadAtual;


        /*
         * Se houver download ativo, encerra toda a
         * árvore de processos.
         */
        if (processo != null
                && processo.isAlive()) {

            ytDlpService.cancelarDownload(
                    processo
            );
        }
    }


    // =====================================================
    // VERIFICAR BINÁRIOS
    // =====================================================

    private void verificarBinarios() {

        Task<Boolean> task =
                new Task<>() {

                    @Override
                    protected Boolean call() {

                        return ytDlpService
                                .binariosDisponiveis();
                    }
                };


        task.setOnSucceeded(
                e -> {

                    boolean ok =
                            task.getValue();


                    if (!ok) {

                        statusLabel.setText(
                                "yt-dlp ou ffmpeg não encontrados."
                        );


                        listFormatsButton
                                .setDisable(true);


                        downloadButton
                                .setDisable(true);


                        urlField
                                .setDisable(true);


                    } else {

                        statusLabel.setText(
                                "Pronto."
                        );
                    }
                }
        );


        task.setOnFailed(
                e -> {

                    statusLabel.setText(
                            "Não foi possível verificar yt-dlp e ffmpeg."
                    );

                }
        );


        Thread thread =
                new Thread(
                        task,
                        "check-binaries"
                );


        thread.setDaemon(true);

        thread.start();
    }


    // =====================================================
    // BOTÃO BUSCAR FORMATOS
    // =====================================================

    @FXML
    private void onListFormats() {

        buscarVideo();
    }


    // =====================================================
    // BUSCAR VÍDEO + THUMBNAIL + FORMATOS
    // =====================================================

    private void buscarVideo() {

        String url =
                urlField.getText().trim();


        if (url.isEmpty()) {

            return;
        }


        // -------------------------------------------------
        // Validar URL
        // -------------------------------------------------

        if (!YOUTUBE_URL_PATTERN
                .matcher(url)
                .matches()) {

            limparPreview();

            downloadButton
                    .setDisable(true);

            statusLabel.setText(
                    "Isso não parece um link válido do YouTube."
            );

            return;
        }


        // -------------------------------------------------
        // ID desta busca
        // -------------------------------------------------

        final long buscaAtual =
                idDaBusca.incrementAndGet();


        // -------------------------------------------------
        // Status
        // -------------------------------------------------

        statusLabel.setText(
                "Consultando vídeo..."
        );


        limparPreview();


        // -------------------------------------------------
        // Travar interface
        // -------------------------------------------------

        travarInterface(true);


        // -------------------------------------------------
        // Task
        // -------------------------------------------------

        Task<VideoDetails> task =
                new Task<>() {

                    @Override
                    protected VideoDetails call()
                            throws Exception {

                        return ytDlpService
                                .getVideoDetails(url);
                    }
                };


        // -------------------------------------------------
        // Sucesso
        // -------------------------------------------------

        task.setOnSucceeded(
                e -> {

                    /*
                     * Se uma busca mais nova já existe,
                     * ignoramos o resultado desta.
                     */
                    if (buscaAtual
                            != idDaBusca.get()) {

                        return;
                    }


                    VideoDetails details =
                            task.getValue();


                    // -------------------------------------
                    // FORMATOS
                    // -------------------------------------

                    todosFormatos =
                            details.formats();


                    atualizarComboFiltrado();


                    // -------------------------------------
                    // PREVIEW
                    // -------------------------------------

                    mostrarVideo(
                            details
                    );


                    // -------------------------------------
                    // LIBERAR INTERFACE
                    // -------------------------------------

                    travarInterface(false);


                    /*
                     * Download só pode ser usado quando
                     * existe pelo menos um formato.
                     */
                    downloadButton.setDisable(
                            todosFormatos.isEmpty()
                    );


                    // -------------------------------------
                    // STATUS
                    // -------------------------------------

                    if (todosFormatos.isEmpty()) {

                        statusLabel.setText(
                                "Vídeo carregado, mas nenhum formato foi encontrado."
                        );

                    } else {

                        statusLabel.setText(
                                "Vídeo carregado • "
                                        + todosFormatos.size()
                                        + " formatos encontrados."
                        );
                    }
                }
        );


        // -------------------------------------------------
        // Erro
        // -------------------------------------------------

        task.setOnFailed(
                e -> {

                    /*
                     * Não mostra o erro de uma busca antiga.
                     */
                    if (buscaAtual
                            != idDaBusca.get()) {

                        return;
                    }


                    limparPreview();


                    todosFormatos =
                            List.of();


                    formatComboBox
                            .getItems()
                            .clear();


                    downloadButton
                            .setDisable(true);


                    statusLabel.setText(
                            mapearErro(
                                    task.getException()
                            )
                    );


                    travarInterface(false);
                }
        );


        // -------------------------------------------------
        // Thread
        // -------------------------------------------------

        Thread thread =
                new Thread(
                        task,
                        "video-info"
                );


        thread.setDaemon(true);

        thread.start();
    }


    // =====================================================
    // MOSTRAR PREVIEW
    // =====================================================

    private void mostrarVideo(
            VideoDetails details) {

        // -------------------------------------------------
        // Título
        // -------------------------------------------------

        videoTitleLabel.setText(
                details.title()
        );


        // -------------------------------------------------
        // Canal
        // -------------------------------------------------

        videoChannelLabel.setText(
                details.uploader()
        );


        // -------------------------------------------------
        // Thumbnail
        // -------------------------------------------------

        String thumbnailUrl =
                details.thumbnailUrl();


        if (thumbnailUrl == null
                || thumbnailUrl.isBlank()) {

            thumbnailImage.setImage(
                    null
            );

        } else {

            Image image =
                    new Image(
                            thumbnailUrl,
                            150,
                            85,
                            true,
                            true,
                            true
                    );


            thumbnailImage.setImage(
                    image
            );


            image.errorProperty()
                    .addListener(
                            (obs,
                             oldValue,
                             error) -> {

                                if (error) {

                                    Platform.runLater(
                                            () ->
                                                    thumbnailImage
                                                            .setImage(
                                                                    null
                                                            )
                                    );
                                }
                            }
                    );
        }


        // -------------------------------------------------
        // Mostrar preview
        // -------------------------------------------------

        videoPreviewBox.setManaged(
                true
        );


        videoPreviewBox.setVisible(
                true
        );
    }


    // =====================================================
    // LIMPAR PREVIEW
    // =====================================================

    private void limparPreview() {

        if (thumbnailImage != null) {

            thumbnailImage.setImage(
                    null
            );
        }


        if (videoTitleLabel != null) {

            videoTitleLabel.setText(
                    ""
            );
        }


        if (videoChannelLabel != null) {

            videoChannelLabel.setText(
                    ""
            );
        }


        if (videoPreviewBox != null) {

            videoPreviewBox.setManaged(
                    false
            );


            videoPreviewBox.setVisible(
                    false
            );
        }
    }


    // =====================================================
    // FILTRAR FORMATOS
    // =====================================================

    private void atualizarComboFiltrado() {

        boolean queroAudio =
                mp3Radio.isSelected();


        List<VideoFormat> filtrados;


        // =================================================
        // MP3
        // =================================================

        if (queroAudio) {

            /*
             * MP3:
             * somente formatos de áudio.
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
             * somente formatos que possuem vídeo
             * e são originalmente MP4.
             */
            filtrados =
                    todosFormatos.stream()
                            .filter(
                                    f ->
                                            f.hasVideo()
                                                    && f.getExt() != null
                                                    && f.getExt()
                                                    .equalsIgnoreCase(
                                                            "mp4"
                                                    )
                            )
                            .collect(
                                    Collectors.toList()
                            );
        }


        // =================================================
        // Atualizar Combo
        // =================================================

        formatComboBox
                .getItems()
                .setAll(
                        filtrados
                );


        // =================================================
        // Selecionar primeiro
        // =================================================

        if (!filtrados.isEmpty()) {

            formatComboBox
                    .getSelectionModel()
                    .selectFirst();
        }


        // =================================================
        // Estado do Download
        // =================================================

        downloadButton.setDisable(
                filtrados.isEmpty()
        );
    }


    // =====================================================
    // TRAVAR INTERFACE
    // =====================================================

    private void travarInterface(
            boolean travar) {

        urlField.setDisable(
                travar
        );


        listFormatsButton.setDisable(
                travar
        );


        downloadButton.setDisable(
                travar
        );


        mp3Radio.setDisable(
                travar
        );


        mp4Radio.setDisable(
                travar
        );


        formatComboBox.setDisable(
                travar
        );
    }


    // =====================================================
    // DOWNLOAD
    // =====================================================

    @FXML
    private void onDownload() {

        String url =
                urlField.getText().trim();


        // =================================================
        // Validar URL
        // =================================================

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


        // =================================================
        // Tipo de saída
        // =================================================

        OutputType outputType =
                mp3Radio.isSelected()
                        ? OutputType.MP3
                        : OutputType.MP4;


        // =================================================
        // Formato selecionado
        // =================================================

        VideoFormat selectedFormat =
                formatComboBox
                        .getSelectionModel()
                        .getSelectedItem();


        if (selectedFormat == null) {

            statusLabel.setText(
                    "Nenhum formato disponível para download."
            );

            return;
        }


        // =================================================
        // Escolher pasta
        // =================================================

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


        // =================================================
        // Preparar download
        // =================================================

        cancelamentoSolicitado =
                false;


        processoDownloadAtual =
                null;


        travarInterface(true);


        cancelButton.setDisable(
                true
        );


        progressBar.setProgress(
                0
        );


        statusLabel.setText(
                "Baixando..."
        );


        // =================================================
        // Task
        // =================================================

        Task<Integer> task =
                new Task<>() {

                    @Override
                    protected Integer call()
                            throws Exception {

                        return ytDlpService.download(
                                url,
                                selectedFormat,
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

                                process -> {

                                    processoDownloadAtual =
                                            process;


                                    Platform.runLater(
                                            () ->
                                                    cancelButton
                                                            .setDisable(
                                                                    false
                                                            )
                                    );
                                }
                        );
                    }
                };


        // =================================================
        // Download concluído
        // =================================================

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


        // =================================================
        // Download falhou
        // =================================================

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


        // =================================================
        // Thread
        // =================================================

        Thread thread =
                new Thread(
                        task,
                        "download"
                );


        thread.setDaemon(true);

        thread.start();
    }


    // =====================================================
    // CANCELAR DOWNLOAD
    // =====================================================

    @FXML
    private void onCancelarDownload() {

        cancelamentoSolicitado =
                true;


        Process processo =
                processoDownloadAtual;


        if (processo == null
                || !processo.isAlive()) {

            return;
        }


        statusLabel.setText(
                "Cancelando..."
        );


        ytDlpService.cancelarDownload(
                processo
        );
    }


    // =====================================================
    // FINALIZAR DOWNLOAD
    // =====================================================

    private void finalizarDownload() {

        processoDownloadAtual =
                null;


        cancelButton.setDisable(
                true
        );


        travarInterface(
                false
        );


        /*
         * Depois do download, só habilita
         * novamente se realmente houver formato.
         */
        downloadButton.setDisable(
                formatComboBox
                        .getItems()
                        .isEmpty()
        );
    }


    // =====================================================
    // MAPEAR ERROS
    // =====================================================

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


        String lower =
                mensagem.toLowerCase();


        // =================================================
        // VÍDEO PRIVADO
        // =================================================

        if (mensagem.contains(
                "Private video")) {

            return "Este vídeo é privado e não pode ser baixado.";
        }


        // =================================================
        // VÍDEO INDISPONÍVEL
        // =================================================

        if (mensagem.contains(
                "Video unavailable")
                || mensagem.contains(
                "This video is unavailable")) {

            return "Vídeo indisponível.";
        }


        // =================================================
        // RESTRIÇÃO DE IDADE
        // =================================================

        if (mensagem.contains(
                "Sign in to confirm your age")) {

            return "Este vídeo tem restrição de idade.";
        }


        // =================================================
        // INTERNET
        // =================================================

        if (lower.contains(
                "temporary failure")
                || lower.contains(
                "name or service not known")
                || lower.contains(
                "network is unreachable")
                || lower.contains(
                "connection timed out")
                || lower.contains(
                "timed out")) {

            return "Não foi possível conectar ao YouTube.";
        }


        // =================================================
        // TIMEOUT
        // =================================================

        if (lower.contains(
                "demorou demais")) {

            return "A consulta demorou demais. Verifique sua conexão e tente novamente.";
        }


        // =================================================
        // LINK INCOMPLETO
        // =================================================

        if (mensagem.contains(
                "Incomplete YouTube ID")
                || mensagem.contains(
                "looks truncated")) {

            return "O link parece estar incompleto ou incorreto.";
        }


        // =================================================
        // FORMATO INDISPONÍVEL
        // =================================================

        if (lower.contains(
                "requested format is not available")) {

            return "O formato selecionado não está disponível para este vídeo.";
        }


        // =================================================
        // ERRO GENÉRICO
        // =================================================

        return "Erro: " + mensagem;
    }
}