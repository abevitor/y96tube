package com.seuapp.core.model;

public class VideoFormat {

    private final String formatId;
    private final String ext;
    private final Integer height;      
    private final Double audioBitrateKbps; 
    private final boolean audioOnly;
    private final Double fileSizeMb;
    private final String label;

    public VideoFormat(String formatId, String ext, Integer height, Double audioBitrateKbps,
                        boolean audioOnly, Double fileSizeMb, String label) {
        this.formatId = formatId;
        this.ext = ext;
        this.height = height;
        this.audioBitrateKbps = audioBitrateKbps;
        this.audioOnly = audioOnly;
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

    public boolean isAudioOnly() {
        return audioOnly;
    }

    public Double getFileSizeMb() {
        return fileSizeMb;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}