package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.net.HttpURLConnection.*;

public abstract class BaseHttpHandler implements HttpHandler {

    protected static final String CONTENT_TYPE_JSON_CHARSET = "application/json; charset=UTF-8";
    protected static final String CONTENT_TYPE_JSON = "application/json";
    protected static final String CONTENT_TYPE = "Content-Type";

    protected static final String ERROR_INVALID_ID = "Некорректный ID";
    protected static final String ERROR_MOVIE_NOT_FOUND = "Фильм не найден";
    protected static final String ERROR_METHOD_NOT_ALLOWED = "Метод не поддерживается";
    protected static final String ERROR_UNSUPPORTED_MEDIA_TYPE = "Unsupported Media Type";
    protected static final String ERROR_INVALID_JSON = "Некорректный JSON";
    protected static final String ERROR_INVALID_YEAR_PARAM = "Некорректный параметр запроса - 'year'";
    protected static final String ERROR_VALIDATION = "Ошибка валидации";
    protected static final String ERROR_INVALID_REQUEST = "Некорректный запрос";

    protected static final String VALIDATION_TITLE_EMPTY = "название не должно быть пустым";
    protected static final String VALIDATION_TITLE_TOO_LONG = "название не должно превышать 100 символов";
    protected static final String VALIDATION_YEAR_INVALID = "год должен быть между 1888 и ";

    protected final Gson gson;
    protected final MoviesStore store;

    public BaseHttpHandler(MoviesStore store) {
        this.store = store;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    protected void sendResponse(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
        byte[] responseBytes = responseBody != null ?
                responseBody.getBytes(StandardCharsets.UTF_8) : new byte[0];
        exchange.getResponseHeaders().set(CONTENT_TYPE, CONTENT_TYPE_JSON_CHARSET);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        if (responseBytes.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
        exchange.close();
    }

    protected void sendError(HttpExchange exchange, int statusCode, String error) throws IOException {
        ErrorResponse errorResponse = new ErrorResponse(error, null);
        sendResponse(exchange, statusCode, gson.toJson(errorResponse));
    }

    protected void sendValidationError(HttpExchange exchange, List<String> details) throws IOException {
        ErrorResponse errorResponse = new ErrorResponse(ERROR_VALIDATION, details);
        sendResponse(exchange, HTTP_INTERNAL_ERROR, gson.toJson(errorResponse));
    }

    protected Integer parseId(String idStr, HttpExchange exchange) throws IOException {
        try {
            return Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            sendError(exchange, HTTP_BAD_REQUEST, ERROR_INVALID_ID);
            return null;
        }
    }

    protected void sendNotFound(HttpExchange exchange) throws IOException {
        sendError(exchange, HTTP_NOT_FOUND, ERROR_MOVIE_NOT_FOUND);
    }

    protected void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendError(exchange, HTTP_BAD_METHOD, ERROR_METHOD_NOT_ALLOWED);
    }
}