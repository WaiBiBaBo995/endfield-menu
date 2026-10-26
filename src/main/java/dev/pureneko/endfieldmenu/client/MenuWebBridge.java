package dev.pureneko.endfieldmenu.client;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/** World-scoped loopback resources and action endpoint. No remote page is loaded. */
final class MenuWebBridge implements AutoCloseable {
  private final HttpServer server;
  private final java.util.concurrent.ExecutorService executor;
  private final String root = "/endfield/" + UUID.randomUUID() + "/";
  private final Consumer<String> action;
  private volatile String state = "{}";
  private volatile byte[] avatar;
  private volatile JsonObject report = new JsonObject();
  private final Map<String, byte[]> resources = new java.util.concurrent.ConcurrentHashMap<>();

  MenuWebBridge(Consumer<String> action) throws IOException {
    this.action = action;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    executor =
        Executors.newFixedThreadPool(
            2,
            r -> {
              Thread t = new Thread(r, "Endfield page resources");
              t.setDaemon(true);
              return t;
            });
    server.setExecutor(executor);
    server.createContext(root, this::request);
    server.start();
  }

  String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + root + "index.html";
  }

  void state(JsonObject value) {
    state = value.toString();
  }

  void avatar(byte[] value) {
    avatar = value;
  }

  JsonObject report() {
    return report;
  }

  private void request(HttpExchange exchange) throws IOException {
    try {
      String path = exchange.getRequestURI().getPath().substring(root.length());
      if (path.contains("..") || path.contains("\\")) {
        reply(exchange, 404, "text/plain", new byte[0]);
        return;
      }
      if (exchange.getRequestMethod().equals("POST")) {
        if (!"1".equals(exchange.getRequestHeaders().getFirst("X-Endfield-Menu"))) {
          reply(exchange, 403, "text/plain", new byte[0]);
          return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(65537);
        if (body.length > 65536) {
          reply(exchange, 413, "text/plain", new byte[0]);
          return;
        }
        String text = new String(body, StandardCharsets.UTF_8);
        if (path.equals("action")) Minecraft.getInstance().execute(() -> action.accept(text));
        else if (path.equals("report")) {
          try {
            report = JsonParser.parseString(text).getAsJsonObject();
          } catch (RuntimeException ignored) {
          }
        }
        reply(exchange, 200, "text/plain", new byte[0]);
        return;
      }
      if (!exchange.getRequestMethod().equals("GET")) {
        reply(exchange, 405, "text/plain", new byte[0]);
        return;
      }
      if (path.equals("state.json")) {
        reply(exchange, 200, "application/json", state.getBytes(StandardCharsets.UTF_8));
        return;
      }
      if (path.equals("avatar.png")) {
        byte[] image = avatar;
        reply(
            exchange, image == null ? 404 : 200, "image/png", image == null ? new byte[0] : image);
        return;
      }
      byte[] data = resources.get(path);
      if (data == null) {
        try (InputStream input =
            MenuWebBridge.class.getResourceAsStream(
                "/assets/apricityui/apricity/endfield/" + path)) {
          if (input == null) {
            reply(exchange, 404, "text/plain", new byte[0]);
            return;
          }
          data = input.readAllBytes();
        }
        if (path.equals("index.html")) {
          String html = new String(data, StandardCharsets.UTF_8);
          // Adapt the served response; the original attachment remains byte-identical.
          html = html.replaceAll("(?m)^<link[^>]*https://fonts\\.[^>]*>\\s*", "");
          html = html.replace("</body>", "<script src=\"minecraft.js\"></script></body>");
          data = html.getBytes(StandardCharsets.UTF_8);
        }
        resources.put(path, data);
      }
      reply(
          exchange,
          200,
          path.endsWith(".js")
              ? "application/javascript"
              : path.endsWith(".png") ? "image/png" : "text/html; charset=utf-8",
          data);
    } catch (RuntimeException ex) {
      reply(exchange, 500, "text/plain", new byte[0]);
    } finally {
      exchange.close();
    }
  }

  private static void reply(HttpExchange e, int status, String type, byte[] data)
      throws IOException {
    e.getResponseHeaders().set("Content-Type", type);
    e.getResponseHeaders().set("Cache-Control", "no-store");
    e.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    e.sendResponseHeaders(status, data.length == 0 ? -1 : data.length);
    if (data.length > 0) e.getResponseBody().write(data);
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdown();
  }
}
