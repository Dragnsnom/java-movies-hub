package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.util.Optional;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;

public class MovieHandler extends BaseHttpHandler {

    public MovieHandler(MoviesStore store) {
        super(store);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String[] pathParts = path.split("/");

        if (pathParts.length < 3) {
            sendError(exchange, HTTP_BAD_REQUEST, ERROR_INVALID_REQUEST);
            return;
        }

        String idStr = pathParts[2];
        Integer id = parseId(idStr, exchange);
        if (id == null) {
            return;
        }

        if ("GET".equals(method)) {
            handleGetMovie(exchange, id);
        } else if ("DELETE".equals(method)) {
            handleDeleteMovie(exchange, id);
        } else {
            sendMethodNotAllowed(exchange);
        }
    }

    private void handleGetMovie(HttpExchange exchange, int id) throws IOException {
        Optional<Movie> movieOpt = store.getMovieById(id);
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
}