package lol.sylvie.bedframe.geyser;

import lol.sylvie.bedframe.BedframeInitializer;
import lol.sylvie.bedframe.geyser.translator.BlockTranslator;
import lol.sylvie.bedframe.geyser.translator.ItemTranslator;
import lol.sylvie.bedframe.util.BedframeConstants;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventBus;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TranslationManager implements EventRegistrar {
    private static final PackGenerator packGenerator = new PackGenerator();
    private boolean generatedResources = false;

    public TranslationManager() {}

    public void registerHooks() {
        List<Translator> translators = List.of(
                new BlockTranslator(),
                new ItemTranslator()
        );

        // Generate the fold
        Path packSourceDir;
        try {
            packSourceDir = Files.createTempDirectory("bedframe");
        } catch (IOException e) {
            BedframeConstants.LOGGER.error("Couldn't create resource pack temporary directory", e);
            return;
        }

        Path resourcePack = BedframeInitializer.CONFIG_DIR.resolve("bedframe.zip");

        EventBus<EventRegistrar> eventBus = GeyserApi.api().eventBus();
        for (Translator translator : translators) {
            translator.register(eventBus, packSourceDir);
        }

        eventBus.subscribe(this, GeyserDefineResourcePacksEvent.class, event -> {
            try {
                // For some reason, GeyserDefineResourcePacksEvent is called once *before* blocks
                // FIXME: It's probably better to generate resources before the event is ever called
                if (!translators.stream().allMatch(Translator::hasProvidedResources)) return;

                // This event is called multiple times, but regenerating the resource pack each time is redundant
                if (!generatedResources) {
                    Files.deleteIfExists(resourcePack);
                    packGenerator.generatePack(packSourceDir, resourcePack.toFile(), translators);
                    generatedResources = true;
                }

                event.register(ResourcePack.create(PackCodec.path(resourcePack)));
            } catch (IOException e) {
                BedframeConstants.LOGGER.error("Couldn't generate resource pack", e);
            }
        });
    }
}
