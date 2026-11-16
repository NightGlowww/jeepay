package com.jeequan.jeepay.pay.channel.starpos;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Local HTTP server used by StarPOS channel tests.
 */
class StarposTestServer implements AutoCloseable {

    private static final Response DEFAULT_RESPONSE = new Response(200, "{}", 0L);

    private final HttpServer server;
    private final ExecutorService executorService;
    private final Queue<Response> responses = new ConcurrentLinkedQueue<>();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

    private StarposTestServer(HttpServer server, ExecutorService executorService) {
        this.server = server;
        this.executorService = executorService;
    }

    static StarposTestServer start() {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
            ExecutorService executorService = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "starpos-test-server");
                thread.setDaemon(true);
                return thread;
            });
            StarposTestServer testServer = new StarposTestServer(httpServer, executorService);
            httpServer.createContext("/", testServer::handle);
            httpServer.setExecutor(executorService);
            httpServer.start();
            return testServer;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    URL url(String path) {
        try {
            return new URL("http", "127.0.0.1", server.getAddress().getPort(), path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void enqueueJson(int statusCode, String json) {
        enqueueJson(statusCode, json, 0L);
    }

    void enqueueJson(int statusCode, String json, long delayMillis) {
        responses.add(new Response(statusCode, json, delayMillis));
    }

    CountDownLatch enqueueJsonWithDelayedBody(int statusCode, String json, long bodyDelayMillis) {
        Response response = new Response(statusCode, json, 0L, bodyDelayMillis);
        responses.add(response);
        return response.headersSent;
    }

    List<RecordedRequest> getRequests() {
        return Collections.unmodifiableList(requests);
    }

    RecordedRequest takeRequest() {
        if (requests.isEmpty()) {
            throw new IllegalStateException("No recorded StarPOS request");
        }
        return requests.get(0);
    }

    @Override
    public void close() {
        server.stop(0);
        executorService.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.add(RecordedRequest.from(exchange));

        Response response = responses.poll();
        if (response == null) {
            response = DEFAULT_RESPONSE;
        }
        response.delay();

        byte[] responseBody = response.json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(response.statusCode, responseBody.length);
        response.headersSent.countDown();
        try {
            Response.delay(response.bodyDelayMillis);
            exchange.getResponseBody().write(responseBody);
        } finally {
            exchange.close();
        }
    }

    static class RecordedRequest {

        private final String path;
        private final String jsonBody;
        private final Map<String, List<String>> headers;

        private RecordedRequest(String path, String jsonBody, Map<String, List<String>> headers) {
            this.path = path;
            this.jsonBody = jsonBody;
            this.headers = headers;
        }

        static RecordedRequest from(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().toString();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, List<String>> headers = copyHeaders(exchange.getRequestHeaders());
            return new RecordedRequest(path, body, headers);
        }

        String getPath() {
            return path;
        }

        String getJsonBody() {
            return jsonBody;
        }

        Map<String, List<String>> getHeaders() {
            return headers;
        }

        String getFirstHeader(String name) {
            List<String> values = headers.get(name);
            if (values == null || values.isEmpty()) {
                return null;
            }
            return values.get(0);
        }

        private static Map<String, List<String>> copyHeaders(Headers source) {
            Headers copy = new Headers();
            for (Map.Entry<String, List<String>> entry : source.entrySet()) {
                copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
    }

    private static class Response {

        private final int statusCode;
        private final String json;
        private final long delayMillis;
        private final long bodyDelayMillis;
        private final CountDownLatch headersSent = new CountDownLatch(1);

        private Response(int statusCode, String json, long delayMillis) {
            this(statusCode, json, delayMillis, 0L);
        }

        private Response(int statusCode, String json, long delayMillis, long bodyDelayMillis) {
            this.statusCode = statusCode;
            this.json = json;
            this.delayMillis = delayMillis;
            this.bodyDelayMillis = bodyDelayMillis;
        }

        private void delay() {
            delay(delayMillis);
        }

        private static void delay(long delayMillis) {
            if (delayMillis <= 0L) {
                return;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
