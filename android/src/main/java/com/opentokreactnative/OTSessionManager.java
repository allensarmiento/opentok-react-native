package com.opentokreactnative;

/**
 * Created by manik on 1/29/18.
 */

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

public class OTSessionManager extends ReactContextBaseJavaModule
        implements Session.SessionListener,
        PublisherKit.PublisherListener,
        PublisherKit.AudioLevelListener,
        SubscriberKit.SubscriberListener,
        Session.SignalListener,
        Session.ConnectionListener,
        Session.ReconnectionListener,
        Session.ArchiveListener,
        Session.StreamPropertiesListener,
        SubscriberKit.AudioLevelListener,
        SubscriberKit.AudioStatsListener,
        SubscriberKit.VideoStatsListener,
        SubscriberKit.VideoListener,
        SubscriberKit.StreamListener{

    private ConcurrentHashMap<String, Integer> connectionStatusMap = new ConcurrentHashMap<>();
    private ArrayList<String> jsEvents = new ArrayList<String>();
    private ArrayList<String> componentEvents = new ArrayList<String>();
    private static final String TAG = "OTRN";
    private final String sessionPreface = "session:";
    private final String publisherPreface = "publisher:";
    private final String subscriberPreface = "subscriber:";
    private Boolean logLevel = false;
    public OTRN sharedState;

    public OTSessionManager(ReactApplicationContext reactContext) {
        super(reactContext);
        sharedState = OTRN.getSharedState();
    }

    /**
     * Creates a new session, sets listeners, and adds the session and extra 
     * details to the shared state.
     * 
     * @param apiKey
     * @param sessionId
     * @param sessionOptions
     */
    @ReactMethod
    public void initSession(String apiKey, String sessionId, ReadableMap sessionOptions) {
        // Build the new OT session
        Session mSession = SessionBuilder.buildSession(this.getReactApplicationContext(), apiKey, sessionId, sessionOptions);

        // Set session listeners
        mSession.setSessionListener(this);
        mSession.setSignalListener(this);
        mSession.setConnectionListener(this);
        mSession.setReconnectionListener(this);
        mSession.setArchiveListener(this);
        mSession.setStreamPropertiesListener(this);

        // Add the new session to the sessions state
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        mSessions.put(sessionId, mSession);

        // Add the new session to the androidOnTopMap state
        ConcurrentHashMap<String, String> mAndroidOnTopMap = sharedState.getAndroidOnTopMap();
        String androidOnTop = sessionOptions.getString("androidOnTop");
        mAndroidOnTopMap.put(sessionId, androidOnTop);

        // Add the new session to the androidZOrderMap state
        ConcurrentHashMap<String, String> mAndroidZOrderMap = sharedState.getAndroidZOrderMap();
        String androidZOrder = sessionOptions.getString("androidZOrder");
        mAndroidZOrderMap.put(sessionId, androidZOrder);
    }

    /**
     * Attempt to connect to the session using the token. 
     * 
     * @param sessionId
     * @param token
     * @param callback
     */
    @ReactMethod
    public void connect(String sessionId, String token, Callback callback) {
        // Connect the sessionId to the session
        ConcurrentHashMap<String, Callback> mSessionConnectCallbacks = sharedState.getSessionConnectCallbacks();
        mSessionConnectCallbacks.put(sessionId, callback);

        // Get the session from the sessions state
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        Session mSession = mSessions.get(sessionId);

        // Attempt to connect to the session with the token
        if (mSession != null) {
            mSession.connect(token);
        } else {
            WritableMap errorInfo = EventUtils.createError("Error connecting to session. Could not find native session instance");
            callback.invoke(errorInfo);
        }
    }

    /**
     * Builds the publisher object, sets listeners and add the new publisher to the state
     * 
     * @param publisherId
     * @param properties
     * @param callback
     */
    @ReactMethod
    public void initPublisher(String publisherId, ReadableMap properties, Callback callback) {
        // Publisher object that will be created
        Publisher mPublisher = null;

        String videoSource = properties.getString("videoSource");

        // Build the publisher depending on the video source
        if (videoSource.equals("screen")) {
            View view = getCurrentActivity().getWindow().getDecorView().getRootView();
            OTScreenCapturer capturer = new OTScreenCapturer(view);

            mPublisher = PublisherBuilder.buildPublisher(this.getReactApplicationContext(), properties, capturer);
            mPublisher.setPublisherVideoType(PublisherKit.PublisherKitVideoType.PublisherKitVideoTypeScreen);
        } else {
            mPublisher = PublisherBuilder.buildPublisher(this.getReactApplicationContext(), properties);

            String cameraPosition = properties.getString("cameraPosition");
            if (cameraPosition.equals("back")) {
                mPublisher.cycleCamera();
            }
        }

        // Set publisher listeners
        mPublisher.setPublisherListener(this);
        mPublisher.setAudioLevelListener(this);

        // Set publisher properties
        Boolean audioFallbackEnabled = properties.getBoolean("audioFallbackEnabled");
        mPublisher.setAudioFallbackEnabled(audioFallbackEnabled);

        Boolean publishVideo = properties.getBoolean("publishVideo");
        mPublisher.setPublishVideo(publishVideo);

        Boolean publishAudio = properties.getBoolean("publishAudio");
        mPublisher.setPublishAudio(publishAudio);

        // Add the publisher to the state
        ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
        mPublishers.put(publisherId, mPublisher);

        callback.invoke();
    }

    /**
     * Attempt to publish to the session.
     * 
     * @param sessionId
     * @param publisherId
     * @param callback
     */
    @ReactMethod
    public void publish(String sessionId, String publisherId, Callback callback) {
        // Get the session using the sessionId
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        Session mSession = mSessions.get(sessionId);

        // Attempt to publish to the session
        if (mSession != null) {
            // Get the publisher from using the publisherId
            ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
            Publisher mPublisher = mPublishers.get(publisherId);

            // Attempt to publish to the session
            if (mPublisher != null) {
                mSession.publish(mPublisher);
                callback.invoke();
            } else {
                WritableMap errorInfo = EventUtils.createError("Error publishing. Could not find native publisher instance.");
                callback.invoke(errorInfo);
            }
        } else {
            WritableMap errorInfo = EventUtils.createError("Error publishing. Could not find native session instance.");
            callback.invoke(errorInfo);
        }
    }

    /**
     * Create a new subscriber, set listeners and attempt to subscribe to the seession.
     * 
     * @param streamId
     * @param sessionId
     * @param properties
     * @param callback
     */
    @ReactMethod
    public void subscribeToStream(String streamId, String sessionId, ReadableMap properties, Callback callback) {
        // Get the current stream using the streamId
        ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
        Stream stream = mSubscriberStreams.get(streamId);

        // Get the session using the sessionId
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        Session mSession = mSessions.get(sessionId);

        // Build the new subscriber
        Subscriber mSubscriber = new Subscriber.Builder(getReactApplicationContext(), stream).build();

        // Set subscriber listeners
        mSubscriber.setSubscriberListener(this);
        mSubscriber.setAudioLevelListener(this);
        mSubscriber.setAudioStatsListener(this);
        mSubscriber.setVideoStatsListener(this);
        mSubscriber.setVideoListener(this);
        mSubscriber.setStreamListener(this);

        // Set properties to subscribe to
        mSubscriber.setSubscribeToAudio(properties.getBoolean("subscribeToAudio"));
        mSubscriber.setSubscribeToVideo(properties.getBoolean("subscribeToVideo"));

        // Add the new subscriber to the subscribers state
        ConcurrentHashMap<String, Subscriber> mSubscribers = sharedState.getSubscribers();
        mSubscribers.put(streamId, mSubscriber);
        
        // Attempt to subscribe to the session
        if (mSession != null) {
            mSession.subscribe(mSubscriber);
            callback.invoke(null, streamId);
        } else {
            WritableMap errorInfo = EventUtils.createError("Error subscribing. The native session instance could not be found.");
            callback.invoke(errorInfo);
        }
    }

    /**
     * Remove the streams and view containers of the subscriber. 
     * 
     * @param streamId
     * @param callback
     */
    @ReactMethod
    public void removeSubscriber(final String streamId, final Callback callback) {
        UiThreadUtil.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Find the subscriber using the streamId
                ConcurrentHashMap<String, Subscriber> mSubscribers = sharedState.getSubscribers();
                String mStreamId = streamId;
                Subscriber mSubscriber = mSubscribers.get(mStreamId);

                // Find the subscriber's stream view containers
                ConcurrentHashMap<String, FrameLayout> mSubscriberViewContainers = sharedState.getSubscriberViewContainers();
                FrameLayout mSubscriberViewContainer = mSubscriberViewContainers.get(mStreamId);

                // Remove all stream views
                if (mSubscriberViewContainer != null) {
                    mSubscriberViewContainer.removeAllViews();
                }

                // Remove the stream from the subscribers state 
                mSubscriberViewContainers.remove(mStreamId);
                mSubscribers.remove(mStreamId);

                // Remove the stream from the subscribers stream state
                ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
                mSubscriberStreams.remove(mStreamId);

                Callback mCallback = callback;
                mCallback.invoke();

            }
        });
    }

    /**
     * Disconnects the sessionId from the session and updates the sessionDisconnectCallbacks
     * 
     * @param sessionId
     * @param callback
     */
    @ReactMethod
    public void disconnectSession(String sessionId, Callback callback) {
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        Session mSession = mSessions.get(sessionId);

        ConcurrentHashMap<String, Callback> mSessionDisconnectCallbacks = sharedState.getSessionDisconnectCallbacks();
        mSessionDisconnectCallbacks.put(sessionId, callback);

        if (mSession != null) {
            mSession.disconnect();
        }
    }

    /**
     * Publishes the audio for a publisher
     * 
     * @param publisherId
     * @param publishAudio
     */
    @ReactMethod
    public void publishAudio(String publisherId, Boolean publishAudio) {
        ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
        Publisher mPublisher = mPublishers.get(publisherId);

        if (mPublisher != null) {
            mPublisher.setPublishAudio(publishAudio);
        }
    }

    /**
     * Publishes the video for a publisher
     * 
     * @param publisherId
     * @param publishVideo
     */
    @ReactMethod
    public void publishVideo(String publisherId, Boolean publishVideo) {
        ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
        Publisher mPublisher = mPublishers.get(publisherId);

        if (mPublisher != null) {
            mPublisher.setPublishVideo(publishVideo);
        }
    }

    /**
     * Subscribes to the audio for a stream
     * 
     * @param streamId
     * @param subscribeToAudio
     */
    @ReactMethod
    public void subscribeToAudio(String streamId, Boolean subscribeToAudio) {
        ConcurrentHashMap<String, Subscriber> mSubscribers = sharedState.getSubscribers();
        Subscriber mSubscriber = mSubscribers.get(streamId);

        if (mSubscriber != null) {
            mSubscriber.setSubscribeToAudio(subscribeToAudio);
        }
    }

    /**
     * Subscribes to the video for a stream
     * 
     * @param streamId
     * @param subscribeToVideo
     */
    @ReactMethod
    public void subscribeToVideo(String streamId, Boolean subscribeToVideo) {
        ConcurrentHashMap<String, Subscriber> mSubscribers = sharedState.getSubscribers();
        Subscriber mSubscriber = mSubscribers.get(streamId);

        if (mSubscriber != null) {
            mSubscriber.setSubscribeToVideo(subscribeToVideo);
        }
    }

    /**
     * Change the camera view for a publisher
     * 
     * @param publisherId
     * @param cameraPosition
     */
    @ReactMethod
    public void changeCameraPosition(String publisherId, String cameraPosition) {
        ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
        Publisher mPublisher = mPublishers.get(publisherId);

        if (mPublisher != null) {
            mPublisher.cycleCamera();
        }
    }

    /**
     * Update the jsEvents of OTSessionManager
     * 
     * @param events
     */
    @ReactMethod
    public void setNativeEvents(ReadableArray events) {
        for (int i = 0; i < events.size(); i++) {
            this.jsEvents.add(events.getString(i));
        }
    }

    /**
     * Remove jsEvents of OTSessionManager
     * 
     * @param events
     */
    @ReactMethod
    public void removeNativeEvents(ReadableArray events) {
        for (int i = 0; i < events.size(); i++) {
            this.jsEvents.remove(events.getString(i));
        }
    }

    /**
     * Set componentEvents of OTSessionManager
     * 
     * @param events
     */
    @ReactMethod
    public void setJSComponentEvents(ReadableArray events) {
        for (int i = 0; i < events.size(); i++) {
            this.componentEvents.add(events.getString(i));
        }
    }

    /**
     * Remove componentEvents of OTSessionManager
     * 
     * @param events
     */
    @ReactMethod
    public void removeJSComponentEvents(ReadableArray events) {
        for (int i = 0; i < events.size(); i++) {
            this.componentEvents.remove(events.getString(i));
        }
    }

    /**
     * Attempt to send a signal if there is a valid session and/or connection
     * 
     * @param sessionId
     * @param signal
     * @param callback
     */
    @ReactMethod
    public void sendSignal(String sessionId, ReadableMap signal, Callback callback) {
        // Get the session of the sessionId
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        Session mSession = mSessions.get(sessionId);

        // Get the connectionId
        String connectionId = signal.getString("to");

        Connection mConnection = null;

        // Get the connection
        if (connectionId != null) {
            ConcurrentHashMap<String, Connection> mConnections = sharedState.getConnections();
            mConnection = mConnections.get(connectionId);
        }

        // If a valid connection and session, send a signal with the connection.
        // If just a valid session, send a signal.
        if (mConnection != null && mSession != null) {
            mSession.sendSignal(signal.getString("type"), signal.getString("data"), mConnection);
            callback.invoke();
        } else if (mSession != null) {
            mSession.sendSignal(signal.getString("type"), signal.getString("data"));
            callback.invoke();
        } else {
            WritableMap errorInfo = EventUtils.createError("There was an error sending the signal. The native session instance could not be found.");
            callback.invoke(errorInfo);
        }
    }

    /**
     * Remove publisher properties from the session
     * 
     * @param publisherId
     * @param callback
     */
    @ReactMethod
    public void destroyPublisher(final String publisherId, final Callback callback) {
        UiThreadUtil.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Session mSession = null;

                // If valid session and publisher, get the session and remove the publisher
                ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
                Publisher mPublisher = mPublishers.get(publisherId);
                if (mPublisher != null && mPublisher.getSession() != null && mPublisher.getSession().getSessionId() != null) {
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

                // Stop publisher capturing
                if (mPublisher != null) {
                    mPublisher.getCapturer().stopCapture();
                }

                // Add to the publisher destroyed callbacks
                ConcurrentHashMap<String, Callback> mPublisherDestroyedCallbacks = sharedState.getPublisherDestroyedCallbacks();
                mPublisherDestroyedCallbacks.put(publisherId, callback);
            }
        });
    }

    /**
     * Get sessionId and connectionStatus using the sessionId
     * 
     * @param sessionId
     * @param callback
     */
    @ReactMethod
    public void getSessionInfo(String sessionId, Callback callback) {
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        Session mSession = mSessions.get(sessionId);
        WritableMap sessionInfo = null;
        if (mSession != null){
            sessionInfo = EventUtils.prepareJSSessionMap(mSession);
            sessionInfo.putString("sessionId", mSession.getSessionId());
            sessionInfo.putInt("connectionStatus", getConnectionStatus(mSession.getSessionId()));
        }
        callback.invoke(sessionInfo);
    }

    /**
     * 
     * @param logLevel
     */
    @ReactMethod
    public void enableLogs(Boolean logLevel) {
        setLogLevel(logLevel);
    }

    /**
     * 
     * @param logLevel
     */
    private void setLogLevel(Boolean logLevel) {
        this.logLevel = logLevel;
    }

    private void sendEventMap(ReactContext reactContext, String eventName, @Nullable WritableMap eventData) {
        // TODO
        if (Utils.contains(jsEvents, eventName) || Utils.contains(componentEvents, eventName)) {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, eventData);
        }
    }

    private void sendEventWithString(ReactContext reactContext, String eventName, String eventString) {

        if (Utils.contains(jsEvents, eventName) || Utils.contains(componentEvents, eventName)) {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, eventString);
        }
    }

    private Integer getConnectionStatus(String sessionId) {
        Integer connectionStatus = 0;
        if (this.connectionStatusMap.get(sessionId) != null) {
            connectionStatus = this.connectionStatusMap.get(sessionId);
        }
        return connectionStatus;
    }

    private void setConnectionStatus(String sessionId, Integer connectionStatus) {
        this.connectionStatusMap.put(sessionId, connectionStatus);
    }


    private void printLogs(String message) {
        if (this.logLevel) {
            Log.i(TAG, message);
        }
    }

    @Override
    public String getName() {

        return this.getClass().getSimpleName();
    }

    @Override
    public void onError(Session session, OpentokError opentokError) {

        if (Utils.didConnectionFail(opentokError)) {
            setConnectionStatus(session.getSessionId(), 6);
        }
        WritableMap errorInfo = EventUtils.prepareJSErrorMap(opentokError);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onError", errorInfo);
        printLogs("There was an error");
    }

    @Override
    public void onDisconnected(Session session) {
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        ConcurrentHashMap<String, Callback> mSessionDisconnectCallbacks = sharedState.getSessionDisconnectCallbacks();
        ConcurrentHashMap<String, Callback> mSessionConnectCallbacks = sharedState.getSessionDisconnectCallbacks();
        setConnectionStatus(session.getSessionId(), 0);
        WritableMap sessionInfo = EventUtils.prepareJSSessionMap(session);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onDisconnected", sessionInfo);
        Callback disconnectCallback = mSessionDisconnectCallbacks.get(session.getSessionId());
        if (disconnectCallback != null) {
            disconnectCallback.invoke();
        }
        mSessions.remove(session.getSessionId());
        mSessionConnectCallbacks.remove(session.getSessionId());
        mSessionDisconnectCallbacks.remove(session.getSessionId());
        printLogs("onDisconnected: Disconnected from session: " + session.getSessionId());
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {

        ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
        mSubscriberStreams.put(stream.getStreamId(), stream);
        WritableMap streamInfo = EventUtils.prepareJSStreamMap(stream, session);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onStreamReceived", streamInfo);
        printLogs("onStreamReceived: New Stream Received " + stream.getStreamId() + " in session: " + session.getSessionId());

    }

    @Override
    public void onConnected(Session session) {

        setConnectionStatus(session.getSessionId(), 1);
        ConcurrentHashMap<String, Callback> mSessionConnectCallbacks = sharedState.getSessionConnectCallbacks();
        Callback mCallback = mSessionConnectCallbacks.get(session.getSessionId());
        if (mCallback != null) {
            mCallback.invoke();
        }
        WritableMap sessionInfo = EventUtils.prepareJSSessionMap(session);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onConnected", sessionInfo);
        printLogs("onConnected: Connected to session: "+session.getSessionId());
    }

    @Override
    public void onReconnected(Session session) {

        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onReconnected", null);
        printLogs("Reconnected");
    }

    @Override
    public void onReconnecting(Session session) {

        setConnectionStatus(session.getSessionId(), 3);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onReconnecting", null);
        printLogs("Reconnecting");
    }

    @Override
    public void onArchiveStarted(Session session, String id, String name) {

        WritableMap archiveInfo = Arguments.createMap();
        archiveInfo.putString("archiveId", id);
        archiveInfo.putString("name", name);
        archiveInfo.putString("sessionId", session.getSessionId());
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onArchiveStarted", archiveInfo);
        printLogs("Archive Started: " + id);
    }

    @Override
    public void onArchiveStopped(Session session, String id) {

        WritableMap archiveInfo = Arguments.createMap();
        archiveInfo.putString("archiveId", id);
        archiveInfo.putString("name", "");
        archiveInfo.putString("sessionId", session.getSessionId());
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onArchiveStopped", archiveInfo);
        printLogs("Archive Stopped: " + id);
    }
    @Override
    public void onConnectionCreated(Session session, Connection connection) {

        ConcurrentHashMap<String, Connection> mConnections = sharedState.getConnections();
        mConnections.put(connection.getConnectionId(), connection);
        WritableMap connectionInfo = EventUtils.prepareJSConnectionMap(connection);
        connectionInfo.putString("sessionId", session.getSessionId());
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onConnectionCreated", connectionInfo);
        printLogs("onConnectionCreated: Connection Created: "+connection.getConnectionId());
    }

    @Override
    public void onConnectionDestroyed(Session session, Connection connection) {

        ConcurrentHashMap<String, Connection> mConnections = sharedState.getConnections();
        mConnections.remove(connection.getConnectionId());
        WritableMap connectionInfo = EventUtils.prepareJSConnectionMap(connection);
        connectionInfo.putString("sessionId", session.getSessionId());
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onConnectionDestroyed", connectionInfo);
        printLogs("onConnectionDestroyed: Connection Destroyed: "+connection.getConnectionId());
    }
    @Override
    public void onStreamDropped(Session session, Stream stream) {

        WritableMap streamInfo = EventUtils.prepareJSStreamMap(stream, session);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onStreamDropped", streamInfo);
        printLogs("onStreamDropped: Stream Dropped: "+stream.getStreamId() +" in session: "+session.getSessionId());
    }

    @Override
    public void onStreamCreated(PublisherKit publisherKit, Stream stream) {

        String publisherId = Utils.getPublisherId(publisherKit);
        ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
        mSubscriberStreams.put(stream.getStreamId(), stream);
        if (publisherId.length() > 0) {
            String event = publisherId + ":" + publisherPreface + "onStreamCreated";;
            WritableMap streamInfo = EventUtils.prepareJSStreamMap(stream, publisherKit.getSession());
            sendEventMap(this.getReactApplicationContext(), event, streamInfo);
        }
        printLogs("onStreamCreated: Publisher Stream Created. Own stream "+stream.getStreamId());

    }

    @Override
    public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {

        String publisherId = Utils.getPublisherId(publisherKit);
        String event = publisherId + ":" + publisherPreface + "onStreamDestroyed";
        ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
        String mStreamId = stream.getStreamId();
        mSubscriberStreams.remove(mStreamId);
        if (publisherId.length() > 0) {
            WritableMap streamInfo = EventUtils.prepareJSStreamMap(stream, publisherKit.getSession());
            sendEventMap(this.getReactApplicationContext(), event, streamInfo);
        }
        Callback mCallback = sharedState.getPublisherDestroyedCallbacks().get(publisherId);
        if (mCallback != null) {
            mCallback.invoke();
        }
        sharedState.getPublishers().remove(publisherId);
        printLogs("onStreamDestroyed: Publisher Stream Destroyed. Own stream "+stream.getStreamId());
    }

    @Override
    public void onError(PublisherKit publisherKit, OpentokError opentokError) {

        String publisherId = Utils.getPublisherId(publisherKit);
        if (publisherId.length() > 0) {
            String event = publisherId + ":" + publisherPreface +  "onError";
            WritableMap errorInfo = EventUtils.prepareJSErrorMap(opentokError);
            sendEventMap(this.getReactApplicationContext(), event, errorInfo);
        }
        printLogs("onError: "+opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() +  " - "+opentokError.getMessage());
    }

    @Override
    public void onAudioLevelUpdated(PublisherKit publisher, float audioLevel) {

        String publisherId = Utils.getPublisherId(publisher);
        if (publisherId.length() > 0) {
            String event = publisherId + ":" + publisherPreface + "onAudioLevelUpdated";
            sendEventWithString(this.getReactApplicationContext(), event, String.valueOf(audioLevel));
        }
    }

    @Override
    public void onConnected(SubscriberKit subscriberKit) {

        String streamId = Utils.getStreamIdBySubscriber(subscriberKit);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriberKit.getSession()));
            }
            sendEventMap(this.getReactApplicationContext(), subscriberPreface +  "onConnected", subscriberInfo);
        }
        printLogs("onConnected: Subscriber connected. Stream: "+subscriberKit.getStream().getStreamId());
    }

    @Override
    public void onDisconnected(SubscriberKit subscriberKit) {

        String streamId = Utils.getStreamIdBySubscriber(subscriberKit);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriberKit.getSession()));
            }
            sendEventMap(this.getReactApplicationContext(), subscriberPreface +  "onDisconnected", subscriberInfo);
        }
        printLogs("onDisconnected: Subscriber disconnected. Stream: "+subscriberKit.getStream().getStreamId());
    }

    @Override
    public void onReconnected(SubscriberKit subscriberKit) {

        String streamId = Utils.getStreamIdBySubscriber(subscriberKit);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriberKit.getSession()));
            }
            sendEventMap(this.getReactApplicationContext(), subscriberPreface +  "onReconnected", subscriberInfo);
        }
        printLogs("onReconnected: Subscriber reconnected. Stream: "+subscriberKit.getStream().getStreamId());
    }

    @Override
    public void onError(SubscriberKit subscriberKit, OpentokError opentokError) {

        String streamId = Utils.getStreamIdBySubscriber(subscriberKit);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriberKit.getSession()));
            }
            subscriberInfo.putMap("error", EventUtils.prepareJSErrorMap(opentokError));
            sendEventMap(this.getReactApplicationContext(), subscriberPreface +  "onError", subscriberInfo);
        }
        printLogs("onError: "+opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() +  " - "+opentokError.getMessage());

    }

    @Override
    public void onSignalReceived(Session session, String type, String data, Connection connection) {

        WritableMap signalInfo = Arguments.createMap();
        signalInfo.putString("type", type);
        signalInfo.putString("data", data);
        if(connection != null) {
            signalInfo.putString("connectionId", connection.getConnectionId());
        }
        signalInfo.putString("sessionId", session.getSessionId());
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onSignalReceived", signalInfo);
        printLogs("onSignalReceived: Data: " + data + " Type: " + type);
    }

    @Override
    public void onAudioStats(SubscriberKit subscriber, SubscriberKit.SubscriberAudioStats stats) {

        String streamId = Utils.getStreamIdBySubscriber(subscriber);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriber.getSession()));
            }
            subscriberInfo.putMap("audioStats", EventUtils.prepareAudioNetworkStats(stats));
            sendEventMap(this.getReactApplicationContext(), subscriberPreface +  "onAudioStats", subscriberInfo);
        }
    }

    @Override
    public void onVideoStats(SubscriberKit subscriber, SubscriberKit.SubscriberVideoStats stats) {

        String streamId = Utils.getStreamIdBySubscriber(subscriber);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriber.getSession()));
            }
            subscriberInfo.putMap("videoStats", EventUtils.prepareVideoNetworkStats(stats));
            sendEventMap(this.getReactApplicationContext(), subscriberPreface + "onVideoStats", subscriberInfo);
        }
    }

    @Override
    public void onAudioLevelUpdated(SubscriberKit subscriber, float audioLevel) {

        String streamId = Utils.getStreamIdBySubscriber(subscriber);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriber.getSession()));
            }
            subscriberInfo.putString("audioLevel", String.valueOf(audioLevel));
            sendEventMap(this.getReactApplicationContext(), subscriberPreface + "onAudioLevelUpdated", subscriberInfo);
        }
    }

    @Override
    public void onVideoDisabled(SubscriberKit subscriber, String reason) {

        String streamId = Utils.getStreamIdBySubscriber(subscriber);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriber.getSession()));
            }
            subscriberInfo.putString("reason", reason);
            sendEventMap(this.getReactApplicationContext(), subscriberPreface + "onVideoDisabled", subscriberInfo);
        }
        printLogs("onVideoDisabled " + reason);
    }

    @Override
    public void onVideoEnabled(SubscriberKit subscriber, String reason) {

        String streamId = Utils.getStreamIdBySubscriber(subscriber);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriber.getSession()));
            }
            subscriberInfo.putString("reason", reason);
            sendEventMap(this.getReactApplicationContext(), subscriberPreface + "onVideoEnabled", subscriberInfo);
        }
        printLogs("onVideoEnabled " + reason);
    }

    @Override
    public void onVideoDisableWarning(SubscriberKit subscriber) {

        String streamId = Utils.getStreamIdBySubscriber(subscriber);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriber.getSession()));
            }
            sendEventMap(this.getReactApplicationContext(), subscriberPreface + "onVideoDisableWarning", subscriberInfo);
        }
        printLogs("onVideoDisableWarning");
    }

    @Override
    public void onVideoDisableWarningLifted(SubscriberKit subscriber) {

        String streamId = Utils.getStreamIdBySubscriber(subscriber);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriber.getSession()));
            }
            sendEventMap(this.getReactApplicationContext(), subscriberPreface + "onVideoDisableWarningLifted", subscriberInfo);
        }
        printLogs("onVideoDisableWarningLifted");
    }

    @Override
    public void onVideoDataReceived(SubscriberKit subscriber) {

        String streamId = Utils.getStreamIdBySubscriber(subscriber);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriber.getSession()));
            }
            sendEventMap(this.getReactApplicationContext(), subscriberPreface + "onVideoDataReceived", subscriberInfo);
        }
    }

    @Override
    public void onStreamHasAudioChanged(Session session, Stream stream, boolean Audio) {

        WritableMap eventData = EventUtils.prepareStreamPropertyChangedEventData("hasAudio", !Audio, Audio, stream, session);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onStreamPropertyChanged", eventData);
        printLogs("onStreamHasAudioChanged");
    }
    @Override
    public void onStreamHasVideoChanged(Session session, Stream stream, boolean Video) {

        WritableMap eventData = EventUtils.prepareStreamPropertyChangedEventData("hasVideo", !Video, Video, stream, session);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onStreamPropertyChanged", eventData);
        printLogs("onStreamHasVideoChanged");
    }

    @Override
    public void onStreamVideoDimensionsChanged(Session session, Stream stream, int width, int height) {
        ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
        Stream mStream = mSubscriberStreams.get(stream.getStreamId());
        WritableMap oldVideoDimensions = Arguments.createMap();
        if ( mStream != null ){
            oldVideoDimensions.putInt("height", mStream.getVideoHeight());
            oldVideoDimensions.putInt("width", mStream.getVideoWidth());
        }
        WritableMap newVideoDimensions = Arguments.createMap();
        newVideoDimensions.putInt("height", height);
        newVideoDimensions.putInt("width", width);
        WritableMap eventData = EventUtils.prepareStreamPropertyChangedEventData("videoDimensions", oldVideoDimensions, newVideoDimensions, stream, session);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onStreamPropertyChanged", eventData);
        printLogs("onStreamVideoDimensionsChanged");

    }

    @Override
    public void onStreamVideoTypeChanged(Session session, Stream stream, Stream.StreamVideoType videoType) {

        ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
        String oldVideoType = stream.getStreamVideoType().toString();
        WritableMap eventData = EventUtils.prepareStreamPropertyChangedEventData("videoType", oldVideoType, videoType.toString(), stream, session);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onStreamPropertyChanged", eventData);
        printLogs("onStreamVideoTypeChanged");
    }

}
