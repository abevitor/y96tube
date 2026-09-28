package com.seuapp.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuapp.core.model.OutputType;
import com.seuapp.core.model.VideoFormat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
                        + System.getProperty(
                        "os.name"
                )
        );


        System.out.println(
                "Arquitetura: "
                        + PlatformUtils
                                .architectureFolder()
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

                        "-J",

                        "--no-warnings",

                        url
                );


        pb.redirectErrorStream(
                false
        );


        Process process =
                pb.start();


        // =================================================
        // SAÍDA
        // =================================================

        String json =
                readAll(
                        process.getInputStream()
                );


        String errOutput =
                readAll(
                        process.getErrorStream()
                );


        // =================================================
        // TIMEOUT
        // =================================================

        boolean terminou =
                process.waitFor(
                        60,
                        java.util.concurrent.TimeUnit.SECONDS
                );


        if (!terminou) {

            PlatformUtils.destroyProcessTree(
                    process
            );


            throw new IOException(
                    "yt-dlp demorou demais para responder."
            );
        }


        // =================================================
        // EXIT CODE
        // =================================================

        int exit =
                process.exitValue();


        if (exit != 0) {

            throw new IOException(
                    "yt-dlp falhou ao consultar o vídeo "
                            + "(exit "
                            + exit
                            + "): "
                            + errOutput
            );
        }


        // =================================================
        // JSON
        // =================================================

        ObjectMapper mapper =
                new ObjectMapper();


        JsonNode root =
                mapper.readTree(
                        json
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
                            && !f.get(
                            "height"
                    ).isNull()
                            ? f.get(
                            "height"
                    ).asInt()
                            : null;


            // ---------------------------------------------
            // BITRATE DE ÁUDIO
            // ---------------------------------------------

            Double abr =
                    f.has("abr")
                            && !f.get(
                            "abr"
                    ).isNull()
                            ? f.get(
                            "abr"
                    ).asDouble()
                            : null;


            // ---------------------------------------------
            // TAMANHO
            // ---------------------------------------------

            Double sizeMb =
                    null;


            if (f.has("filesize")
                    && !f.get(
                    "filesize"
            ).isNull()) {

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


            // =================================================
            // REMUX MP4
            // =================================================

            command.add(
                    "--remux-video"
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
        // DEBUG
        // =================================================

        System.out.println(
                "Executando:"
        );


        System.out.println(
                String.join(
                        " ",
                        command
                )
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
        // LER PROGRESSO
        // =================================================

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream(),
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


        } finally {

            // =================================================
            // ESPERAR TERMINAR
            // =================================================

            boolean terminou =
                    process.waitFor(
                            30,
                            java.util.concurrent.TimeUnit.MINUTES
                    );


            if (!terminou) {

                PlatformUtils
                        .destroyProcessTree(
                                process
                        );


                apagarDiretorio(
                        tempDir
                );


                throw new IOException(
                        "Download demorou demais e foi encerrado."
                );
            }


            // =================================================
            // LIMPAR TEMP
            // =================================================

            apagarDiretorio(
                    tempDir
            );
        }


        // =================================================
        // RETORNO
        // =================================================

        return process.exitValue();
    }


    // =====================================================
    // CANCELAR DOWNLOAD
    // =====================================================

    public void cancelarDownload(
            Process process) {

        if (process == null) {

            return;
        }


        PlatformUtils.destroyProcessTree(
                process
        );
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


                                } catch (
                                        IOException e) {

                                    System.out.println(
                                            "Não foi possível apagar: "
                                                    + path
                                    );
                                }
                            }
                    );


        } catch (IOException e) {

            System.out.println(
                    "Erro limpando diretório temporário: "
                            + e.getMessage()
            );
        }
    }


    // =====================================================
    // LER INPUT STREAM
    // =====================================================

    private String readAll(
            java.io.InputStream inputStream)
            throws IOException {

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        inputStream,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            StringBuilder sb =
                    new StringBuilder();


            String line;


            while (
                    (line =
                            reader.readLine())
                            != null
            ) {

                sb.append(
                        line
                ).append(
                        '\n'
                );
            }


            return sb.toString();
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
                            java.util.concurrent.TimeUnit.SECONDS
                    );


            if (!terminou) {

                p.destroyForcibly();

                return false;
            }


            return p.exitValue() == 0;


        } catch (IOException e) {

            System.out.println(
                    "Erro ao executar "
                            + binario
                            + ": "
                            + e.getMessage()
            );


            return false;


        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            return false;
        }
    }
}