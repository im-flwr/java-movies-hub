package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Year;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static final Gson GSON = new Gson();

    private static MoviesServer server;
    private static MoviesStore store;
    private static HttpClient client;

    @BeforeAll
    static void beforeAll() {
        store = new MoviesStore();
        server = new MoviesServer(store, 8080);
        server.start();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @BeforeEach
    void beforeEach() {
        store.clear();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void getMoviesWhenEmptyReturnsEmptyArray() throws Exception {
        HttpResponse<String> response = send("GET", "/movies", null, null);
        assertEquals(200, response.statusCode());
        assertEquals("application/json; charset=UTF-8", response.headers().firstValue("Content-Type").orElse(""));

        Movie[] movies = GSON.fromJson(response.body(), Movie[].class);
        assertEquals(0, movies.length);
    }

    @Test
    void postGetAndDeleteMovieWorkTogether() throws Exception {
        Movie movie = new Movie(0, "Matrix", 1999);
        HttpResponse<String> created = send("POST", "/movies", "application/json", GSON.toJson(movie));
        assertEquals(201, created.statusCode());

        Movie createdMovie = GSON.fromJson(created.body(), Movie.class);
        assertEquals("Matrix", createdMovie.getTitle());
        assertTrue(createdMovie.getId() > 0);

        HttpResponse<String> found = send("GET", "/movies/" + createdMovie.getId(), null, null);
        assertEquals(200, found.statusCode());
        Movie foundMovie = GSON.fromJson(found.body(), Movie.class);
        assertEquals("Matrix", foundMovie.getTitle());

        HttpResponse<String> deleted = send("DELETE", "/movies/" + createdMovie.getId(), null, null);
        assertEquals(204, deleted.statusCode());

        HttpResponse<String> missing = send("GET", "/movies/" + createdMovie.getId(), null, null);
        assertEquals(404, missing.statusCode());
    }

    @Test
    void getMoviesReturnsAddedMovies() throws Exception {
        send("POST", "/movies", "application/json", GSON.toJson(new Movie(0, "Matrix", 1999)));
        send("POST", "/movies", "application/json", GSON.toJson(new Movie(0, "Arrival", 2016)));

        HttpResponse<String> response = send("GET", "/movies", null, null);
        assertEquals(200, response.statusCode());

        Movie[] movies = GSON.fromJson(response.body(), Movie[].class);
        assertEquals(2, movies.length);
    }

    @Test
    void getMoviesByYearReturnsMatchingMovies() throws Exception {
        send("POST", "/movies", "application/json", GSON.toJson(new Movie(0, "Matrix", 1999)));
        send("POST", "/movies", "application/json", GSON.toJson(new Movie(0, "Titanic", 1997)));

        HttpResponse<String> response = send("GET", "/movies?year=1999", null, null);
        assertEquals(200, response.statusCode());

        Movie[] movies = GSON.fromJson(response.body(), Movie[].class);
        assertEquals(1, movies.length);
        assertEquals("Matrix", movies[0].getTitle());
    }

    @Test
    void getMoviesByYearReturnsEmptyArray() throws Exception {
        HttpResponse<String> response = send("GET", "/movies?year=1999", null, null);
        assertEquals(200, response.statusCode());

        Movie[] movies = GSON.fromJson(response.body(), Movie[].class);
        assertEquals(0, movies.length);
    }

    @Test
    void getMoviesByYearWithBadYearReturnsError() throws Exception {
        HttpResponse<String> response = send("GET", "/movies?year=abc", null, null);
        assertEquals(400, response.statusCode());

        ErrorResponse error = GSON.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Некорректный параметр запроса - 'year'", error.getError());
    }

    @Test
    void postMovieWithEmptyTitleReturnsError() throws Exception {
        HttpResponse<String> response = send("POST", "/movies", "application/json",
                GSON.toJson(new Movie(0, "", 2000)));
        assertEquals(422, response.statusCode());

        ErrorResponse error = GSON.fromJson(response.body(), ErrorResponse.class);
        assertTrue(error.getDetails().size() > 0);
    }

    @Test
    void postMovieWithLongTitleReturnsError() throws Exception {
        String title = "a".repeat(101);
        HttpResponse<String> response = send("POST", "/movies", "application/json",
                GSON.toJson(new Movie(0, title, 2000)));
        assertEquals(422, response.statusCode());

        ErrorResponse error = GSON.fromJson(response.body(), ErrorResponse.class);
        assertTrue(error.getDetails().stream().anyMatch(d -> d.contains("100")));
    }

    @Test
    void postMovieWithOldYearReturnsError() throws Exception {
        HttpResponse<String> response = send("POST", "/movies", "application/json",
                GSON.toJson(new Movie(0, "Movie", 1887)));
        assertEquals(422, response.statusCode());

        ErrorResponse error = GSON.fromJson(response.body(), ErrorResponse.class);
        assertTrue(error.getDetails().stream().anyMatch(d -> d.contains("год")));
    }

    @Test
    void postMovieWithFutureYearReturnsError() throws Exception {
        int invalidYear = Year.now().getValue() + 2;
        HttpResponse<String> response = send("POST", "/movies", "application/json",
                GSON.toJson(new Movie(0, "Movie", invalidYear)));
        assertEquals(422, response.statusCode());

        ErrorResponse error = GSON.fromJson(response.body(), ErrorResponse.class);
        assertTrue(error.getDetails().size() > 0);
    }

    @Test
    void postMovieWithWrongContentTypeReturnsError() throws Exception {
        HttpResponse<String> response = send("POST", "/movies", "text/plain",
                GSON.toJson(new Movie(0, "Movie", 2000)));
        assertEquals(415, response.statusCode());

        ErrorResponse error = GSON.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Неподдерживаемый тип содержимого", error.getError());
    }

    @Test
    void postMovieWithInvalidJsonReturnsError() throws Exception {
        HttpResponse<String> response = send("POST", "/movies", "application/json", "not json");
        assertEquals(400, response.statusCode());

        ErrorResponse error = GSON.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Некорректный JSON", error.getError());
    }

    @Test
    void getMovieWithBadIdReturnsError() throws Exception {
        HttpResponse<String> response = send("GET", "/movies/abc", null, null);
        assertEquals(400, response.statusCode());

        ErrorResponse error = GSON.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Некорректный ID", error.getError());
    }

    @Test
    void deleteMovieWithUnknownIdReturnsError() throws Exception {
        HttpResponse<String> response = send("DELETE", "/movies/999", null, null);
        assertEquals(404, response.statusCode());

        ErrorResponse error = GSON.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Фильм не найден", error.getError());
    }

    @Test
    void unsupportedMethodReturnsError() throws Exception {
        HttpResponse<String> response = send("PUT", "/movies", "application/json",
                GSON.toJson(new Movie(0, "any", 2000)));
        assertEquals(405, response.statusCode());

        ErrorResponse error = GSON.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Метод не поддерживается", error.getError());
    }

    private HttpResponse<String> send(String method, String path, String contentType, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path));
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        return client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }
}