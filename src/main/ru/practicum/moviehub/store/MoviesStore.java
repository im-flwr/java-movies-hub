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
        return movies.values().stream()
                .filter(movie -> movie.getYear() == year)
                .toList();
    }

    public Movie add(Movie movie) {
        movie.setId(nextId.getAndIncrement());
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