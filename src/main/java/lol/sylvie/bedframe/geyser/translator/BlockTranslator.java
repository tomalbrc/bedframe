package lol.sylvie.bedframe.geyser.translator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eu.pb4.polymer.blocks.api.BlockResourceCreator;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import lol.sylvie.bedframe.geyser.Translator;
import lol.sylvie.bedframe.mixin.BlockResourceCreatorAccessor;
import lol.sylvie.bedframe.mixin.PolymerBlockResourceUtilsAccessor;
import lol.sylvie.bedframe.util.JsonHelper;
import lol.sylvie.bedframe.util.ResourceHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.CustomBlockPermutation;
import org.geysermc.geyser.api.block.custom.CustomBlockState;
import org.geysermc.geyser.api.block.custom.NonVanillaCustomBlockData;
import org.geysermc.geyser.api.block.custom.component.*;
import org.geysermc.geyser.api.event.EventBus;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.util.CreativeCategory;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import oshi.util.tuples.Triplet;
import xyz.nucleoid.packettweaker.PacketContext;

import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static lol.sylvie.bedframe.util.BedframeConstants.LOGGER;
import static lol.sylvie.bedframe.util.PathHelper.createDirectoryOrThrow;

public class BlockTranslator extends Translator {
    // Maps parent models to a map containing the translations between Java sides and Bedrock sides
    private static final Map<String, List<Pair<String, String>>> parentFaceMap = Map.of(
            "block/cube_all", List.of(
                    new Pair<>("all", "*")
            ),
            "block/cross", List.of(
                    new Pair<>("cross", "*")
            ),
            "block/cube_bottom_top", List.of(
                    new Pair<>("side", "*"),
                    new Pair<>("top", "up"),
                    new Pair<>("bottom", "down"),
                    new Pair<>("side", "north"),
                    new Pair<>("side", "south"),
                    new Pair<>("side", "east"),
                    new Pair<>("side", "west")
            ),
            "block/cube_column", List.of(
                    new Pair<>("side", "*"),
                    new Pair<>("end", "up"),
                    new Pair<>("end", "down"),
                    new Pair<>("side", "north"),
                    new Pair<>("side", "south"),
                    new Pair<>("side", "east"),
                    new Pair<>("side", "west")
            ),
            "block/cube_column_horizontal", List.of(
                    new Pair<>("side", "*"),
                    new Pair<>("end", "up"),
                    new Pair<>("end", "down"),
                    new Pair<>("side", "north"),
                    new Pair<>("side", "south"),
                    new Pair<>("side", "east"),
                    new Pair<>("side", "west")
            ),
            "block/orientable", List.of(
                    new Pair<>("side", "*"),
                    new Pair<>("front", "north"),
                    new Pair<>("top", "up"),
                    new Pair<>("bottom", "down")
            )
    );

    private final HashMap<Identifier, PolymerTexturedBlock> blocks = new HashMap<>();

    public BlockTranslator() {
        Stream<Identifier> blockIds = Registries.BLOCK.getIds().stream();

        blockIds.forEach(identifier -> {
            Block block = Registries.BLOCK.get(identifier);
            if (block instanceof PolymerTexturedBlock texturedBlock) {
                blocks.put(identifier, texturedBlock);
            }
        });
    }

    private void forEachBlock(BiConsumer<Identifier, PolymerTexturedBlock> function) {
        for (Map.Entry<Identifier, PolymerTexturedBlock> entry : blocks.entrySet()) {
            try {
                function.accept(entry.getKey(), entry.getValue());
            } catch (RuntimeException e) {
                LOGGER.error("Couldn't load block {}", entry.getKey(), e);
            }
        }
    }

    private void populateProperties(CustomBlockData.Builder builder, Collection<Property<?>> properties) {
        for (Property<?> property : properties) {
            switch (property) {
                case IntProperty intProperty ->
                        builder.intProperty(property.getName(), List.copyOf(intProperty.getValues()));
                case BooleanProperty ignored ->
                        builder.booleanProperty(property.getName());
                case EnumProperty<?> enumProperty ->
                        builder.stringProperty(enumProperty.getName(), enumProperty.getValues().stream().map(Enum::name).map(String::toLowerCase).toList());
                default ->
                        LOGGER.error("Unknown property type: {}", property.getClass().getName());
            }
        }
    }

