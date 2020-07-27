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

public class OTPublisherManager extends ReactContextBaseJavaModule
        implements PublisherKit.PublisherListener,
        PublisherKit.AudioLevelListener {

    private ConcurrentHashMap<String, Integer> connectionStatusMap = new ConcurrentHashMap<>();
    private Boolean logLevel = false;

    private static final String TAG = "OTRN";
    private final String publisherPreface = "publisher:";

    public OTRN sharedState;

    public OTPublisherManager(ReactApplicationContext reactContext) {
        super(reactContext);
        sharedState = OTRN.getSharedState();
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override 
    public void onError(PublisherKit publisherKit, OpentokError opentokError) {
        String publisherId = Utils.getPublisherId(publisherKit);
        if (publisherId.length() > 0) {
            String event = publisherId + ":" + publisherPreface + "onError";
            WritableMap errorInfo = EventUtils.prepareJSErrorMap(opentokError);
            this.sendEventMap(this.getReactApplicationContext(), event, errorInfo);
        }
        printLogs("onError: " + opentokError.getErrorDomain() 
            + " : " + opentokError.getErrorCode() + " - " 
            + opentokError.getMessage());
    }

    @Override
    public void onStreamCreated(PublisherKit publisherKit, Stream stream) {
        String publisherId = Utils.getPublisherId(publisherKit);
        ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
        mSubscriberStreams.put(stream.getStreamId(), stream);
        if (publisherId.length() > 0) {
            String event = publisherId + ":" + this.publisherPreface + "onStreamCreated";
            WritableMap streamInfo = EventUtils.prepareJSStreamMap(stream, publisherKit.getSession());
            sendEventMap(this.getReactApplicationContext(), event, streamInfo);
        }
        printLogs("onStreamCreated: Publisher Stream Created. Own stream " + stream.getStreamId());
    }

    @Override 
    public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {
        String publisherId = Utils.getPublisherId(publisherKit);
        String event = publisherId + ":" + this.publisherPreface + "onStreamDestroyed";
        ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
        String mStreamId = stream.getStreamId();
        mSubscriberStreams.remove(mStreamId);
        if (publisherId.length() > 0) {
            WritableMap streamInfo = EventUtils.prepareJSStreamMap(stream, publisherKit.getSession());
            this.sendEventMap(this.getReactApplicationContext(), event, streamInfo);
        }
        Callback mCallback = sharedState.getPublisherDestroyedCallbacks().get(publisherId);
        if (mCallback != null) {
            mCallback.invoke();
        }
        sharedState.getPublishers().remove(publisherId);
        printLogs("onStreamDestroyed: Publisher Stream Destroyed. Own stream " + stream.getStreamId());
    }

    @Override
    public void onAudioLevelUpdated(PublisherKit publisher, float audioLevel) {
        String publisherId = Utils.getPublisherId(publisher);
        if (publisherId.length() > 0) {
            String event = publisherId + ":" + publisherPreface + "onAudioLevelUpdated";
            this.sendEventWithString(this.getReactApplicationContext(), event, String.valueOf(audioLevel));
        }
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

    private void sendEventWithString(ReactContext reactContext, String eventName, String eventString) {
        if (this.containsJsOrComponentEvents(eventName)) {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, eventString);
        }
    }

    private void sendEventMap(ReactContext reactContext, String eventName, @Nullable WritableMap eventData) {
        if (this.containsJsOrComponentEvents(eventName)) {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, eventData);
        }
    }

    private boolean containsJsOrComponentEvents(String eventName) {
        ArrayList<String> jsEvents = sharedState.getJsEvents();
        ArrayList<String> componentEvents = sharedState.getComponentEvents();
        if (Utils.contains(jsEvents, eventName) || Utils.contains(componentEvents, eventName)) {
            return true;
        }
        return false;
    }

    private void printLogs(String message) {
        if (this.logLevel) {
            Log.i(TAG, message);
        }
    }

    @ReactMethod
    public void init(String publisherId, ReadableMap properties, Callback callback) {
        Publisher mPublisher = null;

        String videoSource = properties.getString("videoSource");
        ReactApplicationContext reactContext = this.getReactApplicationContext();
        if (videoSource.equals("screen")) {
            View view = getCurrentActivity().getWindow().getDecorView().getRootView();
            OTScreenCapturer capturer = new OTScreenCapturer(view);
            mPublisher = PublisherBuilder.buildPublisher(reactContext, properties, capturer);
            mPublisher.setPublisherVideoType(PublisherKit.PublisherKitVideoType.PublisherKitVideoTypeScreen);
        } else {
            mPublisher = PublisherBuilder.buildPublisher(reactContext, properties);
            String cameraPosition = properties.getString("cameraPosition");
            if (cameraPosition.equals("back")) {
                mPublisher.cycleCamera();
            }
        }

        mPublisher.setPublisherListener(this);
        mPublisher.setAudioLevelListener(this);

        Boolean audioFallbackEnabled = properties.getBoolean("audioFallbackEnabled");
        mPublisher.setAudioFallbackEnabled(audioFallbackEnabled);

        Boolean publishVideo = properties.getBoolean("publishVideo");
        mPublisher.setPublishVideo(publishVideo);

        Boolean publishAudio = properties.getBoolean("publishAudio");
        mPublisher.setPublishAudio(publishAudio);

        ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
        mPublishers.put(publisherId, mPublisher);

        callback.invoke();
    }

    @ReactMethod
    public void publish(String sessionId, String publisherId, Callback callback) {
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        Session mSession = mSessions.get(sessionId);

        ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
        Publisher mPublisher = mPublishers.get(publisherId);

        // this.attemptPublishAndInvokeCallback(mSession, mPublisher, callback);
        if (mSession == null) {
            WritableMap errorInfo = EventUtils.createError("Error publishing. Could not find native session instance.");
            callback.invoke(errorInfo);
        } else if (mPublisher == null) {
            WritableMap errorInfo = EventUtils.createError("Error publishing. Could not find native publisher instance.");
            callback.invoke(errorInfo);
        } else {
            mSession.publish(mPublisher);
            callback.invoke();
        }
    }

    private Session getSession(String sessionId) {
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        return mSessions.get(sessionId);
    }

    private Publisher getPublisher(String publisherId) {
        ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
        return mPublishers.get(publisherId);
    }

    private void attemptPublishAndInvokeCallback(Session session, Publisher publisher, Callback callback) {
        if (session == null) {
            WritableMap errorInfo = EventUtils.createError("Error publishing. Could not find native session instance.");
            callback.invoke(errorInfo);
        } else if (publisher == null) {
            WritableMap errorInfo = EventUtils.createError("Error publishing. Could not find native publisher instance.");
            callback.invoke(errorInfo);
        } else {
            session.publish(publisher);
            callback.invoke();
        }
    }

    @ReactMethod
    public void destroy(final String publisherId, final Callback callback) {
        UiThreadUtil.runOnUiThread(new Runnable() {
            @Override 
            public void run() {
                Session mSession = null;

                // If valid session and publisher, get the session and remove the publisher
                ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
                Publisher mPublisher = mPublishers.get(publisherId);
                if (mPublisher != null && 
                    mPublisher.getSession() != null && 
                    mPublisher.getSession().getSessionId() != null) {
                        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
                        mSession = mSessions.get(mPublisher.getSession().getSessionId());
                }
                mPublishers.remove(publisherId);

                // If there is a publisher view container, remove all its views
                ConcurrentHashMap<String, FrameLayout> mPublisherViewContainers = sharedState.getPublisherViewContainers();
                FrameLayout mPublisherViewContainer = mPublisherViewContainers.get(publisherId);
                if (mPublisherViewContainer != null) {
                    mPublisherViewContainer.removeAllViews();
                }
                mPublisherViewContainers.remove(publisherId);

                // Unpublish the publisher from the session
                if (mSession != null && mPublisher != null) {
                    mSession.unpublish(mPublisher);
                }

                // Stop publishing capturing
                if (mPublisher != null) {
                    mPublisher.getCapturer().stopCapture();
                }

                // Add to the publisher destroyed callbacks
                ConcurrentHashMap<String, Callback> mPublisherDestroyedCallbacks = sharedState.getPublisherDestroyedCallbacks();
                mPublisherDestroyedCallbacks.put(publisherId, callback);
            }
        });
    }
}
