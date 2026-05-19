/*
 * Copyright (C) 2018 Christopher J. Stehno <chris@stehno.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stehno.gradle.depchecker

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.*

/**
 * HttpHeadClient is @CompileStatic so Groovy metaclass mocking cannot intercept
 * calls within it. These tests use JDK's built-in com.sun.net.httpserver.HttpServer
 * (available in jdk.httpserver without extra dependencies) to exercise the real
 * HTTP code paths.
 */
class HttpHeadClientTest {

    // A fixed coordinate used across all tests.
    // toPathSuffix() → 'org/example/foo/1.0/foo-1.0.jar'
    private static final DependencyCoordinate COORD = new DependencyCoordinate('org.example', 'foo', '1.0')

    private HttpServer server
    private int port

    @BeforeEach
    void startServer() {
        server = HttpServer.create(new InetSocketAddress(0), 0)
        port = server.address.port
        server.executor = null
        server.start()
    }

    @AfterEach
    void stopServer() {
        server.stop(0)
    }

    // --- exists() with a live server ---

    @Test
    void 'exists: returns true when server responds 200'() {
        respondWith(200)

        assertThat(HttpHeadClient.exists(["http://localhost:${port}/repo"], COORD)).isTrue()
    }

    @Test
    void 'exists: returns false when server responds 404'() {
        respondWith(404)

        assertThat(HttpHeadClient.exists(["http://localhost:${port}/repo"], COORD)).isFalse()
    }

    @Test
    void 'exists: returns false when server responds 500'() {
        respondWith(500)

        assertThat(HttpHeadClient.exists(["http://localhost:${port}/repo"], COORD)).isFalse()
    }

    // --- exists() with empty or exhausted URL list ---

    @Test
    void 'exists: returns false for empty base URL list'() {
        // any() on an empty collection is false — no HTTP call is made
        assertThat(HttpHeadClient.exists([], COORD)).isFalse()
    }

    @Test
    void 'exists: returns false when all URLs respond non-200'() {
        respondWith(404)

        assertThat(HttpHeadClient.exists(["http://localhost:${port}/repo1", "http://localhost:${port}/repo2"], COORD))
                .isFalse()
    }

    // --- multi-URL fallback (any() short-circuit) ---

    @Test
    void 'exists: returns true when first URL fails but second responds 200'() {
        respondWith(200)

        // Bind a socket to grab a free port, then close it so nothing listens there.
        // exists() will get a ConnectException on the first URL, catch it, then try the second.
        int deadPort = allocateFreePort()

        assertThat(HttpHeadClient.exists(["http://localhost:${deadPort}/repo", "http://localhost:${port}/repo"], COORD))
                .isTrue()
    }

    // --- exception path inside check() ---

    @Test
    void 'exists: returns false and does not throw when connection is refused'() {
        // Nothing listens on deadPort → ConnectException is caught inside check(), returns false
        int deadPort = allocateFreePort()

        assertThat(HttpHeadClient.exists(["http://localhost:${deadPort}/repo"], COORD)).isFalse()
    }

    // --- URL construction (exists delegates path-building to toPathSuffix) ---

    @Test
    void 'exists: constructs the correct artifact URL from the coordinate'() {
        List<String> receivedPaths = []

        server.createContext('/') { HttpExchange ex ->
            receivedPaths << ex.requestURI.path
            ex.sendResponseHeaders(200, -1)
            ex.close()
        }

        HttpHeadClient.exists(["http://localhost:${port}/repo"], COORD)

        // toPathSuffix converts dots in group to slashes
        assertThat(receivedPaths).containsExactly('/repo/org/example/foo/1.0/foo-1.0.jar')
    }

    // --- helpers ---

    /** Register a catch-all context that replies with the given HTTP status. */
    private void respondWith(int status) {
        server.createContext('/') { HttpExchange ex ->
            ex.sendResponseHeaders(status, -1)
            ex.close()
        }
    }

    /** Allocate a free port and immediately release it so nothing is listening on it. */
    private static int allocateFreePort() {
        ServerSocket ss = new ServerSocket(0)
        int p = ss.localPort
        ss.close()
        p
    }
}
