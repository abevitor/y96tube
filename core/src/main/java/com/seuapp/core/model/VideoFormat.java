package com.seuapp.core.model;


public class VideoFormat {

    private final String formatId;
    private final String ext;
    private final String resolution;
    private final Double fileSizeMb;
    private final String label;

    public VideoFormat(String formatId, String ext, String resolution, Double fileSizeMb, String label){

        this.formatId = formatId;
        this.ext = ext;
        this.resolution = resolution;
        this.fileSizeMb = fileSizeMb;
        this.label = label;
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

    @Override
     public String toString() {
        return label;
    }

}
