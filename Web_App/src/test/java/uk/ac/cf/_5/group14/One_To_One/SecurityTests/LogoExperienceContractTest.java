package uk.ac.cf._5.group14.One_To_One.SecurityTests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uk.ac.cf._5.group14.One_To_One.Config.LocalisationAdvice;
import uk.ac.cf._5.group14.One_To_One.Config.SupportedLanguage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class LogoExperienceContractTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void shippedModelContainsIndependentSolidConstructionLayers() throws IOException {
        byte[] bytes = Files.readAllBytes(RESOURCES.resolve("static/models/one-to-one/sculpture.glb"));
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(buffer.getInt()).isEqualTo(0x46546c67);
        assertThat(buffer.getInt()).isEqualTo(2);
        assertThat(buffer.getInt()).isEqualTo(bytes.length);
        int jsonLength = buffer.getInt();
        assertThat(buffer.getInt()).isEqualTo(0x4e4f534a);
        JsonNode gltf = new ObjectMapper().readTree(new String(bytes, 20, jsonLength, StandardCharsets.UTF_8));
        Set<String> names = new HashSet<>();
        for (JsonNode node : gltf.get("nodes")) names.add(node.path("name").asText());
        assertThat(names).contains("Wing_Left", "Wing_Right", "Wordmark", "Face_Left", "Face_Right",
                "Circuit_Left", "Circuit_Right", "Shell_Left", "Shell_Right", "Core_Left", "Core_Right");
        assertThat(gltf.has("images")).as("Logo is solid geometry, without image planes").isFalse();
        for (JsonNode mesh : gltf.get("meshes")) {
            JsonNode primitive = mesh.get("primitives").get(0);
            JsonNode position = gltf.get("accessors").get(primitive.get("attributes").get("POSITION").asInt());
            for (int axis = 0; axis < 3; axis++) {
                assertThat(position.get("max").get(axis).asDouble() - position.get("min").get(axis).asDouble())
                        .as("Mesh %s has volume on axis %s", mesh.path("name").asText(), axis).isGreaterThan(0.001);
            }
        }
        assertThat(Files.size(RESOURCES.resolve("static/models/one-to-one/sculpture-poster.png"))).isGreaterThan(1000);
        assertThat(Files.size(Path.of("design/brand/one-to-one-sculpture.blend"))).isGreaterThan(1000);
    }

    @Test
    void everyExperienceLabelHasMatchingEnglishAndWelshCopy() throws IOException {
        Properties english = bundle("messages-logo.properties");
        Properties welsh = bundle("messages-logo_cy.properties");
        assertThat(welsh.stringPropertyNames()).containsExactlyInAnyOrderElementsOf(english.stringPropertyNames());
        String fragment = Files.readString(RESOURCES.resolve("templates/public-views/home/fragments/logo-experience.html"));
        var matcher = Pattern.compile("#\\{(logo\\.[^}]+)}").matcher(fragment);
        while (matcher.find()) {
            assertThat(english.getProperty(matcher.group(1))).as(matcher.group(1)).isNotBlank();
            assertThat(welsh.getProperty(matcher.group(1))).as(matcher.group(1)).isNotBlank();
        }
        assertThat(fragment).doesNotContain("<dialog").contains("aria-controls=\"logo-inspector\"", "aria-labelledby=\"logo-experience-title\"", "tabindex=\"0\"",
                "aria-live=\"polite\"", "data-logo-poster", "data-logo-close", "data-logo-reset");
        assertThat(Files.readString(RESOURCES.resolve("application.properties"))).contains("messages-logo");
    }

    private Properties bundle(String name) throws IOException {
        Properties result = new Properties();
        try (var reader = Files.newBufferedReader(RESOURCES.resolve(name), StandardCharsets.UTF_8)) {
            result.load(reader);
        }
        return result;
    }

    @Test
    void configuredMessageSourceResolvesExperienceCopyForEverySupportedLanguage() {
        var messages = new LocalisationAdvice().messageSource();
        for (var language : SupportedLanguage.all()) {
            assertThat(messages.getMessage("logo.title", null, language.locale())).isNotBlank();
            assertThat(messages.getMessage("logo.canvasLabel", null, language.locale())).isNotBlank();
        }
        assertThat(messages.getMessage("logo.title", null, SupportedLanguage.WELSH.locale()))
                .isNotEqualTo(messages.getMessage("logo.title", null, SupportedLanguage.ENGLISH.locale()));
    }
}
