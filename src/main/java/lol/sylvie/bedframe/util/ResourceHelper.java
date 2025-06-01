package lol.sylvie.bedframe.util;

import com.google.gson.JsonObject;
import eu.pb4.polymer.resourcepack.api.AssetPaths;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResourceHelper {
    private static @Nullable ResourcePackBuilder RPBUILDER;

    public static void setPolymerResourcePackBuilder(ResourcePackBuilder resourcePackBuilder) {
        RPBUILDER = resourcePackBuilder;
    }

    public static InputStream getResource(String path) {
        if (RPBUILDER != null) {
            byte[] data = RPBUILDER.getData(path);
            if (data != null) {
                return new ByteArrayInputStream(data);
            }
        }

        return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    }

    public static String getResourcePath(String namespace, String path) {
        return "assets/" + namespace + "/" + path;
    }

    public static InputStream getResource(String namespace, String path) {
        return getResource(getResourcePath(namespace, path));
    }

    public static void copyResource(String namespace, String path, Path destination) {
        try {
            if (Files.notExists(destination))
                Files.copy(getResource(namespace, path), destination);
        } catch (IOException | NullPointerException e) {
            throw new RuntimeException("Couldn't copy resource " + Identifier.of(namespace, path), e);
        }
    }

    public static JsonObject readJsonResource(String namespace, String path) {
        try (InputStream stream = getResource(namespace, path)) {
            return BedframeConstants.GSON.fromJson(new InputStreamReader(stream), JsonObject.class);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't load resource " + Identifier.of(namespace, path), e);
        }
    }

    public static String javaToBedrockTexture(String javaPath) {
        return javaPath.replaceFirst("block", "blocks").replaceFirst("item", "items");
    }
}