    private BoxComponent voxelShapeToBoxComponent(VoxelShape shape, boolean offset) {
        if (shape.isEmpty()) {
            return BoxComponent.emptyBox();
        }

        Box box = shape.getBoundingBox();

        float sizeX = (float) box.getLengthX() * 16;
        float sizeY = (float) box.getLengthY() * 16;
        float sizeZ = (float) box.getLengthZ() * 16;

        Vec3d origin = box.getMinPos();
        Vector3f originNormalized = origin.toVector3f();

        return new BoxComponent(originNormalized.x() - 8, originNormalized.y(), originNormalized.z() - 8, sizeX, sizeY, sizeZ);
    }

    // Referenced https://github.com/GeyserMC/Hydraulic/blob/master/shared/src/main/java/org/geysermc/hydraulic/block/BlockPackModule.java#L54
    public void handle(GeyserDefineCustomBlocksEvent event, Path packRoot) {
        Path textureDir = createDirectoryOrThrow(packRoot.resolve("textures"));
        createDirectoryOrThrow(textureDir.resolve("blocks"));

        JsonObject terrainTextureObject = new JsonObject();
        terrainTextureObject.addProperty("resource_pack_name", "Bedframe");
        terrainTextureObject.addProperty("texture_name", "atlas.terrain");

        JsonObject textureDataObject = new JsonObject();

        forEachBlock((identifier, block) -> {
            Block realBlock = Registries.BLOCK.get(identifier);
            // Block names
            addTranslationKey("tile." + identifier.toString() + ".name", realBlock.getTranslationKey());

            NonVanillaCustomBlockData.Builder builder = NonVanillaCustomBlockData.builder()
                    .name(identifier.getPath())
                    .namespace(identifier.getNamespace())
                    .creativeGroup("itemGroup." + identifier.getNamespace() + ".blocks")
                    .creativeCategory(CreativeCategory.CONSTRUCTION)
                    .includedInCreativeInventory(true);

            // Properties
            populateProperties(builder, realBlock.getStateManager().getProperties());

            // Block states/permutations
            List<CustomBlockPermutation> permutations = new ArrayList<>();
            for (BlockState state : realBlock.getStateManager().getStates()) {
                CustomBlockComponents.Builder stateComponentBuilder = CustomBlockComponents.builder();

                // Obtain model data from polymers internal api
                BlockState polymerBlockState = block.getPolymerBlockState(state, PacketContext.get());
                BlockResourceCreator creator = PolymerBlockResourceUtilsAccessor.getCREATOR();
                PolymerBlockModel[] polymerBlockModels = ((BlockResourceCreatorAccessor)(Object)creator).getModels().get(polymerBlockState);
                PolymerBlockModel modelEntry;
                if (polymerBlockModels != null) {
                    modelEntry = polymerBlockModels[0]; // TODO: java selects one by weight, does bedrock support this?
                } else {
                    continue;
                }

                // Rotation
                TransformationComponent rotationComponent = new TransformationComponent((360 - modelEntry.x()) % 360, (360 - modelEntry.y()) % 360, 0);
                stateComponentBuilder.transformation(rotationComponent);

                // Geometry
                // TODO: More geometry types
                JsonObject blockModel = ResourceHelper.readJsonResource(modelEntry.model().getNamespace(), "models/" + modelEntry.model().getPath() + ".json");
                if (blockModel == null) {
                    LOGGER.warn("Couldn't load model for blockstate {}", state);
                    continue;
                }

                ModelData modelData = ModelData.fromJson(blockModel);
                boolean cross = modelData.parent() != null && modelData.parent().toString().equals("minecraft:block/cross");
                String geometryIdentifier = cross ?  "minecraft:geometry.cross" : "minecraft:geometry.full_block";
                String renderMethod = "alpha_test_single_sided";

                Map<String, String> refmap = new HashMap<>();
                List<Pair<String, String>> faceMap = parentFaceMap.getOrDefault(modelData.parent() == null ? "" : modelData.parent().getPath(), parentFaceMap.get("block/cube_all"));
                try {
                    Triplet<String, JsonArray, Map<String, String>> data = JavaToBedrockGeometryTranslator.convert(modelEntry.model(), packRoot);
                    geometryIdentifier = data.getA();
                    faceMap = JavaToBedrockGeometryTranslator.extractTextureFaceMap(data.getB());
                    refmap = data.getC();
                } catch (Exception e) {
                    LOGGER.error("Could not convert block model: {}", modelEntry.model());
                }

                GeometryComponent geometryComponent = GeometryComponent.builder().identifier(geometryIdentifier).build();
                stateComponentBuilder.geometry(geometryComponent);

                // Textures
                for (Pair<String, String> face : faceMap) {
                    String javaFaceName = face.getLeft();
                    String bedrockFaceName = face.getRight();
                    if (!refmap.containsKey(javaFaceName)) continue;

                    String textureName = JavaToBedrockGeometryTranslator.resolvePath(refmap, javaFaceName);
                    Identifier textureIdentifier = Identifier.of(textureName);
                    String texturePath = "textures/" + textureIdentifier.getPath();
                    String bedrockPath = ResourceHelper.javaToBedrockTexture(texturePath, "block");

                    JsonObject thisTexture = new JsonObject();
                    thisTexture.addProperty("textures", bedrockPath);
                    textureDataObject.add(textureName, thisTexture);

                    stateComponentBuilder.materialInstance(bedrockFaceName, MaterialInstance.builder()
                            .renderMethod(renderMethod)
                            .texture(textureName)
                            .faceDimming(state.isOpaque())
                            .ambientOcclusion(state.isOpaque())
                            .build());

                    ResourceHelper.copyResource(textureIdentifier.getNamespace(), texturePath + ".png", packRoot.resolve(bedrockPath + ".png"));
                }

                stateComponentBuilder.collisionBox(voxelShapeToBoxComponent(state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN), true));
                stateComponentBuilder.selectionBox(voxelShapeToBoxComponent(state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN), false));
                stateComponentBuilder.lightEmission(state.getLuminance());

                CustomBlockComponents stateComponents = stateComponentBuilder.build();
                if (state.getProperties().isEmpty()) {
                    builder.components(stateComponents);
                    continue;
                }

                // Conditions
                // Essentially telling Bedrock what components to activate when
                List<String> conditions = new ArrayList<>();
                for (Property<?> property : state.getProperties()) {
                    String propertyValue = state.get(property).toString();
                    if (property instanceof EnumProperty<?>) {
                        propertyValue = "'" + propertyValue.toLowerCase() + "'";
                    }

                    conditions.add("q.block_property('%name%') == %value%"
                            .replace("%name%", property.getName())
                            .replace("%value%", propertyValue));
                }

                String stateCondition = String.join(" && ", conditions);
                permutations.add(new CustomBlockPermutation(stateComponents, stateCondition));
            }
            builder.permutations(permutations);

            NonVanillaCustomBlockData data = builder.build();
            event.register(data);

            // Registering the block states
            for (BlockState state : realBlock.getStateManager().getStates()) {
                CustomBlockState.Builder stateBuilder = data.blockStateBuilder();

                for (Property<?> property : state.getProperties()) {
                    switch (property) {
                        case IntProperty intProperty ->
                                stateBuilder.intProperty(property.getName(), state.get(intProperty));
                        case BooleanProperty booleanProperty ->
                                stateBuilder.booleanProperty(property.getName(), state.get(booleanProperty));
                        case EnumProperty<?> enumProperty ->
                                stateBuilder.stringProperty(enumProperty.getName(), state.get(enumProperty).toString().toLowerCase());
                        default ->
                                throw new IllegalArgumentException("Unknown property type: " + property.getClass().getName());
                    }
                }

                CustomBlockState customBlockState = stateBuilder.build();
                event.registerOverride(BlockArgumentParser.stringifyBlockState(block.getPolymerBlockState(state, PacketContext.get())), customBlockState);
            }
        });

        terrainTextureObject.add("texture_data", textureDataObject);
        writeJsonToFile(terrainTextureObject, textureDir.resolve("terrain_texture.json").toFile());
        markResourcesProvided();
    }

    @Override
    public void register(EventBus<EventRegistrar> eventBus, Path packRoot) {
        eventBus.subscribe(this, GeyserDefineCustomBlocksEvent.class, event -> handle(event, packRoot));
    }

    record ModelData(@Nullable Identifier parent, Map<String, String> textures) {
        public static ModelData fromJson(JsonObject object) {
            return JsonHelper.GSON.fromJson(object, ModelData.class);
        }
    }
}
