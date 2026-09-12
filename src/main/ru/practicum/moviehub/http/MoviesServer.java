package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {
    private final HttpServer server;

    public MoviesServer(MoviesStore store, int port) {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        } catch (IOException exception) {
            throw new RuntimeException("Не удалось создать HTTP-сервер", exception);
        }
        server.createContext("/movies", new MoviesHandler(store));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }
}