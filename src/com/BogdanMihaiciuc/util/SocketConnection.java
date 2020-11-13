package com.BogdanMihaiciuc.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.net.Socket;

public class SocketConnection {

    public interface SocketCallback {
        void onConnectionEstablished(SocketConnection connection);

        void onMessageReceived(SocketConnection connection, byte[] message);

        void onConnectionDropped(SocketConnection connection);
    }

    private String ip;
    private int port;

    private Socket socket;

    private BufferedWriter writer;
    private BufferedReader reader;

    private SocketConnection(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public static SocketConnection prepareConnection(String ip, int port) {
        SocketConnection connection = new SocketConnection(ip, port);

        return connection;
    }

    public void start() {

    }

}
