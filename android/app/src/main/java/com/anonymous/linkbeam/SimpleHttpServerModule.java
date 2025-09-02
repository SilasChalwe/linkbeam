package com.anonymous.linkbeam;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashMap;
import java.util.Map;

public class SimpleHttpServer extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "SimpleHttpServer";
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private boolean isRunning = false;
    private String documentRoot;
    private int port;

    public SimpleHttpServer(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @ReactMethod
    public void start(int port, String documentRoot, Promise promise) {
        try {
            if (isRunning) {
                WritableMap result = Arguments.createMap();
                result.putBoolean("success", false);
                result.putString("error", "Server is already running");
                promise.resolve(result);
                return;
            }

            this.port = port;
            this.documentRoot = documentRoot;

            // Create server socket bound to all interfaces (0.0.0.0)
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("0.0.0.0", port));
            
            executorService = Executors.newFixedThreadPool(10);
            isRunning = true;

            // Start accepting connections in background thread
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    acceptConnections();
                }
            });

            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            result.putString("url", "http://0.0.0.0:" + port);
            promise.resolve(result);

        } catch (Exception e) {
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", false);
            result.putString("error", e.getMessage());
            promise.resolve(result);
        }
    }

    @ReactMethod
    public void stop(Promise promise) {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                isRunning = false;
                serverSocket.close();
            }
            
            if (executorService != null) {
                executorService.shutdown();
            }

            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            promise.resolve(result);

        } catch (Exception e) {
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", false);
            result.putString("error", e.getMessage());
            promise.resolve(result);
        }
    }

    @ReactMethod
    public void isRunning(Promise promise) {
        promise.resolve(isRunning);
    }

    private void acceptConnections() {
        while (isRunning && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(new ClientHandler(clientSocket));
            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    private class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                OutputStream out = clientSocket.getOutputStream();

                // Read HTTP request line
                String requestLine = in.readLine();
                if (requestLine == null || requestLine.isEmpty()) {
                    clientSocket.close();
                    return;
                }

                // Parse request
                String[] parts = requestLine.split(" ");
                if (parts.length < 2) {
                    sendResponse(out, 400, "Bad Request", "text/plain");
                    return;
                }

                String method = parts[0];
                String path = parts[1];

                // Skip headers
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    // Skip headers
                }

                if ("GET".equals(method)) {
                    handleGetRequest(path, out);
                } else {
                    sendResponse(out, 405, "Method Not Allowed", "text/plain");
                }

            } catch (Exception e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        private void handleGetRequest(String path, OutputStream out) throws IOException {
            // Remove query parameters
            int queryIndex = path.indexOf('?');
            if (queryIndex != -1) {
                path = path.substring(0, queryIndex);
            }

            // Default to index.html for root path
            if ("/".equals(path)) {
                path = "/index.html";
            }

            // Security check - prevent directory traversal
            if (path.contains("..")) {
                sendResponse(out, 403, "Forbidden", "text/plain");
                return;
            }

            File file = new File(documentRoot + path);
            
            if (!file.exists() || file.isDirectory()) {
                sendResponse(out, 404, "Not Found", "text/plain");
                return;
            }

            try {
                String contentType = getContentType(file.getName());
                sendFileResponse(out, file, contentType);
            } catch (Exception e) {
                sendResponse(out, 500, "Internal Server Error", "text/plain");
            }
        }

        private void sendResponse(OutputStream out, int statusCode, String body, String contentType) throws IOException {
            String statusText = getStatusText(statusCode);
            String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                            "Content-Type: " + contentType + "\r\n" +
                            "Content-Length: " + body.getBytes().length + "\r\n" +
                            "Connection: close\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "\r\n" +
                            body;
            out.write(response.getBytes());
            out.flush();
        }

        private void sendFileResponse(OutputStream out, File file, String contentType) throws IOException {
            FileInputStream fis = new FileInputStream(file);
            
            String headers = "HTTP/1.1 200 OK\r\n" +
                           "Content-Type: " + contentType + "\r\n" +
                           "Content-Length: " + file.length() + "\r\n" +
                           "Connection: close\r\n" +
                           "Access-Control-Allow-Origin: *\r\n" +
                           "\r\n";
            
            out.write(headers.getBytes());
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            
            fis.close();
            out.flush();
        }

        private String getContentType(String fileName) {
            String extension = "";
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot > 0) {
                extension = fileName.substring(lastDot + 1).toLowerCase();
            }

            Map<String, String> mimeTypes = new HashMap<>();
            mimeTypes.put("html", "text/html");
            mimeTypes.put("htm", "text/html");
            mimeTypes.put("css", "text/css");
            mimeTypes.put("js", "application/javascript");
            mimeTypes.put("json", "application/json");
            mimeTypes.put("png", "image/png");
            mimeTypes.put("jpg", "image/jpeg");
            mimeTypes.put("jpeg", "image/jpeg");
            mimeTypes.put("gif", "image/gif");
            mimeTypes.put("pdf", "application/pdf");
            mimeTypes.put("txt", "text/plain");

            return mimeTypes.getOrDefault(extension, "application/octet-stream");
        }

        private String getStatusText(int statusCode) {
            switch (statusCode) {
                case 200: return "OK";
                case 400: return "Bad Request";
                case 403: return "Forbidden";
                case 404: return "Not Found";
                case 405: return "Method Not Allowed";
                case 500: return "Internal Server Error";
                default: return "Unknown";
            }
        }
    }
}