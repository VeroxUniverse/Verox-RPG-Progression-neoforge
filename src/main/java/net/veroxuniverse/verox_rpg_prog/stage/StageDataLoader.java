package net.veroxuniverse.verox_rpg_prog.stage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.veroxuniverse.verox_rpg_prog.RPGProgression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@EventBusSubscriber(modid = RPGProgression.MOD_ID)
public class StageDataLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(StageDataLoader.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ADDITIONS_DIRECTORY = "stage_additions";
    private static final String RAIDS_DIRECTORY = "stage_raids";

    public StageDataLoader() {
        super(GSON, "stages");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> elements, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, StageDefinition> loadedStages = new HashMap<>();
        int skipped = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : elements.entrySet()) {
            if (!ModRequirements.areMet(entry.getValue())) {
                skipped++;
                continue;
            }
            StageDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                    .resultOrPartial(error -> LOGGER.error("Failed to parse stage {}: {}", entry.getKey(), error))
                    .ifPresent(stage -> loadedStages.put(entry.getKey(), stage));
        }

        int additions = this.applyAdditions(loadedStages, resourceManager);
        int raids = this.applyRaids(loadedStages, resourceManager);

        StageManager.reloadStages(loadedStages);
        LOGGER.info("Loaded {} world progression stages ({} skipped by mod requirements, {} additions and {} raids applied).",
                loadedStages.size(), skipped, additions, raids);
    }

    private int applyAdditions(Map<ResourceLocation, StageDefinition> stages, ResourceManager resourceManager) {
        Map<ResourceLocation, Resource> resources = new TreeMap<>(
                resourceManager.listResources(ADDITIONS_DIRECTORY, path -> path.getPath().endsWith(".json"))
        );
        int applied = 0;

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            JsonElement json;
            try (Reader reader = entry.getValue().openAsReader()) {
                json = JsonParser.parseReader(reader);
            } catch (Exception exception) {
                LOGGER.error("Failed to read stage addition {}", entry.getKey(), exception);
                continue;
            }

            if (!ModRequirements.areMet(json)) continue;

            StageAddition addition = StageAddition.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> LOGGER.error("Failed to parse stage addition {}: {}", entry.getKey(), error))
                    .orElse(null);
            if (addition == null) continue;

            boolean matched = false;
            for (Map.Entry<ResourceLocation, StageDefinition> stage : stages.entrySet()) {
                if (stage.getValue().order() == addition.order()) {
                    stage.setValue(addition.applyTo(stage.getValue()));
                    matched = true;
                }
            }

            if (matched) {
                applied++;
            } else {
                LOGGER.warn("Stage addition {} targets order {}, but no stage with that order is loaded.", entry.getKey(), addition.order());
            }
        }
        return applied;
    }

    private int applyRaids(Map<ResourceLocation, StageDefinition> stages, ResourceManager resourceManager) {
        Map<ResourceLocation, Resource> resources = new TreeMap<>(
                resourceManager.listResources(RAIDS_DIRECTORY, path -> path.getPath().endsWith(".json"))
        );
        int applied = 0;

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            JsonElement json;
            try (Reader reader = entry.getValue().openAsReader()) {
                json = JsonParser.parseReader(reader);
            } catch (Exception exception) {
                LOGGER.error("Failed to read stage raid {}", entry.getKey(), exception);
                continue;
            }

            if (!ModRequirements.areMet(json)) continue;
            if (!json.isJsonObject() || !json.getAsJsonObject().has("order")) {
                LOGGER.error("Stage raid {} is missing its 'order' field.", entry.getKey());
                continue;
            }

            int order = json.getAsJsonObject().get("order").getAsInt();
            StageRaid raid = StageRaid.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> LOGGER.error("Failed to parse stage raid {}: {}", entry.getKey(), error))
                    .orElse(null);
            if (raid == null) continue;

            boolean matched = false;
            for (Map.Entry<ResourceLocation, StageDefinition> stage : stages.entrySet()) {
                if (stage.getValue().order() == order) {
                    stage.setValue(StageAddition.withRaid(stage.getValue(), raid));
                    matched = true;
                }
            }

            if (matched) {
                applied++;
            } else {
                LOGGER.warn("Stage raid {} targets order {}, but no stage with that order is loaded.", entry.getKey(), order);
            }
        }
        return applied;
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new StageDataLoader());
    }
}