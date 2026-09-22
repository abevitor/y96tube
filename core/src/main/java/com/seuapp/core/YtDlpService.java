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
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class YtDlpService {

    private static final Pattern PROGRESS_PATTERN =
            Pattern.compile(
                    "\\[download\\]\\s+(\\d{1,3}(?:\\.\\d+)?)%"
            );

    private final String ytDlpBinary;
    private final String ffmpegBinary;

    public YtDlpService(
            String ytDlpBinary,
            String ffmpegBinary) {

        this.ytDlpBinary = ytDlpBinary;
        this.ffmpegBinary = ffmpegBinary;
    }

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

    /*
     * Mantemos esse método caso algum outro código seu
     * ainda esteja chamando a versão antiga.
     */
    public static YtDlpService withDefaultLocations(
            Path ignored) {

        return withDefaultLocations();
    }

    public List<VideoFormat> listFormats(
            String url)
            throws IOException, InterruptedException {

        ProcessBuilder pb =
                new ProcessBuilder(
                        ytDlpBinary,
                        "--ignore-config",
                        "--no-playlist",
                        "-J",
                        "--no-warnings",
                        url
                );

        pb.redirectErrorStream(false);

        Process process = pb.start();

        String json =
                readAll(
                        process.getInputStream()
                );

        String errOutput =
                readAll(
                        process.getErrorStream()
                );

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

        int exit =
                process.exitValue();

        if (exit != 0) {

            throw new IOException(
                    "yt-dlp falhou ao consultar formatos "
                            + "(exit "
                            + exit
                            + "): "
                            + errOutput
            );
        }

        return parseFormats(json);
    }

    private List<VideoFormat> parseFormats(
            String json)
            throws IOException {

        ObjectMapper mapper =
                new ObjectMapper();

        JsonNode root =
                mapper.readTree(json);

        JsonNode formats =
                root.get("formats");

        List<VideoFormat> result =
                new ArrayList<>();

        if (formats == null
                || !formats.isArray()) {

            return result;
        }

        for (JsonNode f : formats) {

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
                            && !"none".equals(vcodec);

            boolean hasAudio =
                    acodec != null
                            && !"none".equals(acodec);

            /*
             * Ignora formatos sem vídeo e sem áudio.
             */
            if (!hasVideo && !hasAudio) {
                continue;
            }

            String id =
                    textOrNull(
                            f,
                            "format_id"
                    );

            String ext =
                    textOrNull(
                            f,
                            "ext"
                    );

            Integer height =
                    f.has("height")
                            && !f.get("height").isNull()
                            ? f.get("height").asInt()
                            : null;

            Double abr =
                    f.has("abr")
                            && !f.get("abr").isNull()
                            ? f.get("abr").asDouble()
                            : null;

            Double sizeMb =
                    null;

            if (f.has("filesize")
                    && !f.get("filesize").isNull()) {

                sizeMb =
                        f.get("filesize").asDouble()
                                / (1024.0 * 1024.0);

            } else if (
                    f.has("filesize_approx")
                            && !f.get("filesize_approx").isNull()) {

                sizeMb =
                        f.get("filesize_approx").asDouble()
                                / (1024.0 * 1024.0);
            }

            /*
             * Formato de vídeo sem resolução
             * não é útil para nosso seletor.
             */
            if (hasVideo && height == null) {
                continue;
            }

            String label =
                    buildLabel(
                            ext,
                            height,
                            abr,
                            hasVideo,
                            hasAudio,
                            sizeMb
                    );

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

        result.sort(
                (a, b) -> {

                    /*
                     * Áudio:
                     * maior bitrate primeiro.
                     */
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

                    /*
                     * Vídeo:
                     * maior resolução primeiro.
                     */
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

    private String buildLabel(
            String ext,
            Integer height,
            Double abr,
            boolean hasVideo,
            boolean hasAudio,
            Double sizeMb) {

        StringBuilder sb =
                new StringBuilder();

        if (!hasVideo && hasAudio) {

            if (abr != null) {

                sb.append(
                        Math.round(abr)
                ).append(" kbps");

            } else {

                sb.append("Áudio");
            }

        } else {

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

        sb.append(" - ")
                .append(
                        ext != null
                                ? ext.toUpperCase(
                                        Locale.ROOT
                                )
                                : "?"
                );

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

    private String textOrNull(
            JsonNode node,
            String field) {

        return node.has(field)
                && !node.get(field).isNull()
                ? node.get(field).asText()
                : null;
    }

    public int download(
            String url,
            VideoFormat selectedFormat,
            OutputType outputType,
            Path outputDir,
            Consumer<Double> progressListener,
            Consumer<Process> onProcessoIniciado)
            throws IOException, InterruptedException {

        Files.createDirectories(
                outputDir
        );

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

        /*
         * Diretório final.
         */
        command.add(
                "--paths"
        );

        command.add(
                "home:"
                        + outputDir
                                .toAbsolutePath()
        );

        /*
         * Diretório temporário.
         */
        command.add(
                "--paths"
        );

        command.add(
                "temp:"
                        + tempDir
                                .toAbsolutePath()
        );

        command.add(
                "-o"
        );

        command.add(
                "%(title)s [%(id)s].%(ext)s"
        );

        if (outputType == OutputType.MP3) {

            command.add("-f");

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

            String formatExpression;

            if (selectedFormat == null) {

                /*
                 * Sem escolha específica:
                 * prioriza MP4 + M4A.
                 */
                formatExpression =
                        "bv*[ext=mp4]"
                                + "+ba[ext=m4a]"
                                + "/b[ext=mp4]"
                                + "/bv*+ba/b";

            } else if (
                    selectedFormat.isVideoOnly()) {

                /*
                 * O usuário escolheu um vídeo sem áudio.
                 *
                 * Mantemos EXATAMENTE o vídeo escolhido
                 * e adicionamos o melhor áudio.
                 */
                formatExpression =
                        selectedFormat.getFormatId()
                                + "+ba[ext=m4a]/ba";

            } else {

                /*
                 * O formato escolhido já possui áudio.
                 *
                 * Portanto não baixamos um segundo áudio.
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

            /*
             * Se precisar juntar vídeo + áudio,
             * o resultado será MP4.
             */
            command.add(
                    "--merge-output-format"
            );

            command.add(
                    "mp4"
            );

            /*
             * Caso já exista um arquivo único em outro
             * container compatível, remuxa para MP4.
             *
             * Remux não re-encoda o vídeo.
             */
            command.add(
                    "--remux-video"
            );

            command.add(
                    "mp4"
            );
        }

        command.add(
                url
        );

        System.out.println(
                "Executando:"
        );

        System.out.println(
                String.join(
                        " ",
                        command
                )
        );

        ProcessBuilder pb =
                new ProcessBuilder(command);

        pb.redirectErrorStream(true);

        Process process =
                pb.start();

        if (onProcessoIniciado != null) {

            onProcessoIniciado.accept(
                    process
            );
        }

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
                    (line = reader.readLine())
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

            boolean terminou =
                    process.waitFor(
                            30,
                            java.util.concurrent.TimeUnit.MINUTES
                    );

            if (!terminou) {

                PlatformUtils.destroyProcessTree(
                        process
                );

                apagarDiretorio(
                        tempDir
                );

                throw new IOException(
                        "Download demorou demais e foi encerrado."
                );
            }

            apagarDiretorio(
                    tempDir
            );
        }

        return process.exitValue();
    }

    public void cancelarDownload(
            Process process) {

        PlatformUtils.destroyProcessTree(
                process
        );
    }

    private void apagarDiretorio(
            Path diretorio) {

        if (diretorio == null
                || !Files.exists(diretorio)) {

            return;
        }

        try (
                Stream<Path> paths =
                        Files.walk(diretorio)
        ) {

            paths
                    .sorted(
                            java.util.Comparator
                                    .reverseOrder()
                    )
                    .forEach(
                            path -> {

                                try {

                                    Files.deleteIfExists(
                                            path
                                    );

                                } catch (IOException e) {

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
                    (line = reader.readLine())
                            != null
            ) {

                sb.append(line)
                        .append('\n');
            }

            return sb.toString();
        }
    }

    public boolean binariosDisponiveis() {

        return testarBinario(
                ytDlpBinary,
                "--version"
        )
                && testarBinario(
                ffmpegBinary,
                "-version"
        );
    }

    private boolean testarBinario(
            String binario,
            String argumento) {

        try {

            Process p =
                    new ProcessBuilder(
                            binario,
                            argumento
                    )
                            .redirectErrorStream(true)
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