package com.github.xandergos.terraindiffusionmc;

import com.github.xandergos.terraindiffusionmc.pipeline.CoarseTerrainSearch;
import com.github.xandergos.terraindiffusionmc.pipeline.CoarseTerrainSearch.Feature;
import com.github.xandergos.terraindiffusionmc.world.HeightConverter;
import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionBiomeSource;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@code /td-locate <mountains|highest|flatlands|ocean|land> [radius]}: finds terrain features on the
 * coarse map. Runs off the server thread because the coarse model may need to run for unexplored areas.
 */
final class TerrainDiffusionLocateCommand {
    private static final Logger LOG = LoggerFactory.getLogger(TerrainDiffusionLocateCommand.class);

    private static final int DEFAULT_RADIUS = 8000;
    private static final int MIN_RADIUS = 256;
    private static final int MAX_RADIUS = 50000;
    /** Blocks above the estimated surface for the suggested teleport, since the estimate is coarse. */
    private static final int TELEPORT_HEADROOM = 16;

    private static final ExecutorService SEARCH_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "terrain-diffusion-locate");
        thread.setDaemon(true);
        return thread;
    });

    private TerrainDiffusionLocateCommand() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("td-locate")
                .requires(source -> source.hasPermission(2));
        for (Feature feature : Feature.values()) {
            String name = feature.name().toLowerCase(Locale.ROOT);
            root.then(Commands.literal(name)
                    .executes(ctx -> run(ctx, feature, DEFAULT_RADIUS))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(MIN_RADIUS, MAX_RADIUS))
                            .executes(ctx -> run(ctx, feature, IntegerArgumentType.getInteger(ctx, "radius")))));
        }
        dispatcher.register(root);
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Feature feature, int radius) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        if (level.dimension() != Level.OVERWORLD
                || !(level.getChunkSource().getGenerator().getBiomeSource() instanceof TerrainDiffusionBiomeSource)) {
            source.sendFailure(Component.literal("/td-locate only works in the overworld of a Terrain Diffusion world."));
            return 0;
        }

        Vec3 position = source.getPosition();
        int originX = Mth.floor(position.x);
        int originZ = Mth.floor(position.z);
        int maxY = level.getMaxBuildHeight() - 1;
        MinecraftServer server = source.getServer();
        String label = describe(feature);

        source.sendSuccess(() -> Component.literal("Searching for " + label + " within " + radius + " blocks...")
                .withStyle(ChatFormatting.GRAY), false);

        SEARCH_EXECUTOR.execute(() -> {
            try {
                CoarseTerrainSearch.Hit hit = CoarseTerrainSearch.forCurrentWorld()
                        .findFeature(originX, originZ, radius, feature);
                server.execute(() -> report(source, feature, label, hit, originX, originZ, radius, maxY));
            } catch (Exception e) {
                LOG.error("td-locate {} failed", label, e);
                server.execute(() -> source.sendFailure(Component.literal("Search failed: " + e.getMessage())));
            }
        });
        return 1;
    }

    private static void report(CommandSourceStack source, Feature feature, String label, CoarseTerrainSearch.Hit hit,
                               int originX, int originZ, int radius, int maxY) {
        if (hit == null) {
            source.sendFailure(Component.literal("No " + label + " found within " + radius + " blocks."));
            return;
        }

        int scale = WorldScaleManager.getCurrentScale();
        // Aim above the upper part of the cell so the suggested teleport doesn't land inside terrain.
        int surfaceY = HeightConverter.estimateMinecraftHeight(Math.max(hit.meanM(), 0f) + hit.reliefM(), scale);
        int teleportY = Math.min(maxY, Math.max(surfaceY, 63) + TELEPORT_HEADROOM);
        int distance = (int) Math.round(Math.hypot(hit.blockX() - originX, hit.blockZ() - originZ));

        String teleport = "/tp @s " + hit.blockX() + " " + teleportY + " " + hit.blockZ();
        MutableComponent coordinates = Component.literal("[" + hit.blockX() + ", ~, " + hit.blockZ() + "]")
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, teleport))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to suggest " + teleport))));

        String details = String.format(Locale.ROOT,
                " (%d blocks away): average elevation %.0f m, relief %.0f m. Area of about %d x %d blocks; "
                        + "the exact spot inside it may differ.",
                distance, hit.meanM(), hit.reliefM(), hit.cellBlocks(), hit.cellBlocks());

        String prefix = feature == Feature.HIGHEST
                ? "Highest ground within " + radius + " blocks: "
                : "Nearest " + label + ": ";
        source.sendSuccess(() -> Component.literal(prefix).append(coordinates)
                .append(Component.literal(details)), false);
    }

    private static String describe(Feature feature) {
        switch (feature) {
            case MOUNTAINS:
                return "mountains";
            case HIGHEST:
                return "highest ground";
            case FLATLANDS:
                return "flat lowland";
            case OCEAN:
                return "ocean";
            case LAND:
                return "land";
            default:
                return feature.name().toLowerCase(Locale.ROOT);
        }
    }
}
