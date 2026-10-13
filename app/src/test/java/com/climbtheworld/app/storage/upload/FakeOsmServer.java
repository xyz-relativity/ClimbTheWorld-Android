package com.climbtheworld.app.storage.upload;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** An OSM API stand-in on localhost: it gives the answers set up per request, and keeps the calls. */
final class FakeOsmServer implements AutoCloseable {
	static final String API_PATH = "/api/0.6";

	static final class Call {
		final String method;
		final String uri;
		final String authorization;
		final String body;

		Call(String method, String uri, String authorization, String body) {
			this.method = method;
			this.uri = uri;
			this.authorization = authorization;
			this.body = body;
		}
	}

	final List<Call> calls = Collections.synchronizedList(new ArrayList<>());
	private final Map<String, Deque<Object[]>> answers = new HashMap<>();
	private final HttpServer server;

	FakeOsmServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			String method = exchange.getRequestMethod();
			String uri = exchange.getRequestURI().toString();
			calls.add(new Call(method, uri, exchange.getRequestHeaders().getFirst("Authorization"),
					read(exchange.getRequestBody())));
			Object[] answer;
			synchronized (answers) {
				Deque<Object[]> queue = answers.get(method + " " + uri);
				answer = queue == null || queue.isEmpty() ? new Object[]{404, "No answer set up"} : queue.poll();
			}
			byte[] body = ((String) answer[1]).getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders((Integer) answer[0], body.length == 0 ? -1 : body.length);
			if (body.length > 0) {
				exchange.getResponseBody().write(body);
			}
			exchange.close();
		});
		server.start();
	}

	String apiUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort() + API_PATH;
	}

	/** @param uri the path under the API, with its query, like /nodes.json?nodes=1 */
	void answer(String method, String uri, int code, String body) {
		synchronized (answers) {
			answers.computeIfAbsent(method + " " + API_PATH + uri, key -> new ArrayDeque<>())
					.add(new Object[]{code, body});
		}
	}

	Call call(String method, String uri) {
		synchronized (calls) {
			for (Call call : calls) {
				if (call.method.equals(method) && call.uri.equals(API_PATH + uri)) {
					return call;
				}
			}
		}
		return null;
	}

	@Override
	public void close() {
		server.stop(0);
	}

	private static String read(InputStream input) throws IOException {
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int read;
		while ((read = input.read(buffer)) > 0) {
			result.write(buffer, 0, read);
		}
		return result.toString("UTF-8");
	}
}
