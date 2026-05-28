package com.trade.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class WebConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("legittrade");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private int port = 39482;
	private boolean enabled = true;
	private String bindAddress = "127.0.0.1";

	public int getPort() {
		return port;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public String getBindAddress() {
		return bindAddress;
	}

	private static Path getConfigPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("legittrade-web.json");
	}

	public static WebConfig load() {
		Path configPath = getConfigPath();
		WebConfig config = new WebConfig();

		try {
			if (Files.exists(configPath)) {
				String json = Files.readString(configPath);
				JsonObject obj = GSON.fromJson(json, JsonObject.class);
				if (obj.has("port")) {
					config.port = Math.max(1, Math.min(65535, obj.get("port").getAsInt()));
				}
				if (obj.has("enabled")) {
					config.enabled = obj.get("enabled").getAsBoolean();
				}
				if (obj.has("bindAddress")) {
					config.bindAddress = obj.get("bindAddress").getAsString();
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed to load web config", e);
		}

		return config;
	}
}
