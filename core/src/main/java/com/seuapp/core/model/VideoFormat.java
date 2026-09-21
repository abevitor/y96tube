package com.seuapp.core.model;


public class VideoFormat {

    private final String formatId;
    private final String ext;
    private final String resolution;
    private final Double fileSizeMb;
    private final String label;
    private final Integer height;
    private final Double audioBitrateKbps;


    public VideoFormat(String formatId, String ext, String resolution, Double fileSizeMb, String label, Integer height, Double audioBitrateKbps){

        this.formatId = formatId;
        this.ext = ext;
        this.resolution = resolution;
        this.fileSizeMb = fileSizeMb;
        this.label = label;
        this.height  = height;
        this.audioBitrateKbps = audioBitrateKbps;
    }

    public String getFormatId() {
        return formatId;
    }

    public String getExt() {
        return ext;
    }

    public String getResolution() {
        return resolution;
    }

    public Double getFileSizeMb() {
        return fileSizeMb;
    }

    public String getLabel() {
        return label;
    }

    public Integer getHeight() {
        return height;
    }

    public Double getAudioBitrateKbps() {
        return audioBitrateKbps;
    }

    @Override
     public String toString() {
        return label;
    }
}
