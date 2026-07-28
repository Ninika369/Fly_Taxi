package com.george.servicemap.remote;

public class MapDirectionException extends RuntimeException {

    public MapDirectionException(String message) {
        super(message);
    }

    public MapDirectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
