package com.seuapp.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuapp.core.model.OutputType;
import com.seuapp.core.model.VideoFormat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class YtDlpService {

    // =====================================================
    // PROGRESSO
    // =====================================================

    private static final Pattern PROGRESS_PATTERN =
            Pattern.compile(
                    "\\[download\\]\\s+(\\d{1,3}(?:\\.\\d+)?)%"
            );

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper();

    private static final long INFO_TIMEOUT_SECONDS = 60;

    private static final long DOWNLOAD_TIMEOUT_MINUTES = 30;

    private static final long SOCKET_TIMEOUT_SECONDS = 20;


    // =====================================================
    // BINÁRIOS
    // =====================================================

    private final String ytDlpBinary;

    private final String ffmpegBinary;


    // =====================================================
    // CONSTRUTOR
    // =====================================================

    public YtDlpService(
            String ytDlpBinary,
            String ffmpegBinary) {

        this.ytDlpBinary =
                ytDlpBinary;

        this.ffmpegBinary =
                ffmpegBinary;
    }


    // =====================================================
    // DADOS DO VÍDEO
    // =====================================================

    public record VideoDetails(
            String id,
            String title,
            String uploader,
            String thumbnailUrl,
            List<VideoFormat> formats
    ) {
    }


    // =====================================================
    // LOCALIZAÇÃO DOS BINÁRIOS
    // =====================================================

    public static YtDlpService withDefaultLocations() {

        Path appDir =
                PlatformUtils.applicationDirectory(
                        YtDlpService.class
                );

        Path ytDlpPath =
                PlatformUtils.findTool(
                        appDir,
                        "yt-dlp"
                );

        Path ffmpegPath =
                PlatformUtils.findTool(
                        appDir,
                        "ffmpeg"
                );

        String ytDlp =
                ytDlpPath != null
                        ? ytDlpPath.toString()
                        : PlatformUtils.ytDlpFileName();

        String ffmpeg =
                ffmpegPath != null
                        ? ffmpegPath.toString()
                        : PlatformUtils.ffmpegFileName();

        System.out.println(
                "Sistema: "
                        + System.getProperty("os.name")
        );

        System.out.println(
                "Arquitetura: "
                        + PlatformUtils.architectureFolder()
        );

        System.out.println(
                "Diretório da aplicação: "
                        + appDir
        );

        System.out.println(
                "yt-dlp: "
                        + ytDlp
        );

        System.out.println(
                "ffmpeg: "
                        + ffmpeg
        );

        return new YtDlpService(
                ytDlp,
                ffmpeg
        );
    }


    // =====================================================
    // COMPATIBILIDADE COM A VERSÃO ANTIGA
    // =====================================================

    public static YtDlpService withDefaultLocations(
            Path ignored) {

        return withDefaultLocations();
    }


    // =====================================================
    // LISTAR FORMATOS
    // =====================================================

    public List<VideoFormat> listFormats(
            String url)
            throws IOException, InterruptedException {

        return getVideoDetails(
                url
        ).formats();
    }


    // =====================================================
    // BUSCAR DADOS COMPLETOS DO VÍDEO
    // =====================================================

    public VideoDetails getVideoDetails(
            String url)
            throws IOException, InterruptedException {

        // =================================================
        // PROCESSO
        // =================================================

        ProcessBuilder pb =
                new ProcessBuilder(
                        ytDlpBinary,

                        "--ignore-config",

                        "--no-playlist",

                        "--socket-timeout",
                        String.valueOf(
                                SOCKET_TIMEOUT_SECONDS
                        ),

                        "-J",

                        "--no-warnings",

                        url
                );

        pb.redirectErrorStream(false);

        Process process =
                pb.start();


        // =================================================
        // LEITURA DOS STREAMS
        // =================================================

        StringBuilder stdout =
                new StringBuilder();

        StringBuilder stderr =
                new StringBuilder();

        AtomicReference<IOException> stdoutError =
                new AtomicReference<>();

        AtomicReference<IOException> stderrError =
                new AtomicReference<>();

        Thread stdoutThread =
                iniciarLeitor(
                        process.getInputStream(),
                        stdout,
                        stdoutError,
                        "yt-dlp-stdout"
                );

        Thread stderrThread =
                iniciarLeitor(
                        process.getErrorStream(),
                        stderr,
                        stderrError,
                        "yt-dlp-stderr"
                );


        // =================================================
        // TIMEOUT
        // =================================================

        boolean terminou;

        try {

            terminou =
                    process.waitFor(
                            INFO_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    );

        } catch (InterruptedException e) {

            PlatformUtils.destroyProcessTree(
                    process
            );

            aguardarLeitores(
                    stdoutThread,
                    stderrThread
            );

            Thread.currentThread().interrupt();

            throw e;
        }


        if (!terminou) {

            PlatformUtils.destroyProcessTree(
                    process
            );

            aguardarLeitores(
                    stdoutThread,
                    stderrThread
            );

            throw new IOException(
                    "yt-dlp demorou demais para responder."
            );
        }


        aguardarLeitores(
                stdoutThread,
                stderrThread
        );


        // =================================================
        // ERROS DE LEITURA
        // =================================================

        if (stdoutError.get() != null) {

            throw stdoutError.get();
        }

        if (stderrError.get() != null) {

            throw stderrError.get();
        }


        // =================================================
        // EXIT CODE
        // =================================================

        int exit =
                process.exitValue();

        if (exit != 0) {

            String erro =
                    stderr.toString().trim();

            if (erro.isBlank()) {

                erro =
                        "Nenhuma mensagem detalhada foi fornecida.";
            }

            throw new IOException(
                    "yt-dlp falhou ao consultar o vídeo "
                            + "(exit "
                            + exit
                            + "): "
                            + limitarMensagem(
                                    erro,
                                    2000
                            )
            );
        }


        // =================================================
        // JSON
        // =================================================

        JsonNode root =
                OBJECT_MAPPER.readTree(
                        stdout.toString()
                );


        // =================================================
        // ID
        // =================================================

        String id =
                textOrNull(
                        root,
                        "id"
                );


        // =================================================
        // TÍTULO
        // =================================================

        String title =
                textOrNull(
                        root,
                        "title"
                );

        if (title == null
                || title.isBlank()) {

            title =
                    "Vídeo sem título";
        }


        // =================================================
        // CANAL
        // =================================================

        String uploader =
                textOrNull(
                        root,
                        "uploader"
                );

        if (uploader == null
                || uploader.isBlank()) {

            uploader =
                    "Canal desconhecido";
        }


        // =================================================
        // THUMBNAIL
        // =================================================

        String thumbnailUrl =
                textOrNull(
                        root,
                        "thumbnail"
                );

        /*
         * Em algumas respostas do yt-dlp,
         * caso thumbnail não exista, usamos
         * o ID do vídeo para montar a URL
         * da miniatura.
         */

        if ((thumbnailUrl == null
                || thumbnailUrl.isBlank())
                && id != null
                && !id.isBlank()) {

            thumbnailUrl =
                    "https://i.ytimg.com/vi/"
                            + id
                            + "/hqdefault.jpg";
        }


        // =================================================
        // FORMATOS
        // =================================================

        List<VideoFormat> formats =
                parseFormatsFromJson(
                        root
                );


        // =================================================
        // RETORNO
        // =================================================

        return new VideoDetails(
                id,
                title,
                uploader,
                thumbnailUrl,
                formats
        );
    }


    // =====================================================
    // PARSE DOS FORMATOS
    // =====================================================

    private List<VideoFormat> parseFormatsFromJson(
            JsonNode root) {

        JsonNode formats =
                root.get(
                        "formats"
                );


        List<VideoFormat> result =
                new ArrayList<>();


        if (formats == null
                || !formats.isArray()) {

            return result;
        }


        // =================================================
        // PERCORRER FORMATOS
        // =================================================

        for (JsonNode f : formats) {

            // ---------------------------------------------
            // CODECS
            // ---------------------------------------------

            String vcodec =
                    textOrNull(
                            f,
                            "vcodec"
                    );


            String acodec =
                    textOrNull(
                            f,
                            "acodec"
                    );


            boolean hasVideo =
                    vcodec != null
                            && !"none".equals(
                            vcodec
                    );


            boolean hasAudio =
                    acodec != null
                            && !"none".equals(
                            acodec
                    );


            // ---------------------------------------------
            // IGNORAR FORMATOS VAZIOS
            // ---------------------------------------------

            if (!hasVideo
                    && !hasAudio) {

                continue;
            }


            // ---------------------------------------------
            // ID
            // ---------------------------------------------

            String id =
                    textOrNull(
                            f,
                            "format_id"
                    );


            // ---------------------------------------------
            // EXTENSÃO
            // ---------------------------------------------

            String ext =
                    textOrNull(
                            f,
                            "ext"
                    );


            // ---------------------------------------------
            // RESOLUÇÃO
            // ---------------------------------------------

            Integer height =
                    f.has("height")
                            && !f.get("height")
                            .isNull()
                            ? f.get("height")
                            .asInt()
                            : null;


            // ---------------------------------------------
            // BITRATE DE ÁUDIO
            // ---------------------------------------------

            Double abr =
                    f.has("abr")
                            && !f.get("abr")
                            .isNull()
                            ? f.get("abr")
                            .asDouble()
                            : null;


            // ---------------------------------------------
            // TAMANHO
            // ---------------------------------------------

            Double sizeMb =
                    null;


            if (f.has("filesize")
                    && !f.get("filesize")
                    .isNull()) {

                sizeMb =
                        f.get(
                                "filesize"
                        ).asDouble()
                                / (
                                1024.0
                                        * 1024.0
                        );

            } else if (
                    f.has("filesize_approx")
                            && !f.get(
                            "filesize_approx"
                    ).isNull()) {

                sizeMb =
                        f.get(
                                "filesize_approx"
                        ).asDouble()
                                / (
                                1024.0
                                        * 1024.0
                        );
            }


            // ---------------------------------------------
            // DESCARTAR VÍDEO SEM RESOLUÇÃO
            // ---------------------------------------------

            if (hasVideo
                    && height == null) {

                continue;
            }


            // ---------------------------------------------
            // LABEL
            // ---------------------------------------------

            String label =
                    buildLabel(
                            ext,
                            height,
                            abr,
                            hasVideo,
                            hasAudio,
                            sizeMb
                    );


            // ---------------------------------------------
            // OBJETO
            // ---------------------------------------------

            result.add(
                    new VideoFormat(
                            id,
                            ext,
                            height,
                            abr,
                            hasVideo,
                            hasAudio,
                            sizeMb,
                            label
                    )
            );
        }


        // =================================================
        // ORDENAR
        // =================================================

        result.sort(
                (a, b) -> {

                    // -------------------------------------
                    // ÁUDIO
                    // -------------------------------------

                    if (a.isAudioOnly()
                            && b.isAudioOnly()) {

                        double abrA =
                                a.getAudioBitrateKbps() != null
                                        ? a.getAudioBitrateKbps()
                                        : 0;


                        double abrB =
                                b.getAudioBitrateKbps() != null
                                        ? b.getAudioBitrateKbps()
                                        : 0;


                        return Double.compare(
                                abrB,
                                abrA
                        );
                    }


                    // -------------------------------------
                    // VÍDEO
                    // -------------------------------------

                    int heightA =
                            a.getHeight() != null
                                    ? a.getHeight()
                                    : 0;


                    int heightB =
                            b.getHeight() != null
                                    ? b.getHeight()
                                    : 0;


                    return Integer.compare(
                            heightB,
                            heightA
                    );
                }
        );


        return result;
    }


    // =====================================================
    // LABEL DOS FORMATOS
    // =====================================================

    private String buildLabel(
            String ext,
            Integer height,
            Double abr,
            boolean hasVideo,
            boolean hasAudio,
            Double sizeMb) {

        StringBuilder sb =
                new StringBuilder();


        // =================================================
        // ÁUDIO
        // =================================================

        if (!hasVideo
                && hasAudio) {

            if (abr != null) {

                sb.append(
                        Math.round(abr)
                ).append(
                        " kbps"
                );

            } else {

                sb.append(
                        "Áudio"
                );
            }


        } else {

            // =============================================
            // VÍDEO
            // =============================================

            sb.append(
                    height != null
                            ? height + "p"
                            : "Vídeo"
            );


            if (hasAudio) {

                sb.append(
                        " (vídeo + áudio)"
                );

            } else {

                sb.append(
                        " (vídeo)"
                );
            }
        }


        // =================================================
        // EXTENSÃO
        // =================================================

        sb.append(
                " - "
        );


        sb.append(
                ext != null
                        ? ext.toUpperCase(
                        Locale.ROOT
                )
                        : "?"
        );


        // =================================================
        // TAMANHO
        // =================================================

        if (sizeMb != null) {

            sb.append(
                    String.format(
                            Locale.ROOT,
                            " (~%.1f MB)",
                            sizeMb
                    )
            );
        }


        return sb.toString();
    }


    // =====================================================
    // LER CAMPO DO JSON
    // =====================================================

    private String textOrNull(
            JsonNode node,
            String field) {

        return node.has(field)
                && !node.get(
                field
        ).isNull()

                ? node.get(
                field
        ).asText()

                : null;
    }


    // =====================================================
    // DOWNLOAD
    // =====================================================

    public int download(
            String url,
            VideoFormat selectedFormat,
            OutputType outputType,
            Path outputDir,
            Consumer<Double> progressListener,
            Consumer<Process> onProcessoIniciado)
            throws IOException, InterruptedException {


        // =================================================
        // CRIAR DIRETÓRIO
        // =================================================

        Files.createDirectories(
                outputDir
        );


        // =================================================
        // TEMP
        // =================================================

        Path tempDir =
                outputDir.resolve(
                        ".yt-dlp-temp"
                );


        apagarDiretorio(
                tempDir
        );


        Files.createDirectories(
                tempDir
        );


        // =================================================
        // COMANDO
        // =================================================

        List<String> command =
                new ArrayList<>();


        command.add(
                ytDlpBinary
        );


        command.add(
                "--ignore-config"
        );


        command.add(
                "--no-playlist"
        );


        command.add(
                "--newline"
        );


        command.add(
                "--socket-timeout"
        );


        command.add(
                String.valueOf(
                        SOCKET_TIMEOUT_SECONDS
                )
        );


        command.add(
                "--ffmpeg-location"
        );


        command.add(
                ffmpegBinary
        );


        // =================================================
        // DIRETÓRIO FINAL
        // =================================================

        command.add(
                "--paths"
        );


        command.add(
                "home:"
                        + outputDir
                        .toAbsolutePath()
        );


        // =================================================
        // DIRETÓRIO TEMPORÁRIO
        // =================================================

        command.add(
                "--paths"
        );


        command.add(
                "temp:"
                        + tempDir
                        .toAbsolutePath()
        );


        // =================================================
        // NOME DO ARQUIVO
        // =================================================

        command.add(
                "-o"
        );


        command.add(
                "%(title)s [%(id)s].%(ext)s"
        );


        // =================================================
        // MP3
        // =================================================

        if (outputType == OutputType.MP3) {

            command.add(
                    "-f"
            );


            command.add(
                    selectedFormat != null
                            ? selectedFormat.getFormatId()
                            : "ba"
            );


            command.add(
                    "--extract-audio"
            );


            command.add(
                    "--audio-format"
            );


            command.add(
                    "mp3"
            );


            command.add(
                    "--audio-quality"
            );


            command.add(
                    "0"
            );


        } else {

            // =================================================
            // MP4
            // =================================================

            String formatExpression;


            if (selectedFormat == null) {

                /*
                 * Sem escolha específica:
                 *
                 * 1. vídeo MP4 + áudio M4A
                 * 2. MP4 único
                 * 3. fallback geral
                 */

                formatExpression =
                        "bv*[ext=mp4]"
                                + "+ba[ext=m4a]"
                                + "/b[ext=mp4]"
                                + "/bv*+ba/b";


            } else if (
                    selectedFormat.isVideoOnly()) {

                /*
                 * Vídeo sem áudio:
                 *
                 * mantém exatamente o vídeo escolhido
                 * + melhor áudio M4A.
                 */

                formatExpression =
                        selectedFormat.getFormatId()
                                + "+ba[ext=m4a]/ba";


            } else {

                /*
                 * O formato escolhido já possui áudio.
                 *
                 * Portanto não adicionamos outro áudio.
                 */

                formatExpression =
                        selectedFormat.getFormatId();
            }


            command.add(
                    "-f"
            );


            command.add(
                    formatExpression
            );


            // =================================================
            // MERGE MP4
            // =================================================

            command.add(
                    "--merge-output-format"
            );


            command.add(
                    "mp4"
            );
        }


        // =================================================
        // URL
        // =================================================

        command.add(
                url
        );


        // =================================================
        // PROCESSO
        // =================================================

        ProcessBuilder pb =
                new ProcessBuilder(
                        command
                );


        pb.redirectErrorStream(
                true
        );


        Process process =
                pb.start();


        // =================================================
        // AVISAR CONTROLLER
        // =================================================

        if (onProcessoIniciado != null) {

            onProcessoIniciado.accept(
                    process
            );
        }


        // =================================================
        // LEITOR DE PROGRESSO
        // =================================================

        AtomicReference<IOException> readerError =
                new AtomicReference<>();


        Thread outputReader =
                iniciarLeitorProgresso(
                        process.getInputStream(),
                        progressListener,
                        readerError
                );


        int exitCode;


        try {

            // =================================================
            // ESPERAR TERMINAR
            // =================================================

            boolean terminou;

            try {

                terminou =
                        process.waitFor(
                                DOWNLOAD_TIMEOUT_MINUTES,
                                TimeUnit.MINUTES
                        );

            } catch (InterruptedException e) {

                PlatformUtils.destroyProcessTree(
                        process
                );

                aguardarLeitores(
                        outputReader
                );

                Thread.currentThread().interrupt();

                throw e;
            }


            if (!terminou) {

                PlatformUtils.destroyProcessTree(
                        process
                );

                aguardarLeitores(
                        outputReader
                );

                throw new IOException(
                        "Download demorou demais e foi encerrado."
                );
            }


            // =================================================
            // FINALIZAR LEITOR
            // =================================================

            aguardarLeitores(
                    outputReader
            );


            // =================================================
            // ERRO DE LEITURA
            // =================================================

            if (readerError.get() != null) {

                throw readerError.get();
            }


            // =================================================
            // EXIT CODE
            // =================================================

            exitCode =
                    process.exitValue();

        } finally {

            /*
             * Segurança adicional:
             * se alguma exceção acontecer e o processo
             * ainda estiver vivo, encerramos a árvore.
             */

            if (process.isAlive()) {

                PlatformUtils.destroyProcessTree(
                        process
                );
            }


            // =================================================
            // LIMPAR TEMP
            // =================================================

            apagarDiretorio(
                    tempDir
            );
        }


        return exitCode;
    }


    // =====================================================
    // CANCELAR DOWNLOAD
    // =====================================================

    public void cancelarDownload(
            Process process) {

        if (process == null) {

            return;
        }


        if (process.isAlive()) {

            PlatformUtils.destroyProcessTree(
                    process
            );
        }
    }


    // =====================================================
    // LEITOR DE STREAM
    // =====================================================

    private Thread iniciarLeitor(
            InputStream inputStream,
            StringBuilder destino,
            AtomicReference<IOException> erro,
            String nomeThread) {

        Thread thread =
                new Thread(
                        () -> {

                            try (
                                    BufferedReader reader =
                                            new BufferedReader(
                                                    new InputStreamReader(
                                                            inputStream,
                                                            StandardCharsets.UTF_8
                                                    )
                                            )
                            ) {

                                String line;

                                while (
                                        (line =
                                                reader.readLine())
                                                != null
                                ) {

                                    destino
                                            .append(line)
                                            .append('\n');
                                }

                            } catch (IOException e) {

                                erro.set(
                                        e
                                );
                            }
                        },
                        nomeThread
                );


        thread.setDaemon(
                true
        );


        thread.start();


        return thread;
    }


    // =====================================================
    // LEITOR DE PROGRESSO
    // =====================================================

    private Thread iniciarLeitorProgresso(
            InputStream inputStream,
            Consumer<Double> progressListener,
            AtomicReference<IOException> erro) {

        Thread thread =
                new Thread(
                        () -> {

                            try (
                                    BufferedReader reader =
                                            new BufferedReader(
                                                    new InputStreamReader(
                                                            inputStream,
                                                            StandardCharsets.UTF_8
                                                    )
                                            )
                            ) {

                                String line;

                                while (
                                        (line =
                                                reader.readLine())
                                                != null
                                ) {

                                    if (progressListener == null) {

                                        continue;
                                    }


                                    Matcher matcher =
                                            PROGRESS_PATTERN.matcher(
                                                    line
                                            );


                                    if (matcher.find()) {

                                        double percent =
                                                Double.parseDouble(
                                                        matcher.group(1)
                                                );


                                        progressListener.accept(
                                                percent
                                        );
                                    }
                                }

                            } catch (IOException e) {

                                erro.set(
                                        e
                                );
                            }
                        },
                        "yt-dlp-progress-reader"
                );


        thread.setDaemon(
                true
        );


        thread.start();


        return thread;
    }


    // =====================================================
    // AGUARDAR LEITORES
    // =====================================================

    private void aguardarLeitores(
            Thread... threads) {

        boolean interrompido =
                false;


        for (Thread thread : threads) {

            if (thread == null) {

                continue;
            }


            try {

                thread.join(
                        2000
                );

            } catch (InterruptedException e) {

                interrompido =
                        true;
            }
        }


        if (interrompido) {

            Thread.currentThread().interrupt();
        }
    }


    // =====================================================
    // LIMITAR MENSAGEM
    // =====================================================

    private String limitarMensagem(
            String mensagem,
            int maxCaracteres) {

        if (mensagem == null) {

            return "";
        }


        String texto =
                mensagem.trim();


        if (texto.length()
                <= maxCaracteres) {

            return texto;
        }


        return texto.substring(
                0,
                maxCaracteres
        ) + "...";
    }


    // =====================================================
    // APAGAR DIRETÓRIO
    // =====================================================

    private void apagarDiretorio(
            Path diretorio) {

        if (diretorio == null
                || !Files.exists(
                diretorio
        )) {

            return;
        }


        try (
                Stream<Path> paths =
                        Files.walk(
                                diretorio
                        )
        ) {

            paths
                    .sorted(
                            Comparator.reverseOrder()
                    )
                    .forEach(
                            path -> {

                                try {

                                    Files.deleteIfExists(
                                            path
                                    );

                                } catch (IOException e) {

                                    /*
                                     * Falha de limpeza não deve
                                     * derrubar a aplicação.
                                     */
                                }
                            }
                    );


        } catch (IOException e) {

            /*
             * Falha de limpeza não deve
             * derrubar a aplicação.
             */
        }
    }


    // =====================================================
    // TESTAR BINÁRIOS
    // =====================================================

    public boolean binariosDisponiveis() {

        return testarBinario(
                ytDlpBinary,
                "--version"
        )
                &&
                testarBinario(
                        ffmpegBinary,
                        "-version"
                );
    }


    // =====================================================
    // TESTAR BINÁRIO INDIVIDUAL
    // =====================================================

    private boolean testarBinario(
            String binario,
            String argumento) {

        try {

            Process p =
                    new ProcessBuilder(
                            binario,
                            argumento
                    )
                            .redirectErrorStream(
                                    true
                            )
                            .start();


            boolean terminou =
                    p.waitFor(
                            10,
                            TimeUnit.SECONDS
                    );


            if (!terminou) {

                p.destroyForcibly();

                return false;
            }


            return p.exitValue() == 0;


        } catch (IOException e) {

            return false;


        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            return false;
        }
    }
}