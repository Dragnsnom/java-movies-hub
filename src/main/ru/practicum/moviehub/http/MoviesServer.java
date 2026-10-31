package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {
    private final HttpServer server;
    private final int port;

    public MoviesServer(MoviesStore store, int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/movies", new MoviesHandler(store));
        server.createContext("/movies/", new MovieHandler(store));
    }

    public void start() {
        server.start();
        System.out.println("MovieHub server started on port " + port);
    }

    public void stop() {
        server.stop(0);
        System.out.println("MovieHub server stopped");
    }
}