package lol.sylvie.bedframe.geyser.translator;

import com.google.gson.JsonObject;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.AssetPaths;
import eu.pb4.polymer.resourcepack.extras.api.format.item.ItemAsset;
import eu.pb4.polymer.resourcepack.extras.api.format.item.model.BasicItemModel;
import lol.sylvie.bedframe.geyser.Translator;
import lol.sylvie.bedframe.util.BedframeConstants;
import lol.sylvie.bedframe.util.ResourceHelper;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.*;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.geysermc.geyser.api.event.EventBus;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.item.custom.CustomItemOptions;
import org.geysermc.geyser.api.item.custom.NonVanillaCustomItemData;
import org.geysermc.geyser.api.util.CreativeCategory;
import xyz.nucleoid.packettweaker.PacketContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static lol.sylvie.bedframe.util.PathHelper.createDirectoryOrThrow;

public class ItemTranslator extends Translator {
    private final HashMap<Identifier, PolymerItem> items = new HashMap<>();
    private static final ArrayList<Item> registeredItems = new ArrayList<>();

    public ItemTranslator() {
        Stream<Identifier> itemIds = Registries.ITEM.getIds().stream();

        itemIds.forEach(identifier -> {
            Item item = Registries.ITEM.get(identifier);
            if (item instanceof PolymerItem polymerItem) {
                items.put(identifier, polymerItem);
            }
        });
    }

    private void forEachItem(BiConsumer<Identifier, PolymerItem> function) {
        for (Map.Entry<Identifier, PolymerItem> entry : items.entrySet()) {
            function.accept(entry.getKey(), entry.getValue());
        }
    }

    public static boolean isTexturedItem(Item item) {
        return registeredItems.contains(item);
    }

    private void handle(GeyserDefineCustomItemsEvent event, Path packRoot) {
        Path textureDir = createDirectoryOrThrow(packRoot.resolve("textures"));
        createDirectoryOrThrow(textureDir.resolve("items"));

        JsonObject itemTextureObject = new JsonObject();
        itemTextureObject.addProperty("resource_pack_name", BedframeConstants.MOD_ID);
        itemTextureObject.addProperty("texture_name", "atlas.items");

        JsonObject textureDataObject = new JsonObject();

        forEachItem((identifier, item) -> {
            Item realItem = Registries.ITEM.get(identifier);
            ItemStack realDefaultItemStack = realItem.getDefaultStack();

            // I know there is item.getPolymerItemModel but some developers (cough me cough) just override the itemstack model
            ItemStack itemStack = item.getPolymerItemStack(realItem.getDefaultStack(), TooltipType.BASIC, PacketContext.get());
            Identifier model = itemStack.get(DataComponentTypes.ITEM_MODEL);

            // Check if the model is provided by a mod using the minecraft namespace
            if (model == null || (model.getNamespace().equals("minecraft") && !ResourceHelper.isModProvided(AssetPaths.itemModel(model)))) return;

            CustomItemOptions.Builder itemOptions = CustomItemOptions.builder();

            String translated = Text.translatable(realItem.getTranslationKey()).getString();
            NonVanillaCustomItemData.Builder itemBuilder = NonVanillaCustomItemData.builder()
                    .name(identifier.toString())
                    .identifier(identifier.toString())
                    .displayName(translated)
                    .creativeGroup("itemGroup." + identifier.getNamespace() + ".items")
                    .creativeCategory(CreativeCategory.CONSTRUCTION.id())
                    .allowOffhand(true);

            ComponentMap components = realDefaultItemStack.getComponents();

            // Food
            FoodComponent foodComponent = components.get(DataComponentTypes.FOOD);
            if (foodComponent != null) {
                itemBuilder.edible(components.contains(DataComponentTypes.CONSUMABLE));
                itemBuilder.canAlwaysEat(foodComponent.canAlwaysEat());
            }

            // Glint
            itemBuilder.foil(components.getOrDefault(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false));

            // Damage
            itemOptions.unbreakable(realDefaultItemStack.isDamageable());

            // Bows
            itemBuilder.chargeable(realItem instanceof CrossbowItem || realItem instanceof BowItem);

            if (realDefaultItemStack.contains(DataComponentTypes.EQUIPPABLE)) {
                // FIXME: ew
                Map<String, String> equipmentSlotMap = Map.of(
                        "mainhand", "main_hand",
                        "offhand", "off_hand",
                        "feet", "boots",
                        "legs", "leggings",
                        "chest", "chestplate",
                        "head", "helmet",
                        "body", "body",
                        "saddle", "saddle" // does not exist in bedrock
                );
                // TODO: translate the equippable asset
                itemBuilder.armorType(equipmentSlotMap.get(realDefaultItemStack.get(DataComponentTypes.EQUIPPABLE).slot().asString()));
            }

            itemBuilder.customItemOptions(itemOptions.build());
            itemBuilder.javaId(Registries.ITEM.getRawIdOrThrow(realItem));

            if (realItem instanceof BlockItem blockItem) {
                String blockId = Registries.BLOCK.getEntry(blockItem.getBlock()).getIdAsString();
                itemBuilder.icon(""); // see CustomItemRegistryPopulatorMixin
                itemBuilder.block(blockId);
                //itemBuilder.translationString("tile." + blockId + ".name");
            } else {
                // Item names
                String bedrockKey = "item." + identifier + ".name";
                addTranslationKey(bedrockKey, realItem.getTranslationKey());
                //itemBuilder.translationString(bedrockKey);
            }

            ItemAsset itemDescription = ResourceHelper.readJsonResource(AssetPaths.itemAsset(model), ItemAsset.class);
            if (itemDescription == null || !(itemDescription.model() instanceof BasicItemModel basicItemModel))
                return;

            Identifier modelId = basicItemModel.model();
            JsonObject modelObject = ResourceHelper.readJsonResource(modelId.getNamespace(), "models/" + modelId.getPath() + ".json");
            if (modelObject == null)
                return;

            Identifier modelType = modelObject.has("parent") ? Identifier.of(modelObject.get("parent").getAsString()) : null;
            if (modelType != null && (modelType.equals(BedframeConstants.GENERATED_IDENTIFIER) || modelType.equals(BedframeConstants.HANDHELD_IDENTIFIER))) {
                boolean handheld = modelType.equals(BedframeConstants.HANDHELD_IDENTIFIER);
                itemBuilder.displayHandheld(handheld);

                Identifier textureId = Identifier.of(modelObject.get("textures").getAsJsonObject().get("layer0").getAsString());

                String texturePath = "textures/" + textureId.getPath();
                String bedrockPath = ResourceHelper.javaToBedrockTexture(texturePath, "item");
                String textureName = identifier.toString();

                JsonObject textureObject = new JsonObject();
                textureObject.addProperty("textures", bedrockPath);

                textureDataObject.add(textureName, textureObject);
                ResourceHelper.copyResource(textureId.getNamespace(), texturePath + ".png", packRoot.resolve(bedrockPath + ".png"));

                itemBuilder.icon(textureName);
            }

            registeredItems.add(realItem);
            event.register(itemBuilder.build());
        });

        itemTextureObject.add("texture_data", textureDataObject);
        writeJsonToFile(itemTextureObject, textureDir.resolve("item_texture.json").toFile());
        markResourcesProvided();
    }

    @Override
    public void register(EventBus<EventRegistrar> eventBus, Path packRoot) {
        eventBus.subscribe(this, GeyserDefineCustomItemsEvent.class, event -> handle(event, packRoot));
    }
}
