import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;
import java.io.OutputStream;

import java.net.InetSocketAddress;
import java.net.URLDecoder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

String readFile(String path) throws IOException {
	byte[] encoded = Files.readAllBytes(Paths.get(path));
	return new String(encoded, StandardCharsets.UTF_8);
}

class Base62 {
	static String charset =
		"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	;

	static String encode(int n) {
		if (n < charset.length())
			return String.format("%c", charset.charAt(n));

		var result = new StringBuilder();

		for (; n > 0; n /= charset.length()) {
			var i = (n - 1) % charset.length();
			result.insert(0, charset.charAt(i));
		}

		return result.toString();
	}

	static int decode(String base62) {
		int result = 0;

		for (int i = 0; i < base62.length(); i++)
			result *= charset.indexOf(base62.charAt(i));

		return result;
	}
}

void main() throws Exception {
	var server = HttpServer.create(new InetSocketAddress(8080), 0);

	// database
	var count = new AtomicInteger(0);
	var short_to_url = new HashMap<String, String>();
	var url_to_number = new HashMap<String, Integer>();

	server.createContext(
		"/",
		new HttpHandler() {
			public void handle(HttpExchange t) throws IOException {
				try (OutputStream os = t.getResponseBody()) {
					String method = t.getRequestMethod();

					if (method.equals("GET")) {
						String user_short_url = t
							.getRequestURI()
							.getPath()
							.substring(1)
						;

						String response;
						int status;

						if (user_short_url.isBlank()) {
							status = 200;
							response = String.format(
								readFile("index.html"),
								""
							);
						} else {
							status = 301;
							response = "redirecting";
							t.getResponseHeaders().add(
								"Location",
								short_to_url.get(user_short_url)
							);
						}

						t.sendResponseHeaders(status, response.length());
						os.write(response.getBytes());

						return;
					}


					if (!method.equals("POST"))
						return;

					var payload =
						new String(
							t.getRequestBody().readAllBytes(),
							StandardCharsets.US_ASCII
						)
						.split("=", 2)
					;

					if (payload.length <= 0) {
						var err_msg =
							"invalid input:"+
							"empty input, please insert some url"
						;

						t.sendResponseHeaders(404, err_msg.length());
						os.write(err_msg.getBytes());

						return;
					}

					// TODO: validate and sanite the url
					var url = URLDecoder.decode(
						payload[1],
						StandardCharsets.UTF_8.name()
					);

					var host_name = t
						.getRequestHeaders()
						.get("Host")
						.getFirst()
					;

					String short_url;
					if (url_to_number.containsKey(url))
						short_url = Base62.encode(url_to_number.get(url));
					else {
						int n = count.get();
						short_url = Base62.encode(n);
						url_to_number.put(url, n);
						count.set(n + 1);
					}

					short_to_url.put(short_url, url);

					// TODO: a proper way to do a template string
					// not this thing
					var html_url = String.format(
						readFile("url.html"),
						"/"+short_url,
						host_name+"/"+short_url,
						short_to_url.get(short_url)
					);

					var response = String.format(
						readFile("index.html"),
						html_url
					);

					t.sendResponseHeaders(200, response.length());
					os.write(response.getBytes());
				}
			}
		}
	);

	server.setExecutor(null);
	server.start();
}
