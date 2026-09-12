package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CONTENT_TYPE = "application/json; charset=UTF-8";
    protected final Gson gson = new Gson();

    protected void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    protected void sendNoContent(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }
}