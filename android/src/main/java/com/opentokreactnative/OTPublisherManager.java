package com.opentokreactnative;

import android.util.Log;
import android.widget.FrameLayout;
import android.support.annotation.Nullable;
import android.view.View;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;

import com.opentok.android.Session;
import com.opentok.android.Connection;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Stream;
import com.opentok.android.OpentokError;
import com.opentok.android.Subscriber;
import com.opentok.android.SubscriberKit;

import com.opentokreactnative.builders.SessionBuilder;
import com.opentokreactnative.builders.PublisherBuilder;
import com.opentokreactnative.utils.EventUtils;
import com.opentokreactnative.utils.Utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;

public class OTPublisherManager extends ReactContextBaseJavaModule {
    public OTRN sharedState;

    public OTPublisherManager(ReactApplicationContext reactContext) {
        super(reactContext);
        sharedState = OTRN.getSharedState();
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @ReactMethod
    public void publishAudio(String publisherId, Boolean publishAudio) {
        ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
        Publisher mPublisher = mPublishers.get(publisherId);
        if (mPublisher != null) {
            mPublisher.setPublishAudio(publishAudio);
        }
    }

    @ReactMethod
    public void publishVideo(String publisherId, Boolean publishVideo) {
        ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
        Publisher mPublisher = mPublishers.get(publisherId);
        if (mPublisher != null) {
            mPublisher.setPublishVideo(publishVideo);
        }
    }

    @ReactMethod
    public void changeCameraPosition(String publisherId, String cameraPosition) {
        ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
        Publisher mPublisher = mPublishers.get(publisherId);
        if (mPublisher != null) {
            mPublisher.cycleCamera();
        }
    }
}
