package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
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
        HttpResponse<String> response = sendGetRequest("/movies");

        assertAll("Проверка пустого списка фильмов",
                () -> assertStatus(response, 200),
                () -> assertContentType(response, BaseHttpHandler.CONTENT_TYPE_JSON_CHARSET),
                () -> {
                    List<Movie> movies = parseResponseToList(response);
                    assertTrue(movies.isEmpty());
                }
        );
    }

    @Test
    void getMovies_whenHasMovies_returnsMoviesArray() throws Exception {
        addMovie(new Movie("Inception", 2010));
        addMovie(new Movie("The Matrix", 1999));

        HttpResponse<String> response = sendGetRequest("/movies");

        assertAll("Проверка списка с фильмами",
                () -> assertStatus(response, 200),
                () -> {
                    List<Movie> movies = parseResponseToList(response);
                    assertEquals(2, movies.size());
                }
        );
    }

    @Test
    void postMovie_whenValid_returnsCreatedMovie() throws Exception {
        Movie newMovie = new Movie("The Dark Knight", 2008);

        HttpResponse<String> response = sendPostRequest("/movies", newMovie);
        Movie createdMovie = parseResponseToMovie(response);

        assertAll("Проверка создания валидного фильма",
                () -> assertStatus(response, 201),
                () -> assertContentType(response, BaseHttpHandler.CONTENT_TYPE_JSON_CHARSET),
                () -> assertNotNull(createdMovie.getId()),
                () -> assertEquals("The Dark Knight", createdMovie.getTitle()),
                () -> assertEquals(2008, createdMovie.getYear())
        );

        HttpResponse<String> getResponse = sendGetRequest("/movies/" + createdMovie.getId());
        assertStatus(getResponse, 200);
    }

    @Test
    void postMovie_whenEmptyTitle_returnsValidationError() throws Exception {
        Movie invalidMovie = new Movie("", 2024);

        HttpResponse<String> response = sendPostRequest("/movies", invalidMovie);

        assertAll("Проверка ошибки при пустом названии",
                () -> assertStatus(response, 500),
                () -> assertValidationError(response, BaseHttpHandler.VALIDATION_TITLE_EMPTY)
        );
    }

    @Test
    void postMovie_whenTitleTooLong_returnsValidationError() throws Exception {
        String longTitle = "A".repeat(101);
        Movie invalidMovie = new Movie(longTitle, 2024);

        HttpResponse<String> response = sendPostRequest("/movies", invalidMovie);

        assertAll("Проверка ошибки при слишком длинном названии",
                () -> assertStatus(response, 500),
                () -> assertValidationError(response, BaseHttpHandler.VALIDATION_TITLE_TOO_LONG)
        );
    }

    @Test
    void postMovie_whenYearTooLow_returnsValidationError() throws Exception {
        Movie invalidMovie = new Movie("Too Old Movie", 1800);

        HttpResponse<String> response = sendPostRequest("/movies", invalidMovie);
        String expectedError = BaseHttpHandler.VALIDATION_YEAR_INVALID + (Year.now().getValue() + 1);

        assertAll("Проверка ошибки при годе меньше 1888",
                () -> assertStatus(response, 500),
                () -> assertValidationErrorContains(response, expectedError)
        );
    }

    @Test
    void postMovie_whenYearTooHigh_returnsValidationError() throws Exception {
        int futureYear = Year.now().getValue() + 2;
        Movie invalidMovie = new Movie("Too Future Movie", futureYear);

        HttpResponse<String> response = sendPostRequest("/movies", invalidMovie);
        String expectedError = BaseHttpHandler.VALIDATION_YEAR_INVALID + (Year.now().getValue() + 1);

        assertAll("Проверка ошибки при годе больше текущего + 1",
                () -> assertStatus(response, 500),
                () -> assertValidationErrorContains(response, expectedError)
        );
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

        assertStatus(response, 415);
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

        assertStatus(response, 400);
    }

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        Movie movie = addMovie(new Movie("Pulp Fiction", 1994));

        HttpResponse<String> response = sendGetRequest("/movies/" + movie.getId());

        Movie foundMovie = parseResponseToMovie(response);

        assertAll("Проверка получения фильма по существующему ID",
                () -> assertStatus(response, 200),
                () -> assertEquals(movie.getId(), foundMovie.getId()),
                () -> assertEquals("Pulp Fiction", foundMovie.getTitle()),
                () -> assertEquals(1994, foundMovie.getYear())
        );
    }

    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {
        HttpResponse<String> response = sendGetRequest("/movies/99999");

        assertAll("Проверка ошибки при поиске несуществующего фильма",
                () -> assertStatus(response, 404),
                () -> assertErrorContains(response, BaseHttpHandler.ERROR_MOVIE_NOT_FOUND)
        );
    }

    @Test
    void getMovieById_whenIdNotNumber_returns400() throws Exception {
        HttpResponse<String> response = sendGetRequest("/movies/abc");

        assertAll("Проверка ошибки при некорректном ID",
                () -> assertStatus(response, 400),
                () -> assertErrorContains(response, BaseHttpHandler.ERROR_INVALID_ID)
        );
    }

    @Test
    void deleteMovie_whenExists_returns204() throws Exception {
        Movie movie = addMovie(new Movie("Fight Club", 1999));

        HttpResponse<String> response = sendDeleteRequest("/movies/" + movie.getId());

        assertAll("Проверка удаления существующего фильма",
                () -> assertStatus(response, 204),
                () -> assertTrue(response.body().isEmpty())
        );

        HttpResponse<String> getResponse = sendGetRequest("/movies/" + movie.getId());
        assertStatus(getResponse, 404);
    }

    @Test
    void deleteMovie_whenNotFound_returns404() throws Exception {
        HttpResponse<String> response = sendDeleteRequest("/movies/99999");

        assertAll("Проверка ошибки при удалении несуществующего фильма",
                () -> assertStatus(response, 404),
                () -> assertErrorContains(response, BaseHttpHandler.ERROR_MOVIE_NOT_FOUND)
        );
    }

    @Test
    void deleteMovie_whenIdNotNumber_returns400() throws Exception {
        HttpResponse<String> response = sendDeleteRequest("/movies/xyz");

        assertAll("Проверка ошибки при удалении с некорректным ID",
                () -> assertStatus(response, 400),
                () -> assertErrorContains(response, BaseHttpHandler.ERROR_INVALID_ID)
        );
    }

    @Test
    void getMoviesByYear_whenMoviesExist_returnsFilteredMovies() throws Exception {
        addMovie(new Movie("Movie 1999 A", 1999));
        addMovie(new Movie("Movie 1999 B", 1999));
        addMovie(new Movie("Movie 2000", 2000));

        HttpResponse<String> response = sendGetRequest("/movies?year=1999");

        assertAll("Проверка фильтрации фильмов по году",
                () -> assertStatus(response, 200),
                () -> {
                    List<Movie> movies = parseResponseToList(response);
                    assertEquals(2, movies.size());
                    assertTrue(movies.stream().allMatch(m -> m.getYear() == 1999));
                }
        );
    }

    @Test
    void getMoviesByYear_whenNoMovies_returnsEmptyArray() throws Exception {
        HttpResponse<String> response = sendGetRequest("/movies?year=1800");

        assertAll("Проверка фильтрации при отсутствии фильмов",
                () -> assertStatus(response, 200),
                () -> {
                    List<Movie> movies = parseResponseToList(response);
                    assertTrue(movies.isEmpty());
                }
        );
    }

    @Test
    void getMoviesByYear_whenYearNotNumber_returns400() throws Exception {
        HttpResponse<String> response = sendGetRequest("/movies?year=abcd");

        assertAll("Проверка ошибки при некорректном параметре year",
                () -> assertStatus(response, 400),
                () -> assertContentType(response, BaseHttpHandler.CONTENT_TYPE_JSON_CHARSET),
                () -> assertErrorEquals(response, BaseHttpHandler.ERROR_INVALID_YEAR_PARAM)
        );
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/movies"))
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertStatus(response, 405);
    }

    @Test
    void allSuccessResponsesHaveCorrectContentType() throws Exception {
        HttpResponse<String> getResponse = sendGetRequest("/movies");

        Movie movie = new Movie("Test", 2024);
        HttpResponse<String> postResponse = sendPostRequest("/movies", movie);
        Movie createdMovie = parseResponseToMovie(postResponse);

        HttpResponse<String> getByIdResponse = sendGetRequest("/movies/" + createdMovie.getId());

        assertAll("Проверка Content-Type для всех успешных ответов",
                () -> assertContentType(getResponse, BaseHttpHandler.CONTENT_TYPE_JSON_CHARSET),
                () -> assertContentType(postResponse, BaseHttpHandler.CONTENT_TYPE_JSON_CHARSET),
                () -> assertContentType(getByIdResponse, BaseHttpHandler.CONTENT_TYPE_JSON_CHARSET)
        );
    }

    private HttpResponse<String> sendGetRequest(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendPostRequest(String path, Movie movie) throws IOException, InterruptedException {
        String json = gson.toJson(movie);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header(BaseHttpHandler.CONTENT_TYPE, BaseHttpHandler.CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendDeleteRequest(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .DELETE()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Movie addMovie(Movie movie) throws IOException, InterruptedException {
        HttpResponse<String> response = sendPostRequest("/movies", movie);
        return parseResponseToMovie(response);
    }

    private List<Movie> parseResponseToList(HttpResponse<String> response) {
        return gson.fromJson(response.body(), new ListOfMoviesTypeToken().getType());
    }

    private Movie parseResponseToMovie(HttpResponse<String> response) {
        return gson.fromJson(response.body(), Movie.class);
    }

    private void assertStatus(HttpResponse<String> response, int expectedStatus) {
        assertEquals(expectedStatus, response.statusCode());
    }

    private void assertContentType(HttpResponse<String> response, String expectedContentType) {
        assertEquals(expectedContentType,
                response.headers().firstValue(BaseHttpHandler.CONTENT_TYPE).orElse(""));
    }

    private void assertValidationError(HttpResponse<String> response, String expectedErrorDetail) {
        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals(BaseHttpHandler.ERROR_VALIDATION, error.getError());
        assertTrue(error.getDetails().stream()
                .anyMatch(d -> d.contains(expectedErrorDetail)));
    }

    private void assertValidationErrorContains(HttpResponse<String> response, String expectedPart) {
        ErrorResponse error = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals(BaseHttpHandler.ERROR_VALIDATION, error.getError());
        assertTrue(error.getDetails().stream()
                .anyMatch(d -> d.contains(expectedPart)));
    }

    private void assertErrorContains(HttpResponse<String> response, String expectedError) {
        assertTrue(response.body().contains(expectedError));
    }

    private void assertErrorEquals(HttpResponse<String> response, String expectedError) {
        ErrorResponse errorResponse = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals(expectedError, errorResponse.getError());
    }
}