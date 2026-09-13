package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            switch (method) {
                case "GET":
                    handleGet(exchange);
                    return;
                case "POST":
                    handlePost(exchange);
                    return;
                case "DELETE":
                    handleDelete(exchange);
                    return;
                default:
                    sendError(exchange, 405, "Метод не поддерживается");
            }
        } catch (Exception exception) {
            if (!exchange.getResponseHeaders().containsKey("Content-Type")) {
                sendError(exchange, 500, "Внутренняя ошибка сервера");
            }
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        String[] parts = getPathParts(exchange);

        if (parts.length == 2 && parts[1].equals("movies")) {
            handleGetCollection(exchange);
            return;
        }

        if (parts.length == 3 && parts[1].equals("movies") && !parts[2].isEmpty()) {
            handleGetMovie(exchange, parts[2]);
            return;
        }

        sendError(exchange, 404, "Ресурс не найден");
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        String[] parts = getPathParts(exchange);

        if (parts.length == 2 && parts[1].equals("movies")) {
            handlePostMovie(exchange);
            return;
        }

        sendError(exchange, 404, "Ресурс не найден");
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        String[] parts = getPathParts(exchange);

        if (parts.length == 3 && parts[1].equals("movies") && !parts[2].isEmpty()) {
            handleDeleteMovie(exchange, parts[2]);
            return;
        }

        sendError(exchange, 404, "Ресурс не найден");
    }

    private void handleGetCollection(HttpExchange exchange) throws IOException {
        Integer year = getYear(exchange.getRequestURI());
        if (year == null && hasYearParameter(exchange.getRequestURI())) {
            sendError(exchange, 400, "Некорректный параметр запроса - 'year'");
            return;
        }
        List<Movie> movies = year == null
                ? store.findAll()
                : store.findByYear(year);
        sendJson(exchange, 200, gson.toJson(movies));
    }

    private void handleGetMovie(HttpExchange exchange, String idText) throws IOException {
        Long id = parseId(idText);
        if (id == null) {
            sendError(exchange, 400, "Некорректный ID");
            return;
        }
        Movie movie = store.findById(id).orElse(null);
        if (movie == null) {
            sendError(exchange, 404, "Фильм не найден");
        } else {
            sendJson(exchange, 200, gson.toJson(movie));
        }
    }

    private void handlePostMovie(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            sendError(exchange, 415, "Неподдерживаемый тип содержимого");
            return;
        }

        Movie movie;
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            movie = gson.fromJson(body, Movie.class);
        } catch (Exception exception) {
            sendError(exchange, 400, "Некорректный JSON");
            return;
        }

        if (movie == null) {
            sendError(exchange, 400, "Некорректный JSON");
            return;
        }

        List<String> details = validate(movie);
        if (!details.isEmpty()) {
            sendError(exchange, 422, "Ошибка валидации", details);
            return;
        }

        Movie created = store.add(movie);
        sendJson(exchange, 201, gson.toJson(created));
    }

    private void handleDeleteMovie(HttpExchange exchange, String idText) throws IOException {
        Long id = parseId(idText);
        if (id == null) {
            sendError(exchange, 400, "Некорректный ID");
            return;
        }
        if (store.delete(id)) {
            sendNoContent(exchange);
        } else {
            sendError(exchange, 404, "Фильм не найден");
        }
    }

    private String[] getPathParts(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        return path.split("/");
    }

    private Long parseId(String idText) {
        try {
            return Long.parseLong(idText);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<String> validate(Movie movie) {
        List<String> details = new ArrayList<>();
        int currentYear = Year.now().getValue();
        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            details.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > 100) {
            details.add("название не должно быть длиннее 100 символов");
        }
        if (movie.getYear() < 1888 || movie.getYear() > currentYear + 1) {
            details.add("год должен быть между 1888 и " + (currentYear + 1));
        }
        return details;
    }

    private boolean hasYearParameter(URI uri) {
        return uri.getRawQuery() != null
                && Arrays.stream(uri.getRawQuery().split("&"))
                .anyMatch(parameter -> parameter.startsWith("year="));
    }

    private Integer getYear(URI uri) {
        if (uri.getRawQuery() == null) {
            return null;
        }
        for (String parameter : uri.getRawQuery().split("&")) {
            String[] pair = parameter.split("=", 2);
            if (pair.length == 2 && pair[0].equals("year")) {
                try {
                    return Integer.parseInt(URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                } catch (NumberFormatException exception) {
                    return null;
                }
            }
        }
        return null;
    }

    private void sendError(HttpExchange exchange, int status, String error) throws IOException {
        sendJson(exchange, status, gson.toJson(new ErrorResponse(error)));
    }

    private void sendError(HttpExchange exchange, int status, String error, List<String> details)
            throws IOException {
        sendJson(exchange, status, gson.toJson(new ErrorResponse(error, details)));
    }
}