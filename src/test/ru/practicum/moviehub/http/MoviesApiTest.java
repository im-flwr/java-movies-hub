package ru.practicum.moviehub.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Year;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
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
        assertEquals("[]", response.body());
        assertEquals("application/json; charset=UTF-8", response.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    void postGetAndDeleteMovieWorkTogether() throws Exception {
        HttpResponse<String> created = send("POST", "/movies", "application/json", "{\"title\":\"Matrix\",\"year\":1999}");
        assertEquals(201, created.statusCode());
        assertTrue(created.body().contains("Matrix"));
        HttpResponse<String> found = send("GET", "/movies/1", null, null);
        assertEquals(200, found.statusCode());
        assertTrue(found.body().contains("Matrix"));
        HttpResponse<String> deleted = send("DELETE", "/movies/1", null, null);
        assertEquals(204, deleted.statusCode());
        HttpResponse<String> missing = send("GET", "/movies/1", null, null);
        assertEquals(404, missing.statusCode());
    }

    @Test
    void getMoviesReturnsAddedMovies() throws Exception {
        send("POST", "/movies", "application/json", "{\"title\":\"Matrix\",\"year\":1999}");
        send("POST", "/movies", "application/json", "{\"title\":\"Arrival\",\"year\":2016}");

        HttpResponse<String> response = send("GET", "/movies", null, null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Matrix"));
        assertTrue(response.body().contains("Arrival"));
    }

    @Test
    void getMoviesByYearReturnsMatchingMovies() throws Exception {
        send("POST", "/movies", "application/json", "{\"title\":\"Matrix\",\"year\":1999}");
        send("POST", "/movies", "application/json", "{\"title\":\"Titanic\",\"year\":1997}");

        HttpResponse<String> response = send("GET", "/movies?year=1999", null, null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Matrix"));
        assertFalse(response.body().contains("Titanic"));
    }

    @Test
    void getMoviesByYearReturnsEmptyArray() throws Exception {
        HttpResponse<String> response = send("GET", "/movies?year=1999", null, null);

        assertEquals(200, response.statusCode());
        assertEquals("[]", response.body());
    }

    @Test
    void getMoviesByYearWithBadYearReturnsError() throws Exception {
        HttpResponse<String> response = send("GET", "/movies?year=abc", null, null);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("error"));
    }

    @Test
    void postMovieWithEmptyTitleReturnsError() throws Exception {
        HttpResponse<String> response = send("POST", "/movies", "application/json", "{\"title\":\"\",\"year\":2000}");

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("details"));
    }

    @Test
    void postMovieWithLongTitleReturnsError() throws Exception {
        String title = "a".repeat(101);
        String body = "{\"title\":\"" + title + "\",\"year\":2000}";

        HttpResponse<String> response = send("POST", "/movies", "application/json", body);

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("100"));
    }

    @Test
    void postMovieWithOldYearReturnsError() throws Exception {
        HttpResponse<String> response = send("POST", "/movies", "application/json", "{\"title\":\"Movie\",\"year\":1887}");

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("год"));
    }

    @Test
    void postMovieWithFutureYearReturnsError() throws Exception {
        int invalidYear = Year.now().getValue() + 2;
        String body = "{\"title\":\"Movie\",\"year\":" + invalidYear + "}";

        HttpResponse<String> response = send("POST", "/movies", "application/json", body);

        assertEquals(422, response.statusCode());
    }

    @Test
    void postMovieWithWrongContentTypeReturnsError() throws Exception {
        HttpResponse<String> response = send("POST", "/movies", "text/plain", "{\"title\":\"Movie\",\"year\":2000}");

        assertEquals(415, response.statusCode());
        assertTrue(response.body().contains("error"));
    }

    @Test
    void postMovieWithInvalidJsonReturnsError() throws Exception {
        HttpResponse<String> response = send("POST", "/movies", "application/json", "not json");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Некорректный JSON"));
    }

    @Test
    void getMovieWithBadIdReturnsError() throws Exception {
        HttpResponse<String> response = send("GET", "/movies/abc", null, null);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Некорректный ID"));
    }

    @Test
    void deleteMovieWithUnknownIdReturnsError() throws Exception {
        HttpResponse<String> response = send("DELETE", "/movies/999", null, null);

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("Фильм не найден"));
    }

    @Test
    void unsupportedMethodReturnsError() throws Exception {
        HttpResponse<String> response = send("PUT", "/movies", "application/json", "{}");

        assertEquals(405, response.statusCode());
        assertTrue(response.body().contains("error"));
    }

    private HttpResponse<String> send(String method, String path, String contentType, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path));
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        HttpRequest.BodyPublisher publisher;
        if (body == null) {
            publisher = HttpRequest.BodyPublishers.noBody();
        } else {
            publisher = HttpRequest.BodyPublishers.ofString(body);
        }
        return client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }
}