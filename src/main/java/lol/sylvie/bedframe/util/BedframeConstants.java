package lol.sylvie.bedframe.util;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BedframeConstants {
    // To save file space it's technically better to disable pretty printing
    public static final String MOD_ID = "bedframe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final ModMetadata METADATA = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();

    public static final Identifier GENERATED_IDENTIFIER = Identifier.ofVanilla("item/generated");
    public static final Identifier HANDHELD_IDENTIFIER = Identifier.ofVanilla("item/handheld");
    public static final Identifier POLYMER_EMPTY_BLOCK_MODEL = Identifier.of("polymer", "block/empty");
}
