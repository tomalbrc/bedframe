package lol.sylvie.bedframe.geyser.translator;

import com.google.gson.*;
import eu.pb4.polymer.resourcepack.api.AssetPaths;
import lol.sylvie.bedframe.util.JsonHelper;
import lol.sylvie.bedframe.util.ResourceHelper;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import oshi.util.tuples.Triplet;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class JavaToBedrockGeometryTranslator {
    public static Triplet<String, JsonArray, Map<String, String>> convert(Identifier modelId, Path packRoot) throws Exception {
        JsonObject javaModel = loadModel(modelId);
        if (javaModel == null)
            return null;

        Map<String, String> textureRefMap = new HashMap<>();
        JsonArray elements = resolveElements(javaModel, JavaToBedrockGeometryTranslator::loadModel, textureRefMap);

        if (elements == null) {
            return null;
        }

        JsonElement texturesElement = javaModel.get("textures");
        Map<String, BufferedImage> textureMap = new HashMap<>();
        if (texturesElement != null) {
            JsonObject textures = texturesElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                String path = resolvePathJson(textures, entry.getValue().getAsString());
                Identifier textureId = Identifier.of(path);
                InputStream textureData = ResourceHelper.getResource(AssetPaths.texture(textureId) + ".png");
                if (textureData != null) {
                    BufferedImage texture = ImageIO.read(textureData);
                    textureMap.put(entry.getKey(), texture);
                }
            }
        }

        String bedrockStringId = modelId.toString().replace(":", ".").replace("/", ".");
        String bedrockStringIdWithPrefix = "geometry." + bedrockStringId;
        JsonObject bedrockGeo = buildBedrockGeometry(elements, textureMap, bedrockStringIdWithPrefix);
        Path outputPath = packRoot.resolve("models/blocks/" + bedrockStringId + ".geo.json");
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            JsonHelper.GSON.toJson(bedrockGeo, writer);
        }

        return new Triplet<>(bedrockStringIdWithPrefix, elements, textureRefMap);
    }

    public static String resolvePath(Map<String, String> map, String key) {
        String value = map.get(key);
        while (value != null && value.startsWith("#")) {
            String newKey = value.substring(1);
            value = map.get(newKey);
        }
        return value;
    }

    public static String resolvePathJson(JsonElement map, String key) {
        if (!key.startsWith("#"))
            return key;

        String value = map.getAsJsonObject().get(key).getAsString();
        while (value != null && value.startsWith("#")) {
            String newKey = value.substring(1);
            JsonElement obj = map.getAsJsonObject().get(newKey);
            value = obj != null ? obj.getAsString() : null;
        }
        return value;
    }

    public static JsonObject buildBedrockGeometry(JsonArray elements, Map<String, BufferedImage> textureMap, String identifier) {
        JsonArray bones = new JsonArray();
        int unnamedCounter = 0;

        for (JsonElement el : elements) {
            JsonObject element = el.getAsJsonObject();
            JsonArray from = element.getAsJsonArray("from");
            JsonArray to = element.getAsJsonArray("to");

            JsonObject cube = new JsonObject();

            // Translate geometry into Bedrock‐block coordinates.
            //  For Java models, (0,0,0) is the corner of a 16x16x16 cube.
            //  For bedrock, (0,0,0) is the center of the bottom face.
            //    bedX = javaX − 8;  bedY = javaY;  bedZ = javaZ − 8
            float javaFromX = from.get(0).getAsFloat();
            float javaFromY = from.get(1).getAsFloat();
            float javaFromZ = from.get(2).getAsFloat();

            JsonArray origin = new JsonArray();
            origin.add(javaFromX - 8.0f);
            origin.add(javaFromY);
            origin.add(javaFromZ - 8.0f);
            cube.add("origin", origin);

            float javaToX = to.get(0).getAsFloat();
            float javaToY = to.get(1).getAsFloat();
            float javaToZ = to.get(2).getAsFloat();
            JsonArray size = new JsonArray();
            size.add(javaToX - javaFromX);
            size.add(javaToY - javaFromY);
            size.add(javaToZ - javaFromZ);
            cube.add("size", size);

            // UV
            JsonObject uvObject = new JsonObject();
            if (element.has("faces")) {
                JsonObject faces = element.getAsJsonObject("faces");
                for (Map.Entry<String, JsonElement> faceEntry : faces.entrySet()) {
                    String faceName = faceEntry.getKey();
                    JsonObject face = faceEntry.getValue().getAsJsonObject();
                    if (face.has("uv")) {
                        JsonArray uv = face.getAsJsonArray("uv");
                        JsonArray uvCoords = new JsonArray();
                        uvCoords.add(uv.get(0).getAsFloat());
                        uvCoords.add(uv.get(1).getAsFloat());
                        JsonObject uvSubObj = new JsonObject();
                        uvSubObj.add("uv", uvCoords);
                        uvObject.add(faceName, uvSubObj);
                    } else {
                        JsonArray uvCoords = new JsonArray();
                        uvCoords.add(0.f);
                        uvCoords.add(0.f);
                        JsonObject uvSubObj = new JsonObject();
                        uvSubObj.add("uv", uvCoords);
                        uvObject.add(faceName, uvSubObj);
                    }
                }
            }

            if (!uvObject.isEmpty()) {
                JsonArray fallbackUV = new JsonArray();
                fallbackUV.add(0f);
                fallbackUV.add(0f);
                JsonObject uvSubObj = new JsonObject();
                uvSubObj.add("uv", fallbackUV);
                uvObject.add("*", uvSubObj);
                cube.add("uv", uvObject);
            } else {
                // fallback
                JsonArray fallbackUV = new JsonArray();
                fallbackUV.add(0f);
                fallbackUV.add(0f);
                JsonObject uvSubObj = new JsonObject();
                uvSubObj.add("uv", fallbackUV);
                uvObject.add("*", uvSubObj);
                cube.add("uv", uvObject);
            }

            cube.addProperty("inflate", 0f);

            //  bone wrapper for this cube.
            JsonObject bone = new JsonObject();
            bone.addProperty("name", "element_" + unnamedCounter++);

            // Pivot & Rotation:
            //    In bedrock geo:
            //      - Pivot is in pixel units relative to (0,0,0) = center of bottom face.
            //      - Java's rotation origin is in pixel units relative to corner.
            //      => pivotBedrockX = rotOriginX – 8, pivotBedrockY = rotOriginY, pivotBedrockZ = rotOriginZ – 8.
            JsonArray pivot = new JsonArray();
            pivot.add(0f);
            pivot.add(0f);
            pivot.add(0f);

            if (element.has("rotation")) {
                JsonObject rot = element.getAsJsonObject("rotation");
                JsonArray rotOrigin = rot.getAsJsonArray("origin");

                float rOrigX = rotOrigin.get(0).getAsFloat();
                float rOrigY = rotOrigin.get(1).getAsFloat();
                float rOrigZ = rotOrigin.get(2).getAsFloat();

                JsonArray bedrockPivot = new JsonArray();
                bedrockPivot.add(rOrigX - 8.0f);
                bedrockPivot.add(rOrigY);
                bedrockPivot.add(rOrigZ - 8.0f);
                bone.add("pivot", bedrockPivot);

                String axis = rot.get("axis").getAsString();
                float angle = (360 - rot.get("angle").getAsFloat()) % 360;
                JsonArray rotation = new JsonArray();
                rotation.add(axis.equals("x") ? angle : 0f);
                rotation.add(axis.equals("y") ? angle : 0f);
                rotation.add(axis.equals("z") ? angle : 0f);
                bone.add("rotation", rotation);
            } else {
                bone.add("pivot", pivot);
            }

            // add the cube (maybe add all cubes here)
            JsonArray cubes = new JsonArray();
            cubes.add(cube);
            bone.add("cubes", cubes);

            bones.add(bone);
        }

        // create json
        JsonObject description = new JsonObject();
        description.addProperty("identifier", identifier);

        BufferedImage texture = getFirstNonParticle(textureMap);
        description.add("texture_width", new JsonPrimitive(texture == null ? 16 : texture.getWidth()));
        description.add("texture_height", new JsonPrimitive(texture == null ? 16 : texture.getHeight()));

        JsonObject geometry = new JsonObject();
        geometry.add("description", description);
        geometry.add("bones", bones);

        JsonArray geometryArray = new JsonArray();
        geometryArray.add(geometry);

        JsonObject result = new JsonObject();
        result.addProperty("format_version", "1.16.0");
        result.add("minecraft:geometry", geometryArray);

        return result;
    }

    private static BufferedImage getFirstNonParticle(Map<String, BufferedImage> obj) {
        for (Map.Entry<String, BufferedImage> entry : obj.entrySet()) {
            if (!entry.getKey().equals("particle") && !entry.getKey().startsWith("#")) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static JsonArray resolveElements(JsonObject model, Function<Identifier, JsonObject> modelLoader, Map<String, String> textureRefMap) {
        var map = model.getAsJsonObject("textures").asMap();
        for (Map.Entry<String, JsonElement> entry : map.entrySet()) {
            textureRefMap.put(entry.getKey(), entry.getValue().getAsString());
        }

        while (!model.has("elements")) {
            if (!model.has("parent")) return null;

            String parentStr = model.get("parent").getAsString();
            Identifier parentId = Identifier.of(parentStr);
            model = loadModel(parentId);

            if (model == null) {
                return null;
            }

            JsonObject modelTextureMap = model.getAsJsonObject("textures");
            if (modelTextureMap != null) {
                Map<String, JsonElement> map2 = modelTextureMap.asMap();
                for (Map.Entry<String, JsonElement> entry : map2.entrySet()) {
                    textureRefMap.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }

        return model.getAsJsonArray("elements");
    }

    public static JsonObject loadModel(Identifier id) {
        String modelPath = AssetPaths.model(id) + ".json";

        try (InputStream inputStream = ResourceHelper.getResource(modelPath)) {
            if (inputStream == null)
                return null;

            return JsonParser.parseReader(new InputStreamReader(inputStream)).getAsJsonObject();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load model: " + id, e);
        }
    }

    public static List<Pair<String, String>> extractTextureFaceMap(JsonArray elements) {
        List<Pair<String, String>> result = new ArrayList<>();
        for (JsonElement el : elements) {
            JsonObject element = el.getAsJsonObject();
            if (!element.has("faces")) continue;

            JsonObject faces = element.getAsJsonObject("faces");

            for (Map.Entry<String, JsonElement> faceEntry : faces.entrySet()) {
                String direction = faceEntry.getKey(); // e.g., "north", "up", etc.
                JsonObject faceObj = faceEntry.getValue().getAsJsonObject();
                if (faceObj.has("texture")) {
                    String textureRef = faceObj.get("texture").getAsString();
                    if (textureRef.startsWith("#"))
                        textureRef = textureRef.substring(1);
                    result.add(new Pair<>(textureRef, direction));
                }
            }
        }

        return result;
    }
}
