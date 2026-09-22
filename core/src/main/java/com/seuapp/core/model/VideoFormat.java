package com.seuapp.core.model;

public class VideoFormat {

    private final String formatId;
    private final String ext;
    private final Integer height;
    private final Double audioBitrateKbps;

    private final boolean hasVideo;
    private final boolean hasAudio;

    private final Double fileSizeMb;
    private final String label;

    public VideoFormat(
            String formatId,
            String ext,
            Integer height,
            Double audioBitrateKbps,
            boolean hasVideo,
            boolean hasAudio,
            Double fileSizeMb,
            String label) {

        this.formatId = formatId;
        this.ext = ext;
        this.height = height;
        this.audioBitrateKbps = audioBitrateKbps;
        this.hasVideo = hasVideo;
        this.hasAudio = hasAudio;
        this.fileSizeMb = fileSizeMb;
        this.label = label;
    }

    public String getFormatId() {
        return formatId;
    }

    public String getExt() {
        return ext;
    }

    public Integer getHeight() {
        return height;
    }

    public Double getAudioBitrateKbps() {
        return audioBitrateKbps;
    }

    public boolean hasVideo() {
        return hasVideo;
    }

    public boolean hasAudio() {
        return hasAudio;
    }

    public boolean isAudioOnly() {
        return hasAudio && !hasVideo;
    }

    public boolean isVideoOnly() {
        return hasVideo && !hasAudio;
    }

    public boolean isCombined() {
        return hasVideo && hasAudio;
    }

    public Double getFileSizeMb() {
        return fileSizeMb;
    }

    @Override
    public String toString() {
        return label;
    }
}