package io.github.imaron85.wakamine;

import io.github.imaron85.wakamine.config.config;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class wakatime {
	private static final HttpClient client = HttpClient.newHttpClient();
	private static final ExecutorService executor = Executors.newSingleThreadExecutor((r) -> {
		Thread t = Executors.defaultThreadFactory().newThread(r);
		t.setDaemon(true);
		return t;
	});

	private static String hostname;

	static {
		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			WakaMine.LOGGER.warn("Couldn't get hostname for wakatime");
		}
	}

	public static void sendHeartbeat(String state) {
		if (config.INSTANCE.api_token == "waka_xxx"){
			WakaMine.LOGGER.warn("No wakatime API-Key - Skipping update");
			return;
		}

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("https://api.wakatime.com/api/v1/users/current/heartbeats"))
			.POST(HttpRequest.BodyPublishers.ofString(
				"{\n" +
					"    \"entity\": \"Minecraft - " + state + "\",\n" +
					"    \"type\": \"app\",\n" +
					"    \"category\": \"building\",\n" +
					"    \"time\": \"1706453970\",\n" +
					"    \"language\": \"Minecraft\",\n" +
					"    \"plugin\": \"minecraft/1.20.1 wakamine/1.0.0\"\n" +
					"    \"hostname\": \"" +  hostname + "\"\n" +
					"}"
			))
			.header("Authorization", "Basic " + config.INSTANCE.api_token)
			.build();
		try {
			CompletableFuture<HttpResponse<String>> promise = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
			HttpResponse<String> response = promise.get();
			WakaMine.LOGGER.debug(response.toString());
			WakaMine.LOGGER.info("Sent Hearbeat with state: " + state);
		}
		catch (ExecutionException | InterruptedException e) {
			WakaMine.LOGGER.error("Failed to send wakatime heartbeat");
			e.printStackTrace();
		}
	}

	public static void sendHeartbeatAsync(String state) {
		executor.submit(() -> sendHeartbeat(state));
	}
}
