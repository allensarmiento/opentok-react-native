package com.opentokreactnative.properties;

import com.facebook.react.bridge.ReadableMap;

public class PublisherProperties {
    private ReadableMap properties;

    public PublisherProperties(ReadableMap properties) {
        this.properties = properties;
    }

    public String getName() {
        return this.properties.getString("name");
    }

    public Boolean getAudioTrack() {
        return this.properties.getBoolean("audioTrack");
    }

    public Boolean getVideoTrack() {
        return this.properties.getBoolean("videoTrack");
    }

    public Integer getAudioBitrate() {
        return this.properties.getInt("audioBitrate");
    }

    public String getResolution() {
        return this.properties.getString("resolution");
    }

    public String getFrameRate() {
        return "FPS_" + this.properties.getInt("frameRate");
    }
}
