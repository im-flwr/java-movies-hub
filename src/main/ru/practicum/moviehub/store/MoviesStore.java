package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MoviesStore {
    private final Map<Long, Movie> movies = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public List<Movie> findAll() {
        return new ArrayList<>(movies.values());
    }

    public List<Movie> findByYear(int year) {
        List<Movie> result = new ArrayList<>();
        for (Movie movie : movies.values()) {
            if (movie.getYear() == year) {
                result.add(movie);
            }
        }
        return result;
    }

    public Movie add(String title, int year) {
        Movie movie = new Movie(nextId.getAndIncrement(), title, year);
        movies.put(movie.getId(), movie);
        return movie;
    }

    public Optional<Movie> findById(long id) {
        return Optional.ofNullable(movies.get(id));
    }

    public boolean delete(long id) {
        return movies.remove(id) != null;
    }

    public void clear() {
        movies.clear();
        nextId.set(1);
    }
}