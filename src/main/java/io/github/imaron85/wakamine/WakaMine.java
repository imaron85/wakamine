package io.github.imaron85.wakamine;

import io.github.imaron85.wakamine.config.ReloadListener;
import io.github.imaron85.wakamine.wakatime.WakaTime;
import org.quiltmc.loader.api.minecraft.ClientOnly;
import org.quiltmc.qsl.lifecycle.api.client.event.ClientLifecycleEvents;
import org.quiltmc.qsl.resource.loader.api.ResourceLoader;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;
import org.quiltmc.qsl.lifecycle.api.client.event.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.resource.ResourceType;

@ClientOnly
public class WakaMine implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod name as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger("WakaMine");

	private static String state = "";

	private static long lastCalled = 0;
	private static String lastCalledWithState = "";
	@Override
	public void onInitialize(ModContainer mod) {
		ReloadListener reloadListener = new ReloadListener();
		ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(reloadListener);

		ClientLifecycleEvents.READY.register((client) -> {
			WakaTime.initComponent(client.getGameVersion());
		});

		ClientLifecycleEvents.STOPPING.register((client) -> {
			WakaTime.disposeComponent();
		});

		ClientTickEvents.END.register((client) -> {
			state = "Idling";
			if (client.isInSingleplayer()) {
				state = "Singleplayer";
			} else if (client.getCurrentServerEntry() != null) {
				if (client.isConnectedToRealms()) {
					state = "Realms";
				}
				state = "Multiplayer";
			}

			if(lastCalledWithState == state) {
				//timeout for 30 seconds minutes if recalling with same values
				if(System.currentTimeMillis() - lastCalled <= 30000)
					return;
			}

			lastCalledWithState = state;
			lastCalled = System.currentTimeMillis();
			WakaTime.appendHeartbeat(state);
		});
	}
}
