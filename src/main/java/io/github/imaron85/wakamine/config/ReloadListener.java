package io.github.imaron85.wakamine.config;

import io.github.imaron85.wakamine.config.config;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.quiltmc.qsl.resource.loader.api.reloader.SimpleSynchronousResourceReloader;
import io.github.imaron85.wakamine.WakaMine;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class ReloadListener implements SimpleSynchronousResourceReloader {

	boolean firstRun = true;

	@Override
	public Identifier getQuiltId() {
		return new Identifier("wakamine", "reload_listener");
	}

	@Override
	public void reload(ResourceManager manager) {
		if (firstRun) {
			config.init();
			firstRun = false;

			Properties prop = new Properties();

			try {
				FileInputStream fis = new FileInputStream(System.getProperty("user.home") + "/.wakatime.cfg");
				prop.load(fis);
				config.INSTANCE.api_token = prop.getProperty("api_key").trim();
				config.saveConfig();
				WakaMine.LOGGER.info("Found Wakatime config and loaded values");
			}
			catch (FileNotFoundException e) {
				WakaMine.LOGGER.warn("Couldn't find Wakatime config, please enter settings manually.");
			}
			catch (IOException e){
				WakaMine.LOGGER.error("Found Wakatime config, but couldn't load it. Please enter settings manually.");
			}
		}
	}

	@Override
	public String getName() {
		return SimpleSynchronousResourceReloader.super.getName();
	}
}
