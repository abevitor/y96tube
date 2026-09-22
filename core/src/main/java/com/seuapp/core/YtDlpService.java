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
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YtDlpService {

    private static final Pattern PROGRESS_PATTERN = Pattern.compile("\\[download]\\s+(\\d{1,3}(?:\\.\\d+)?)%");

    private final String ytDlpBinary;
    private final String ffmpegBinary;

    public YtDlpService(String ytDlpBinary, String ffmpegBinary) {
        this.ytDlpBinary = ytDlpBinary;
        this.ffmpegBinary = ffmpegBinary;
    }

    public static YtDlpService withDefaultLocations(Path appDir) {

        boolean windows = System.getProperty("os.name", "")
                .toLowerCase()
                .contains("win");

        String ytDlpName = windows ? "yt-dlp.exe" : "yt-dlp";
        String ffmpegName = windows ? "ffmpeg.exe" : "ffmpeg";

        Path toolsDir = encontrarPastaTools(appDir);

        System.out.println("Diretório inicial: " + appDir.toAbsolutePath());
        System.out.println("Pasta tools encontrada: " + toolsDir);

        String ytDlp = ytDlpName;
        String ffmpeg = ffmpegName;

        if (toolsDir != null) {

            Path ytDlpPath = toolsDir.resolve(ytDlpName);
            Path ffmpegPath = toolsDir.resolve(ffmpegName);

            System.out.println("yt-dlp procurado em: " + ytDlpPath);
            System.out.println("ffmpeg procurado em: " + ffmpegPath);

            if (Files.isRegularFile(ytDlpPath)) {
                ytDlp = ytDlpPath.toAbsolutePath().toString();
            }

            if (Files.isRegularFile(ffmpegPath)) {
                ffmpeg = ffmpegPath.toAbsolutePath().toString();
            }
        }
        return new YtDlpService(ytDlp, ffmpeg);
    }

    private static Path encontrarPastaTools(Path partida) {
        Path atual = partida.toAbsolutePath();

        for (int i = 0; i < 6 && atual != null; i++) {
            Path candidato = atual.resolve("tools");
            if (Files.isDirectory(candidato)) {
                return candidato;
            }
            atual = atual.getParent();
        }

        return null;
    }

    public List<VideoFormat> listFormats(String url) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(ytDlpBinary, "-J", "--no-warnings", url);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        String json = readAll(process.getInputStream());
        String errOutput = readAll(process.getErrorStream());

        boolean terminou = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
        if (!terminou) {
            process.destroyForcibly();
            throw new IOException("yt-dlp demorou demais para responder (timeout).");
        }

        int exit = process.exitValue();
        if (exit != 0) {
            throw new IOException("yt-dlp falhou ao consultar formatos (exit " + exit + "): " + errOutput);
        }

        return parseFormats(json);
    }

    private List<VideoFormat> parseFormats(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode formats = root.get("formats");

        List<VideoFormat> result = new ArrayList<>();
        if (formats == null || !formats.isArray()) {
            return result;
        }

        for (JsonNode f : formats) {
            String vcodec = textOrNull(f, "vcodec");
            String acodec = textOrNull(f, "acodec");

            boolean semVideo = vcodec == null || "none".equals(vcodec);
            boolean semAudio = acodec == null || "none".equals(acodec);

            // descarta "storyboards" (miniaturas da barra de progresso) e formatos
            // quebrados
            if (semVideo && semAudio) {
                continue;
            }

            String id = textOrNull(f, "format_id");
            String ext = textOrNull(f, "ext");
            boolean audioOnly = semVideo; // tem áudio mas não tem vídeo

            Integer height = f.has("height") && !f.get("height").isNull() ? f.get("height").asInt() : null;
            Double abr = f.has("abr") && !f.get("abr").isNull() ? f.get("abr").asDouble() : null;

            Double sizeMb = null;
            if (f.has("filesize") && !f.get("filesize").isNull()) {
                sizeMb = f.get("filesize").asDouble() / (1024.0 * 1024.0);
            } else if (f.has("filesize_approx") && !f.get("filesize_approx").isNull()) {
                sizeMb = f.get("filesize_approx").asDouble() / (1024.0 * 1024.0);
            }

            // pra formato de vídeo, só nos interessa quem tem altura definida (descarta
            // lixo residual)
            if (!audioOnly && height == null) {
                continue;
            }

            String label = buildLabel(ext, height, abr, audioOnly, sizeMb);
            result.add(new VideoFormat(id, ext, height, abr, audioOnly, sizeMb, label));
        }

        // ordena do melhor pro pior: vídeo por resolução, áudio por bitrate
        result.sort((a, b) -> {
            if (a.isAudioOnly() && b.isAudioOnly()) {
                double abrA = a.getAudioBitrateKbps() != null ? a.getAudioBitrateKbps() : 0;
                double abrB = b.getAudioBitrateKbps() != null ? b.getAudioBitrateKbps() : 0;
                return Double.compare(abrB, abrA);
            }
            int hA = a.getHeight() != null ? a.getHeight() : 0;
            int hB = b.getHeight() != null ? b.getHeight() : 0;
            return Integer.compare(hB, hA);
        });

        return result;
    }

    private String buildLabel(String ext, Integer height, Double abr, boolean audioOnly, Double sizeMb) {
        StringBuilder sb = new StringBuilder();

        if (audioOnly) {
            if (abr != null) {
                sb.append(Math.round(abr)).append(" kbps");
            } else {
                sb.append("Áudio");
            }
        } else {
            sb.append(height != null ? height + "p" : "Vídeo");
        }

        sb.append(" - ").append(ext != null ? ext.toUpperCase() : "?");

        if (sizeMb != null) {
            sb.append(String.format(" (~%.1f MB)", sizeMb));
        }

        return sb.toString();
    }

    private String textOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;

    }

    public int download(String url,
            String formatId,
            OutputType outputType,
            Path outputDir,
            Consumer<Double> progressListener,
            Consumer<Process> onProcessoIniciado) throws IOException, InterruptedException {
        Files.createDirectories(outputDir);

        List<String> command = new ArrayList<>();
        command.add(ytDlpBinary);
        command.add("--ffmpeg-location");
        command.add(ffmpegBinary);
        command.add("-o");
        command.add(outputDir.resolve("%(title)s.%(ext)s").toString());

        if (outputType == OutputType.MP3) {
            command.add("-f");
            command.add(formatId != null ? formatId : "bestaudio");
            command.add("--extract-audio");
            command.add("--audio-format");
            command.add("mp3");
            command.add("--audio-quality");
            command.add("0");
        } else {
            command.add("-f");
            command.add(formatId != null ? formatId + "+bestaudio/best" : "bestvideo+bestaudio/best");
            command.add("--merge-output-format");
            command.add("mp4");

        }

        command.add(url);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        if(onProcessoIniciado != null) {
            onProcessoIniciado.accept(process);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (progressListener != null) {
                    Matcher matcher = PROGRESS_PATTERN.matcher(line);
                    if (matcher.find()) {
                        double percent = Double.parseDouble(matcher.group(1));
                        progressListener.accept(percent);
                    }
                }
            }
        }
        boolean terminou = process.waitFor(30, java.util.concurrent.TimeUnit.MINUTES);
        if (!terminou) {
            process.destroyForcibly();
            throw new IOException("Download demorou demais e foi cancelado (timeout de 30 minutos).");
        }
        return process.exitValue();

    }

    private String readAll(java.io.InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    public boolean binariosDisponiveis() {
        return testarBinario(ytDlpBinary, "--version")
                && testarBinario(ffmpegBinary, "-version");
    }

    private boolean testarBinario(String binario, String argumento) {
        try {
            Process p = new ProcessBuilder(binario, argumento)
                    .redirectErrorStream(true)
                    .start();

            int exit = p.waitFor();

            return exit == 0;

        } catch (IOException e) {
            System.out.println("Erro ao executar: " + binario);
            System.out.println("Motivo: " + e.getMessage());
            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}