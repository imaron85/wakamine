package io.github.imaron85.wakamine;

import io.github.imaron85.wakamine.config.ReloadListener;
import org.quiltmc.qsl.resource.loader.api.ResourceLoader;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;
import org.quiltmc.qsl.lifecycle.api.client.event.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.text.Text;
import net.minecraft.resource.ResourceType;
import io.github.imaron85.wakamine.config.config;
import io.github.imaron85.wakamine.HttpsRequestPoster;

import java.io.IOException;

public class WakaMine implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod name as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger("Example Mod");
	String state = "";
	@Override
	public void onInitialize(ModContainer mod) {
		ReloadListener reloadListener = new ReloadListener();
		ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(reloadListener);

		ClientTickEvents.END.register((client) -> {
			if (client.isInSingleplayer()) {
				state = Text.translatable("single_state").toString();
			} else if (client.getCurrentServerEntry() != null) {
				if (client.isConnectedToRealms()) {
					state = Text.translatable("realm_state").toString();
				}

				if (config.INSTANCE.api_token != "waka_xxx") {
					String address = client.getCurrentServerEntry().address;
					state = Text.translatable("multi_state").toString();
				} else {
					state = Text.translatable("main_state").toString();
					;
				}
			}
			try {
				HttpsRequestPoster.wakatimeHeartbeat(state);
			}
			catch (IOException e) {
				LOGGER.error("Couldn't send hearbeat to Wakatime");
			}
		});
	}
}
