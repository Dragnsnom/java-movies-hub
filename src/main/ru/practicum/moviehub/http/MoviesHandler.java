package ru.practicum.moviehub.http;

import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNSUPPORTED_TYPE;

public class MoviesHandler extends BaseHttpHandler {

    public MoviesHandler(MoviesStore store) {
        super(store);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (path.equals("/movies")) {
            if ("GET".equals(method)) {
                handleGetMovies(exchange);
            } else if ("POST".equals(method)) {
                handlePostMovie(exchange);
            } else {
                sendMethodNotAllowed(exchange);
            }
        } else if (path.matches("/movies/\\d+")) {
            String idStr = path.substring(8);
            Integer id = parseId(idStr, exchange);
            if (id == null) {
                return;
            }
            if ("GET".equals(method)) {
                handleGetMovieById(exchange, id);
            } else if ("DELETE".equals(method)) {
                handleDeleteMovie(exchange, id);
            } else {
                sendMethodNotAllowed(exchange);
            }
        } else {
            sendNotFound(exchange);
        }
    }

    private void handleGetMovies(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();

        if (query != null && query.startsWith("year=")) {
            String yearParam = query.substring(5);
            handleGetMoviesByYear(exchange, yearParam);
        } else {
            List<Movie> movies = store.getAllMovies();
            String responseBody = gson.toJson(movies);
            sendResponse(exchange, HTTP_OK, responseBody);
        }
    }

    private void handleGetMoviesByYear(HttpExchange exchange, String yearParam) throws IOException {
        try {
            int year = Integer.parseInt(yearParam);
            List<Movie> movies = store.getMoviesByYear(year);
            String responseBody = gson.toJson(movies);
            sendResponse(exchange, HTTP_OK, responseBody);
        } catch (NumberFormatException e) {
            sendError(exchange, HTTP_BAD_REQUEST, ERROR_INVALID_YEAR_PARAM);
        }
    }

    private void handleGetMovieById(HttpExchange exchange, int id) throws IOException {
        var movieOpt = store.getMovieById(id);
        if (movieOpt.isPresent()) {
            sendResponse(exchange, HTTP_OK, gson.toJson(movieOpt.get()));
        } else {
            sendNotFound(exchange);
        }
    }

    private void handleDeleteMovie(HttpExchange exchange, int id) throws IOException {
        if (store.deleteMovie(id)) {
            sendResponse(exchange, HTTP_NO_CONTENT, null);
        } else {
            sendNotFound(exchange);
        }
    }

    private void handlePostMovie(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst(CONTENT_TYPE);
        if (contentType == null || !contentType.equals(CONTENT_TYPE_JSON)) {
            sendError(exchange, HTTP_UNSUPPORTED_TYPE, ERROR_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        InputStream is = exchange.getRequestBody();
        String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        try {
            Movie movie = gson.fromJson(requestBody, Movie.class);
            List<String> validationErrors = validateMovie(movie);

            if (!validationErrors.isEmpty()) {
                sendValidationError(exchange, validationErrors);
                return;
            }

            Movie createdMovie = store.addMovie(movie);
            String responseBody = gson.toJson(createdMovie);
            sendResponse(exchange, HTTP_CREATED, responseBody);
        } catch (JsonSyntaxException e) {
            sendError(exchange, HTTP_BAD_REQUEST, ERROR_INVALID_JSON);
        }
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();
        int currentYearPlusOne = Year.now().getValue() + 1;

        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            errors.add(VALIDATION_TITLE_EMPTY);
        } else if (movie.getTitle().length() > 100) {
            errors.add(VALIDATION_TITLE_TOO_LONG);
        }

        if (movie.getYear() < 1888 || movie.getYear() > currentYearPlusOne) {
            errors.add(VALIDATION_YEAR_INVALID + currentYearPlusOne);
        }

        return errors;
    }
}