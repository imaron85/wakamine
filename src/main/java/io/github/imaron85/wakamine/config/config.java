package io.github.imaron85.wakamine.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import org.quiltmc.loader.api.ExtendedFiles;
import org.quiltmc.loader.api.FasterFiles;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Properties;

@Config(name = "wakamine")
public class config implements ConfigData {
	@ConfigEntry.Gui.Excluded
	public static config INSTANCE;
	@ConfigEntry.Gui.Excluded
	private static ConfigHolder<config> CONFIG_HOLDER;

	@ConfigEntry.Gui.PrefixText()
	public String api_token = "waka_xxx";

	private static ConfigHolder<config> getConfigHolder() {
		if (CONFIG_HOLDER == null) {
			ConfigHolder<config> configHolder = AutoConfig.getConfigHolder(config.class);
			CONFIG_HOLDER = configHolder;
			return configHolder;
		} else {
			return CONFIG_HOLDER;
		}
	}

	public static void saveConfig() {
		getConfigHolder().save();
	}

	public void validateConfig() {

	}

	public static void init() { }

	static {
		AutoConfig.register(config.class, GsonConfigSerializer::new);
		INSTANCE = getConfigHolder().getConfig();
	}
}
