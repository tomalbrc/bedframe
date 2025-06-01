package lol.sylvie.bedframe;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import lol.sylvie.bedframe.geyser.TranslationManager;
import lol.sylvie.bedframe.util.ResourceHelper;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.Person;

import java.nio.file.Path;

import static lol.sylvie.bedframe.util.BedframeConstants.*;

public class BedframeInitializer implements ModInitializer {
	public static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Bedframe - {}", METADATA.getVersion().getFriendlyString());
		LOGGER.info("Contributors: {}", String.join(", ", METADATA.getAuthors().stream().map(Person::getName).toList()));

		PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(ResourceHelper::setPolymerResourcePackBuilder);

		ServerLifecycleEvents.SERVER_STARTING.register(ignored -> {
			TranslationManager manager = new TranslationManager();
			manager.registerHooks();
		});
	}
}