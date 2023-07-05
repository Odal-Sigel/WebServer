package com.webserver;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Hello world! - Web Server
 */
public class Server {
    private boolean running = false; // Flag for server status.

    private int timeout = 800; // Time limit for data transfers.
    private Charset encoder = Charset.forName("UTF-8"); // Encoder for strings

    private ServerSocket serverSocket; // Server Socket
    private String contentPath; // Root path -> Where our Web Server files are located

    // MIME - TODO: add more MIME
    private Map<String, String> extensions = new HashMap<String, String>() {
        {
            put("html", "text/html");
            put("css", "text/css");
        }
    };

    /**
     * Try to start Web Server if it is not running yet
     * @param ipAddress The local InetAddress the server will bind to
     * @param port The port number
     * @param maxNOfCon Requested maximum length of the queue of incoming connections
     * @param contentPath Server root path
     * @return True if everything is ok or False if an error ocurred
     */
    public boolean start(InetAddress ipAddress, int port, int maxNOfCon, String contentPath) {
        // If is running exit
        if (running) {
            return false;
        }

        // Crete a new Socket
        try {
            serverSocket = new ServerSocket(port, maxNOfCon, ipAddress);
            serverSocket.setSoTimeout(timeout);
            running = true;
            this.contentPath = contentPath;
        } catch (IOException ex) {
            return false;
        }

        // Connection requests listener
        Thread requestListener = new Thread(() -> {
            while (running) {
                Socket clientSocket;

                // Create a new thread for every incoming request
                try {
                    clientSocket = serverSocket.accept();

                    Thread requestHandler = new Thread(() -> {
                        try {
                            clientSocket.setSoTimeout(timeout * 1000);
                            handleTheRequest(clientSocket);
                        } catch (IOException ex) {
                            try {
                                clientSocket.close();
                            } catch (Exception exClose) {
                                exClose.printStackTrace();
                            }
                        }
                    });
                    requestHandler.start();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        requestListener.start();

        return true;
    }

    /**
     * Try to stop Web Server
     */
    public void stop() {
        if (running) {
            running = false;

            try {
                serverSocket.close();
            } catch (IOException ex) {
                serverSocket = null;
            }
        }
    }

    /**
     * Handle every request
     * @param clientSocket The socket opened for the request
     */
    private void handleTheRequest(Socket clientSocket) {
        byte[] buf = new byte[10240]; // 10 kb

        try {
            int receivedBCount = clientSocket.getInputStream().read(buf); // Receive the request
            String receivedS = new String(buf, 0, receivedBCount, encoder);

            // Parse the request
            String[] requestLines = receivedS.split(System.lineSeparator());
            String[] firstLineParts = requestLines[0].split(" ");
            String method = firstLineParts[0];
            String url = firstLineParts[1];

            String requestedFile;
            if (method.equals("GET") || method.equals("POST")) {
                requestedFile = url.split("\\?")[0];
            } else {
                notImplemented(clientSocket);
                return;
            }

            requestedFile = requestedFile.replace("/", "\\").replace("\\..", "");
            int start = requestedFile.lastIndexOf("\\") + 1;

            if (start > 1) {
                int length = requestedFile.length() - start;
                String extension = requestedFile.substring(start, start + length);

                if (extensions.containsKey(extension)) {
                    File file = new File(contentPath + requestedFile);

                    if (file.exists()) {
                        // OK
                        sendOkResponse(clientSocket, Files.readAllBytes(file.toPath()), extensions.get(extension));
                    } else {
                        // NOT FOUND
                        notFound(clientSocket);
                    }
                }
            } else {
                if (requestedFile.endsWith("\\")) {
                    requestedFile += "\\";

                    File indexHtmFile = new File(contentPath + requestedFile + "index.htm");
                    File indexHtmlFile = new File(contentPath + requestedFile + "index.html");
                    if (indexHtmFile.exists()) {
                        sendOkResponse(clientSocket, Files.readAllBytes(indexHtmFile.toPath()), "text/html");
                    } else if (indexHtmlFile.exists()) {
                        sendOkResponse(clientSocket, Files.readAllBytes(indexHtmlFile.toPath()), "text/html");
                    } else {
                        notFound(clientSocket);
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Not implemented http methods
     * @param clientSocket The socket opened for the request
     */
    private void notImplemented(Socket clientSocket) {
        String responseContent = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"></head><body><div>501 - Method Not Implemented</div></body></html>";
        sendResponse(clientSocket, responseContent, "501 Not Implemented", "text/html");
    }

    /**
     * File not found
     * @param clientSocket The socket opened for the request
     */
    private void notFound(Socket clientSocket) {
        String responseContent = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"></head><body><div>404 - Not Found</div></body></html>";
        sendResponse(clientSocket, responseContent, "404 Not Found", "text/html");
    }

    /**
     * 200 OK
     * @param clientSocket The socket opened for the request
     * @param bContent Byte content
     * @param contentType File type
     */
    private void sendOkResponse(Socket clientSocket, byte[] bContent, String contentType) {
        sendResponse(clientSocket, bContent, "200 OK", contentType);
    }

    /**
     * For Strings
     * @param clientSocket The socket opened for the request
     * @param contentS Content of request
     * @param responseCode Response code
     * @param contentType File type
     */
    private void sendResponse(Socket clientSocket, String contentS, String responseCode,
            String contentType) {
        byte[] bContent = contentS.getBytes(encoder);
        sendResponse(clientSocket, bContent, responseCode, contentType);
    }

    /**
     * For byte arrays
     * @param clientSocket The socket opened for the request
     * @param bContent Content of request
     * @param responseCode Response code
     * @param contentType File type
     */
    private void sendResponse(Socket clientSocket, byte[] bContent, String responseCode,
            String contentType) {
        try {
            String responseHeader = "HTTP/1.1 " + responseCode + "\r\n"
                    + "Server: Atasoy Simple Web Server\r\n"
                    + "Content-Length: " + bContent.length + "\r\n"
                    + "Connection: close\r\n"
                    + "Content-Type: " + contentType + "\r\n\r\n";
            byte[] bHeader = responseHeader.getBytes(encoder);
            clientSocket.getOutputStream().write(bHeader);
            clientSocket.getOutputStream().write(bContent);
            clientSocket.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

}
