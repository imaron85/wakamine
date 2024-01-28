package io.github.imaron85.wakamine.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.quiltmc.loader.api.minecraft.ClientOnly;
import me.shedaniel.autoconfig.AutoConfig;

import java.lang.annotation.Annotation;

@ClientOnly
public class modmenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return (parent) -> AutoConfig.getConfigScreen(config.class, parent).get();
	}
}
