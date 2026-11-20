package dev.uwu.dimension;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;

/** Reads the bundled Sakura Reach conversations during command registration. */
final class AmbientDialogueText {
    private static final String RESOURCE = "/data/uwudimension/dialogue/sakura_ambient.json";
    private final Map<String, Scene> scenes;

    record Line(String speaker, String text) {
        Component component() {
            return Component.literal(speaker + ": " + text);
        }
    }

    record Choice(String id, String label, Line reply, boolean needsCrow) {}
    record Scene(Line opening, List<Choice> choices) {}

    private AmbientDialogueText(Map<String, Scene> scenes) {
        this.scenes = Map.copyOf(scenes);
    }

    Scene scene(String id) {
        return scenes.get(id);
    }

    static AmbientDialogueText load() {
        var stream = AmbientDialogueText.class.getResourceAsStream(RESOURCE);
        if (stream == null) throw new IllegalStateException("Missing conversation resource " + RESOURCE);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot read conversation resource " + RESOURCE, error);
        }
    }

    static AmbientDialogueText parse(Reader reader) {
        var root = JsonParser.parseReader(reader).getAsJsonObject();
        Map<String, Line> lines = new HashMap<>();
        for (var element : root.getAsJsonArray("dialogues")) {
            var line = element.getAsJsonObject();
            String id = field(line, "id");
            if (lines.putIfAbsent(id, new Line(field(line, "speaker"), field(line, "text"))) != null) {
                throw new IllegalArgumentException("Duplicate line " + id);
            }
        }
        Map<String, Scene> scenes = new HashMap<>();
        for (var element : root.getAsJsonArray("scenes")) {
            var scene = element.getAsJsonObject();
            String id = field(scene, "id");
            Line opening = reference(lines, scene, "opening");
            List<Choice> choices = new ArrayList<>();
            Set<String> topics = new HashSet<>();
            for (var option : scene.getAsJsonArray("choices")) {
                var choice = option.getAsJsonObject();
                String topic = field(choice, "id");
                if (!topic.matches("[a-z0-9_]+") || !topics.add(topic)) {
                    throw new IllegalArgumentException("Invalid or duplicate topic " + id + "/" + topic);
                }
                boolean needsCrow = choice.has("requires");
                if (needsCrow && !"nearby_dusk_crow".equals(field(choice, "requires"))) {
                    throw new IllegalArgumentException("Unknown condition on " + id + "/" + topic);
                }
                choices.add(new Choice(topic, field(choice, "label"), reference(lines, choice, "reply"), needsCrow));
            }
            if (scenes.putIfAbsent(id, new Scene(opening, List.copyOf(choices))) != null) {
                throw new IllegalArgumentException("Duplicate scene " + id);
            }
        }
        if (!scenes.keySet().equals(Set.of("dusk_village", "moonveil_lower", "moonveil_upper"))) {
            throw new IllegalArgumentException("Unexpected conversation scenes " + scenes.keySet());
        }
        return new AmbientDialogueText(scenes);
    }

    private static String field(JsonObject object, String name) {
        String value = object.getAsJsonPrimitive(name).getAsString();
        if (value.isBlank()) throw new IllegalArgumentException("Empty conversation field " + name);
        return value;
    }

    private static Line reference(Map<String, Line> lines, JsonObject object, String field) {
        String id = field(object, field);
        Line line = lines.get(id);
        if (line == null) throw new IllegalArgumentException("Unknown conversation line " + id);
        return line;
    }
}
