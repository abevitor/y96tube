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

    private static final Pattern PROGRESS_PATTERN =
    Pattern.compile("\\[download]\\s+(\\d{1,3}(?:\\.\\d+)?)%");

    private final String ytDlpBinary;
    private final String ffmpegBinary;

    public YtDlpService(String ytDlpBinary, String ffmpegBinary){
        this.ytDlpBinary = ytDlpBinary;
        this.ffmpegBinary = ffmpegBinary;
    }

    public static YtDlpService withDefaultLocations(Path appDir) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String ytDlpName = windows ? "yt-dlp.exe" : "yt-dlp";
        String ffmpegName = windows ? "ffmpeg.exe" : "ffmpeg";

        Path ytDlpPath = appDir.resolve("tools").resolve(ytDlpName);
        Path ffmpegPath = appDir.resolve("tools").resolve(ffmpegName);

        String ytDlp = Files.isRegularFile(ytDlpPath) ? ytDlpPath.toString() : ytDlpName;
        String ffmpeg = Files.isRegularFile(ffmpegPath) ? ffmpegPath.toString() : ffmpegName;

        return new YtDlpService(ytDlp, ffmpeg);
    }

    public List<VideoFormat> listFormats(String url) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(ytDlpBinary, "-J", "--no-warnings", url);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        String json = readAll(process.getInputStream());
        String errOutput = readAll(process.getErrorStream());

        int exit = process.waitFor();
        if(exit != 0) {
            throw new IOException("yt-dlp falhou ao consultar formatos (exit " + exit + "): " + errOutput);

        }

        return parseFormats(json);
    }

    private List<VideoFormat> parseFormats(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode formats = root.get("formats");

        List<VideoFormat> result = new ArrayList<>();
        if(formats == null || !formats.isArray()) {
            return result;
        }

        for(JsonNode f : formats) {
            String id = textOrNull(f, "format_id");
            String ext = textOrNull(f, "ext");
            String resolution = f.has("resolution") ? f.get("resolution").asText()
                    : (f.has("height") && !f.get("height").isNull() ? f.get("height").asText() + "p" : "audio only");
            Double sizeMb = null;
            if(f.has("filesize") && !f.get("filesize").isNull()) {
                sizeMb = f.get("filesize").asDouble() / (1024.0 * 1024.0);
            } else if (f.has("filesize_approx") && !f.get("filesize_approx").isNull()){
                 sizeMb = f.get("filesize_approx").asDouble() / (1024.0 * 1024.0);
            }
            
            String label = buildLabel(id, ext, resolution, sizeMb);
            result.add(new VideoFormat(id, ext, resolution, sizeMb, label));

        }
    }
}    