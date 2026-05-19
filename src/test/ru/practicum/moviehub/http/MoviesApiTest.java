package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {

    private static MoviesServer server;
    private static MoviesStore store;
    private static HttpClient client;
    private static Gson gson;
    private static int port = 8080;
    private static String baseUrl;

    @BeforeAll
    static void beforeAll() throws IOException {
        store = new MoviesStore();
        server = new MoviesServer(store, port);
        client = HttpClient.newHttpClient();
        gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        baseUrl = "http://localhost:" + port;
        server.start();
    }

    @BeforeEach
    void beforeEach() {
        store.clear();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals(BaseHttpHandler.CONTENT_TYPE_JSON_CHARSET,
                response.headers().firstValue(BaseHttpHandler.CONTENT_TYPE).orElse(""));

        List<Movie> movies = gson.fromJson(response.body(), new ListOfMoviesTypeToken().getType());
        assertTrue(movies.isEmpty());
    }

    @Test
    void getMovies_whenHasMovies_returnsMoviesArray() throws Exception {
        addMovie(new Movie("Inception", 2010));
        addMovie(new Movie("The Matrix", 1999));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        List<Movie> movies = gson.fromJson(response.body(), new ListOfMoviesTypeToken().getType());
        assertEquals(2, movies.size());
    }

    @Test
    void postMovie_whenValid_returnsCreatedMovie() throws Exception {
        Movie newMovie = new Movie("The Dark Knight", 2008);
        String json = gson.toJson(newMovie);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies"))
                .header(BaseHttpHandler.CONTENT_TYPE, BaseHttpHandler.CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertEquals(BaseHttpHandler.CONTENT_TYPE_JSON_CHARSET,
                response.headers().firstValue(BaseHttpHandler.CONTENT_TYPE).orElse(""));

        Movie createdMovie = gson.fromJson(response.body(), Movie.class);
        assertNotNull(createdMovie.getId());
        assertEquals("The Dark Knight", createdMovie.getTitle());
        assertEquals(2008, createdMovie.getYear());

        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies/" + createdMovie.getId()))
                .GET()
                .build();
        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResponse.statusCode());
    }

    @Test
    void postMovie_whenEmptyTitle_returnsValidationError() throws Exception {
        Movie invalidMovie = new Movie("", 2024);
        String json = gson.toJson(invalidMovie);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies"))
                .header(BaseHttpHandler.CONTENT_TYPE, BaseHttpHandler.CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(500, response.statusCode());
        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals(BaseHttpHandler.ERROR_VALIDATION, error.getError());
        assertTrue(error.getDetails().stream()
                .anyMatch(d -> d.contains(BaseHttpHandler.VALIDATION_TITLE_EMPTY)));
    }

    @Test
    void postMovie_whenTitleTooLong_returnsValidationError() throws Exception {
        String longTitle = "A".repeat(101);
        Movie invalidMovie = new Movie(longTitle, 2024);
        String json = gson.toJson(invalidMovie);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies"))
                .header(BaseHttpHandler.CONTENT_TYPE, BaseHttpHandler.CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(500, response.statusCode());
        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals(BaseHttpHandler.ERROR_VALIDATION, error.getError());
        assertTrue(error.getDetails().stream()
                .anyMatch(d -> d.contains(BaseHttpHandler.VALIDATION_TITLE_TOO_LONG)));
    }

    @Test
    void postMovie_whenYearTooLow_returnsValidationError() throws Exception {
        Movie invalidMovie = new Movie("Too Old Movie", 1800);
        String json = gson.toJson(invalidMovie);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies"))
                .header(BaseHttpHandler.CONTENT_TYPE, BaseHttpHandler.CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(500, response.statusCode());
        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals(BaseHttpHandler.ERROR_VALIDATION, error.getError());
        assertTrue(error.getDetails().stream()
                .anyMatch(d -> d.contains(BaseHttpHandler.VALIDATION_YEAR_INVALID + (Year.now().getValue() + 1))));
    }

    @Test
    void postMovie_whenYearTooHigh_returnsValidationError() throws Exception {
        int futureYear = Year.now().getValue() + 2;
        Movie invalidMovie = new Movie("Too Future Movie", futureYear);
        String json = gson.toJson(invalidMovie);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies"))
                .header(BaseHttpHandler.CONTENT_TYPE, BaseHttpHandler.CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(500, response.statusCode());
        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals(BaseHttpHandler.ERROR_VALIDATION, error.getError());
        assertTrue(error.getDetails().stream()
                .anyMatch(d -> d.contains(BaseHttpHandler.VALIDATION_YEAR_INVALID + (Year.now().getValue() + 1))));
    }

    @Test
    void postMovie_whenInvalidContentType_returns415() throws Exception {
        Movie movie = new Movie("Test Movie", 2024);
        String json = gson.toJson(movie);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies"))
                .header(BaseHttpHandler.CONTENT_TYPE, "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(415, response.statusCode());
    }

    @Test
    void postMovie_whenInvalidJson_returns400() throws Exception {
        String invalidJson = "{\"title\": \"Incomplete\", \"year\":}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies"))
                .header(BaseHttpHandler.CONTENT_TYPE, BaseHttpHandler.CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
    }

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        Movie movie = addMovie(new Movie("Pulp Fiction", 1994));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies/" + movie.getId()))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        Movie foundMovie = gson.fromJson(response.body(), Movie.class);
        assertEquals(movie.getId(), foundMovie.getId());
        assertEquals("Pulp Fiction", foundMovie.getTitle());
        assertEquals(1994, foundMovie.getYear());
    }

    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies/99999"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains(BaseHttpHandler.ERROR_MOVIE_NOT_FOUND));
    }

    @Test
    void getMovieById_whenIdNotNumber_returns400() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies/abc"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains(BaseHttpHandler.ERROR_INVALID_ID));
    }

    @Test
    void deleteMovie_whenExists_returns204() throws Exception {
        Movie movie = addMovie(new Movie("Fight Club", 1999));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies/" + movie.getId()))
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(204, response.statusCode());
        assertTrue(response.body().isEmpty());

        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies/" + movie.getId()))
                .GET()
                .build();
        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, getResponse.statusCode());
    }

    @Test
    void deleteMovie_whenNotFound_returns404() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies/99999"))
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains(BaseHttpHandler.ERROR_MOVIE_NOT_FOUND));
    }

    @Test
    void deleteMovie_whenIdNotNumber_returns400() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies/xyz"))
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains(BaseHttpHandler.ERROR_INVALID_ID));
    }

    @Test
    void getMoviesByYear_whenMoviesExist_returnsFilteredMovies() throws Exception {
        addMovie(new Movie("Movie 1999 A", 1999));
        addMovie(new Movie("Movie 1999 B", 1999));
        addMovie(new Movie("Movie 2000", 2000));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies?year=1999"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        List<Movie> movies = gson.fromJson(response.body(), new ListOfMoviesTypeToken().getType());
        assertEquals(2, movies.size());
        assertTrue(movies.stream().allMatch(m -> m.getYear() == 1999));
    }

    @Test
    void getMoviesByYear_whenNoMovies_returnsEmptyArray() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies?year=1800"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        List<Movie> movies = gson.fromJson(response.body(), new ListOfMoviesTypeToken().getType());
        assertTrue(movies.isEmpty());
    }

    @Test
    void getMoviesByYear_whenYearNotNumber_returns400() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies?year=abcd"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertEquals(BaseHttpHandler.CONTENT_TYPE_JSON_CHARSET,
                response.headers().firstValue(BaseHttpHandler.CONTENT_TYPE).orElse(""));

        ErrorResponse errorResponse = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals(BaseHttpHandler.ERROR_INVALID_YEAR_PARAM, errorResponse.getError());
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies"))
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
    }

    @Test
    void allSuccessResponsesHaveCorrectContentType() throws Exception {
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies"))
                .GET()
                .build();
        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(BaseHttpHandler.CONTENT_TYPE_JSON_CHARSET,
                getResponse.headers().firstValue(BaseHttpHandler.CONTENT_TYPE).orElse(""));

        Movie movie = new Movie("Test", 2024);
        String json = gson.toJson(movie);
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies"))
                .header(BaseHttpHandler.CONTENT_TYPE, BaseHttpHandler.CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> postResponse = client.send(postRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(BaseHttpHandler.CONTENT_TYPE_JSON_CHARSET,
                postResponse.headers().firstValue(BaseHttpHandler.CONTENT_TYPE).orElse(""));

        Movie createdMovie = gson.fromJson(postResponse.body(), Movie.class);
        HttpRequest getByIdRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies/" + createdMovie.getId()))
                .GET()
                .build();
        HttpResponse<String> getByIdResponse = client.send(getByIdRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(BaseHttpHandler.CONTENT_TYPE_JSON_CHARSET,
                getByIdResponse.headers().firstValue(BaseHttpHandler.CONTENT_TYPE).orElse(""));
    }

    private Movie addMovie(Movie movie) throws IOException, InterruptedException {
        String json = gson.toJson(movie);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies"))
                .header(BaseHttpHandler.CONTENT_TYPE, BaseHttpHandler.CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return gson.fromJson(response.body(), Movie.class);
    }

    private static class ListOfMoviesTypeToken extends com.google.gson.reflect.TypeToken<List<Movie>> {
    }

    private static class ErrorResponse {
        private String error;
        private List<String> details;

        public String getError() {
            return error;
        }

        public List<String> getDetails() {
            return details;
        }
    }
}