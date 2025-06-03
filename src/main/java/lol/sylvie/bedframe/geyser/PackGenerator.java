package lol.sylvie.bedframe.geyser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lol.sylvie.bedframe.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static lol.sylvie.bedframe.util.BedframeConstants.METADATA;

/**
 * Compiles the output of the {@link Translator} classes into a Bedrock resource pack
 */
public class PackGenerator {
    private static JsonArray getVersionArray() {
        // TODO: A regex would be more inclusive
        Version version = BedframeConstants.METADATA.getVersion();
        List<Integer> friendly = Arrays.stream(version.getFriendlyString()
                        .split("\\."))
                .map(x -> x.replaceAll("[^0-9]", ""))
                .map(Integer::valueOf)
                .toList();

        JsonArray array = new JsonArray(friendly.size());
        friendly.forEach(array::add);
        return array;
    }

    private static void writeJsonToFile(JsonElement object, File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            JsonHelper.GSON.toJson(object, writer);
        }
    }


    private static void writeManifestFile(Path directory) throws IOException {
        // TODO: Maybe generate this based on the mod list? Look into how aggressively Bedrock caches server resource packs
        // (is it hash based? uuid based? version based?)
        String versionIdentifier = METADATA.getId() + "-" + METADATA.getVersion().getFriendlyString();
        boolean shouldRandomize = FabricLoader.getInstance().isDevelopmentEnvironment();

        // Manifest
        File manifestFile = directory.resolve("manifest.json").toFile();
        JsonObject manifestObject = new JsonObject();
        manifestObject.addProperty("format_version", 2);
        JsonArray version = getVersionArray();
        // Header
        JsonObject header = new JsonObject();
        header.addProperty("description", METADATA.getDescription());
        header.addProperty("name", METADATA.getId());
        header.addProperty("uuid", (shouldRandomize ? UUID.randomUUID() : UUID.fromString(versionIdentifier)).toString());
        header.add("version", version);

        JsonArray engineVersion = new JsonArray();
        engineVersion.add(1); engineVersion.add(21); engineVersion.add(70);
        header.add("min_engine_version", engineVersion);

        manifestObject.add("header", header);

        // Modules
        JsonArray modules = new JsonArray();
        JsonObject module = new JsonObject();
        module.addProperty("description", METADATA.getName() + " Resources");
        module.addProperty("type", "resources");
        module.addProperty("uuid", (shouldRandomize ? UUID.randomUUID() : UUID.fromString(versionIdentifier + "-resources")).toString());
        module.add("version", version);

        modules.add(module);
        manifestObject.add("modules", modules);

        writeJsonToFile(manifestObject, manifestFile);
    }

    public void generatePack(Path packPath, File outputFile, List<Translator> translators) throws IOException {
        writeManifestFile(packPath);

        Path textsDir = packPath.resolve("texts");
        PathHelper.createDirectoryOrThrow(textsDir);

        // TODO: I'm not sure if translations are even necessary
        /*JsonArray languages = new JsonArray();

        ArrayList<Pair<String, String>> allKeys = new ArrayList<>();
        translators.forEach(t -> allKeys.addAll(t.getTranslations()));

        TranslationHelper.LANGUAGES.forEach((code) -> {
            TranslationAccess access = ServerTranslations.INSTANCE.getLanguage(code).serverTranslations();
            try (FileWriter writer = new FileWriter(textsDir.resolve(code + ".lang").toFile())) {
                for (Pair<String, String> keyPair : allKeys) {
                    writer.write(keyPair.getLeft() + "=" + access.get(keyPair.getRight()) + "\n");
                }
            } catch (IOException e) {
                bedframe.getLogger().error("Couldn't write language file");
            }

            languages.add(code);
        });
        writeJsonToFile(languages, textsDir.resolve("languages.json").toFile());*/

        Optional<String> icon = METADATA.getIconPath(512);
        Files.copy(ResourceHelper.getResource(icon.orElseThrow()), packPath.resolve("pack_icon.png"));

        ZipHelper.zipFolder(packPath, outputFile);
    }
}
