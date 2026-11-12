package com.mrsrik.ai_powered_commerce.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Records, per test class, the JDBC URL seen, to prove one container serves the whole JVM. */
final class SharedContainerRecorder {

    static final Map<String, String> URLS = new ConcurrentHashMap<>();

    private SharedContainerRecorder() {
    }

    static void record(String testClass, String url) {
        URLS.put(testClass, url);
        System.out.println("[container-sharing] " + testClass + " -> " + url
                + " (container id " + AbstractPostgresIT.POSTGRES.getContainerId() + ")");
    }
}
