package com.opentokreactnative.builders;

import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReactApplicationContext;
import com.opentok.android.Publisher;
import com.opentokreactnative.properties.PublisherProperties;
import com.opentokreactnative.OTScreenCapturer;
import java.util.concurrent.ConcurrentHashMap;

public final class PublisherBuilder {
    public static Publisher buildPublisher(ReactApplicationContext reactContext, ReadableMap properties) {
        PublisherProperties publisherProperties = new PublisherProperties(properties);

        return new Publisher.Builder(reactContext)
            .audioTrack(publisherProperties.getAudioTrack())
            .videoTrack(publisherProperties.getVideoTrack())
            .name(publisherProperties.getName())
            .audioBitrate(publisherProperties.getAudioBitrate())
            .resolution(Publisher.CameraCaptureResolution.valueOf(publisherProperties.getResolution()))
            .frameRate(Publisher.CameraCaptureFrameRate.valueOf(publisherProperties.getFrameRate()))
            .build();
    }

    public static Publisher buildPublisher(ReactApplicationContext reactContext, ReadableMap properties, OTScreenCapturer capturer) {
        PublisherProperties publisherProperties = new PublisherProperties(properties);

        return new Publisher.Builder(reactContext)
            .audioTrack(publisherProperties.getAudioTrack())
            .videoTrack(publisherProperties.getVideoTrack())
            .name(publisherProperties.getName())
            .audioBitrate(publisherProperties.getAudioBitrate())
            .resolution(Publisher.CameraCaptureResolution.valueOf(publisherProperties.getResolution()))
            .frameRate(Publisher.CameraCaptureFrameRate.valueOf(publisherProperties.getFrameRate()))
            .capturer(capturer)
            .build();
    }
}
