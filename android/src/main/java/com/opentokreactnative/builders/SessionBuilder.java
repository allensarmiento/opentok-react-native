package com.opentokreactnative.builders;

import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReactApplicationContext;
import com.opentok.android.Session;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionBuilder {
    public static Session buildSession(ReactApplicationContext reactContext, String apiKey, String sessionId, ReadableMap sessionOptions) {
        final boolean useTextureViews = sessionOptions.getBoolean("useTextureViews");
        final boolean isCamera2Capable = sessionOptions.getBoolean("isCamera2Capable");
        final boolean connectionEventsSuppressed = sessionOptions.getBoolean("connectionEventsSuppressed");
        final boolean ipWhitelist = sessionOptions.getBoolean("ipWhitelist");
        // Note: IceConfig is an additional property not supported at the moment. 
        // final ReadableMap iceConfig = sessionOptions.getMap("iceConfig");
        // final List<Session.Builder.IceServer> iceConfigServerList = (List<Session.Builder.IceServer>) iceConfig.getArray("customServers");
        // final Session.Builder.IncludeServers iceConfigServerConfig; // = iceConfig.getString("includeServers");
        final String proxyUrl = sessionOptions.getString("proxyUrl");

        return new Session.Builder(reactContext, apiKey, sessionId)
            .sessionOptions(new Session.SessionOptions() {
                @Override 
                public boolean useTextureViews() {
                    return useTextureViews;
                }

                @Override 
                public boolean isCamera2Capable() {
                    return isCamera2Capable;
                }
            })
            .connectionEventsSuppressed(connectionEventsSuppressed)
            // Note: setCustomIceServers is an additional property not supported at the moment.
            // .setCustomIceServers(serverList, config)
            .setIpWhitelist(ipWhitelist)
            .setProxyUrl(proxyUrl)
            .build();
    }
}
