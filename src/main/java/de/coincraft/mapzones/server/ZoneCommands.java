package de.coincraft.mapzones.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.coincraft.mapzones.zone.ChunkRect;
import de.coincraft.mapzones.zone.Zone;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ColumnPos;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /mapzone ... als Alternative zur Weltkarten-Oberflaeche. */
public final class ZoneCommands {

    private static final SuggestionProvider<CommandSourceStack> ZONE_NAMES = (context, builder) -> {
        MinecraftServer server = context.getSource().getServer();
        ResourceLocation dim = context.getSource().getLevel().dimension().location();
        List<String> names = ZoneStore.get(server).all().stream()
            .filter(zone -> zone.dimension().equals(dim))
            .map(zone -> "\"" + zone.name() + "\"")
            .toList();
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private ZoneCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("mapzone")
            .then(Commands.literal("list").executes(ZoneCommands::list))
            .then(Commands.literal("info")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ZONE_NAMES)
                    .executes(ZoneCommands::info)))
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("from", ColumnPosArgument.columnPos())
                        .then(Commands.argument("to", ColumnPosArgument.columnPos())
                            .executes(context -> create(context, ZoneColors.DEFAULT_COLOR))
                            .then(Commands.argument("color", StringArgumentType.word())
                                .suggests(ZoneColors.SUGGESTIONS)
                                .executes(context -> create(context, parseColor(StringArgumentType.getString(context, "color")))))))))
            .then(Commands.literal("here")
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("radiusChunks", IntegerArgumentType.integer(0, 32))
                        .executes(context -> here(context, ZoneColors.DEFAULT_COLOR))
                        .then(Commands.argument("color", StringArgumentType.word())
                            .suggests(ZoneColors.SUGGESTIONS)
                            .executes(context -> here(context, parseColor(StringArgumentType.getString(context, "color"))))))))
            .then(Commands.literal("add")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ZONE_NAMES)
                    .then(Commands.argument("from", ColumnPosArgument.columnPos())
                        .then(Commands.argument("to", ColumnPosArgument.columnPos())
                            .executes(ZoneCommands::addArea)))))
            .then(Commands.literal("merge")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ZONE_NAMES)
                    .then(Commands.argument("other", StringArgumentType.string())
                        .suggests(ZONE_NAMES)
                        .executes(ZoneCommands::merge))))
            .then(Commands.literal("rename")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ZONE_NAMES)
                    .then(Commands.argument("newName", StringArgumentType.string())
                        .executes(ZoneCommands::rename))))
            .then(Commands.literal("color")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ZONE_NAMES)
                    .then(Commands.argument("color", StringArgumentType.word())
                        .suggests(ZoneColors.SUGGESTIONS)
                        .executes(ZoneCommands::recolor))))
            .then(Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ZONE_NAMES)
                    .executes(ZoneCommands::delete)));

        event.getDispatcher().register(root);
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ResourceLocation dim = source.getLevel().dimension().location();
        List<Zone> zones = ZoneStore.get(source.getServer()).all().stream()
            .filter(zone -> zone.dimension().equals(dim))
            .toList();
        if (zones.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.mapzones.list_empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.mapzones.list_header", zones.size()), false);
        for (Zone zone : zones) {
            source.sendSuccess(() -> Component.literal(" ▪ ").withStyle(style -> style.withColor(zone.color()))
                .append(Component.literal(zone.name()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.format(" %d Chunks / %d Teile - %s",
                        zone.chunkCount(), zone.rectCount(),
                        zone.ownerName() == null || zone.ownerName().isEmpty() ? "-" : zone.ownerName()))
                    .withStyle(ChatFormatting.GRAY)), false);
        }
        return zones.size();
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Zone zone = findZone(context, "name");
        source.sendSuccess(() -> Component.literal("▪ ").withStyle(style -> style.withColor(zone.color()))
            .append(Component.literal(zone.name()).withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.translatable("message.mapzones.info_area",
            zone.minChunkX() << 4, zone.minChunkZ() << 4, (zone.maxChunkX() << 4) + 15, (zone.maxChunkZ() << 4) + 15,
            zone.chunkCount(), zone.rectCount()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.translatable("message.mapzones.info_meta",
            String.format("#%06X", zone.color()),
            zone.ownerName() == null || zone.ownerName().isEmpty() ? "-" : zone.ownerName()).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> context, int color) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        return apply(source, ZoneManager.createOrExtend(source.getServer(), ZoneActor.of(source),
            StringArgumentType.getString(context, "name"), color,
            source.getLevel().dimension().location(), rectFromArguments(context)));
    }

    private static int here(CommandContext<CommandSourceStack> context, int color) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        int radius = IntegerArgumentType.getInteger(context, "radiusChunks");
        int chunkX = SectionPos.blockToSectionCoord(source.getPosition().x);
        int chunkZ = SectionPos.blockToSectionCoord(source.getPosition().z);
        ChunkRect rect = ChunkRect.of(chunkX - radius, chunkZ - radius, chunkX + radius, chunkZ + radius);
        return apply(source, ZoneManager.createOrExtend(source.getServer(), ZoneActor.of(source),
            StringArgumentType.getString(context, "name"), color,
            source.getLevel().dimension().location(), rect));
    }

    private static int addArea(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Zone zone = findZone(context, "name");
        return apply(source, ZoneManager.addArea(source.getServer(), ZoneActor.of(source),
            zone.id(), rectFromArguments(context)));
    }

    private static int merge(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Zone target = findZone(context, "name");
        Zone other = findZone(context, "other");
        return apply(source, ZoneManager.merge(source.getServer(), ZoneActor.of(source), target.id(), other.id()));
    }

    private static int rename(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Zone zone = findZone(context, "name");
        return apply(source, ZoneManager.edit(source.getServer(), ZoneActor.of(source), zone.id(),
            StringArgumentType.getString(context, "newName"), zone.color()));
    }

    private static int recolor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Zone zone = findZone(context, "name");
        return apply(source, ZoneManager.edit(source.getServer(), ZoneActor.of(source), zone.id(),
            zone.name(), parseColor(StringArgumentType.getString(context, "color"))));
    }

    private static int delete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Zone zone = findZone(context, "name");
        return apply(source, ZoneManager.delete(source.getServer(), ZoneActor.of(source), zone.id()));
    }

    private static ChunkRect rectFromArguments(CommandContext<CommandSourceStack> context) {
        ColumnPos from = ColumnPosArgument.getColumnPos(context, "from");
        ColumnPos to = ColumnPosArgument.getColumnPos(context, "to");
        return ChunkRect.of(
            SectionPos.blockToSectionCoord(from.x()), SectionPos.blockToSectionCoord(from.z()),
            SectionPos.blockToSectionCoord(to.x()), SectionPos.blockToSectionCoord(to.z()));
    }

    private static int apply(CommandSourceStack source, ZoneManager.Result result) throws CommandSyntaxException {
        if (!result.success()) {
            throw new SimpleCommandExceptionType(result.message()).create();
        }
        source.sendSuccess(result::message, true);
        return 1;
    }

    private static Zone findZone(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, argument);
        ResourceLocation dim = source.getLevel().dimension().location();
        ZoneStore store = ZoneStore.get(source.getServer());
        List<Zone> matches = store.byName(name, dim);
        if (matches.isEmpty()) {
            matches = store.byName(name, null);
        }
        if (matches.isEmpty()) {
            throw error("message.mapzones.unknown_zone");
        }
        if (matches.size() > 1) {
            throw error("message.mapzones.ambiguous_zone", matches.size());
        }
        return matches.get(0);
    }

    /** Akzeptiert #RRGGBB, RRGGBB oder einen Namen aus der Palette. */
    public static int parseColor(String raw) throws CommandSyntaxException {
        String value = raw.trim();
        Integer named = ZoneColors.byName(value);
        if (named != null) {
            return named;
        }
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.startsWith("0x") || value.startsWith("0X")) {
            value = value.substring(2);
        }
        try {
            return Integer.parseInt(value, 16) & 0xFFFFFF;
        } catch (NumberFormatException exception) {
            throw error("message.mapzones.bad_color", raw);
        }
    }

    private static CommandSyntaxException error(String key, Object... args) {
        return new SimpleCommandExceptionType(Component.translatable(key, args)).create();
    }
}
