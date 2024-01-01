package httpsrv;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;

public class Gateway {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: gateway <target>");
            System.exit(1);
        }
        var target = args[0];
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var client = HttpClient.newBuilder()
                .executor(executor)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(target))
                .build();
        var server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.setExecutor(executor);
        server.createContext("/").setHandler(exchange -> {
            try {
                var body = client.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
                exchange.sendResponseHeaders(200, body.length);
                try (var os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        });
        server.start();
        System.out.println("ready");
    }

}