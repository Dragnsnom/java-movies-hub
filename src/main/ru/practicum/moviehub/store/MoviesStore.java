package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MoviesStore {
    private final Map<Integer, Movie> movies = new ConcurrentHashMap<>();
    private int nextId = 1;

    public void clear() {
        movies.clear();
        nextId = 1;
    }

    public List<Movie> getAllMovies() {
        return new ArrayList<>(movies.values());
    }

    public Optional<Movie> getMovieById(int id) {
        return Optional.ofNullable(movies.get(id));
    }

    public Movie addMovie(Movie movie) {
        movie.setId(nextId++);
        movies.put(movie.getId(), movie);
        return movie;
    }

    public boolean deleteMovie(int id) {
        return movies.remove(id) != null;
    }

    public List<Movie> getMoviesByYear(int year) {
        return movies.values().stream()
                .filter(movie -> movie.getYear() == year)
                .collect(Collectors.toList());
    }
}