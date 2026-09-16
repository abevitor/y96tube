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
}    