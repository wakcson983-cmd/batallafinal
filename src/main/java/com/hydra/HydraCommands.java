package com.hydra;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class HydraCommands {
    private HydraCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        // /batalla final iniciar | detener
        d.register(CommandManager.literal("batalla")
                .requires(s -> s.hasPermissionLevel(2))
                .then(CommandManager.literal("final")
                        .then(CommandManager.literal("iniciar").executes(ctx -> {
                            String err = BattleManager.startBattle(ctx.getSource().getServer());
                            if (err != null) {
                                ctx.getSource().sendError(Text.literal(err));
                                return 0;
                            }
                            ctx.getSource().sendFeedback(() -> Text.literal("Batalla final iniciada."), true);
                            return 1;
                        }))
                        .then(CommandManager.literal("detener").executes(ctx -> {
                            BattleManager.stopBattle(ctx.getSource().getServer());
                            ctx.getSource().sendFeedback(() -> Text.literal("Batalla final detenida."), true);
                            return 1;
                        }))));

        // /hydra invocar | evento  (por si la ruleta no se detecta, y para probar)
        d.register(CommandManager.literal("hydra")
                .requires(s -> s.hasPermissionLevel(2))
                .then(CommandManager.literal("invocar").executes(ctx -> {
                    if (!BattleManager.summon(ctx.getSource().getServer())) {
                        ctx.getSource().sendError(Text.literal("No se pudo invocar (ya hay una HYDRA o no hay jugadores)."));
                        return 0;
                    }
                    return 1;
                }))
                .then(CommandManager.literal("evento").executes(ctx -> {
                    if (!BattleManager.isActive()) {
                        ctx.getSource().sendError(Text.literal("Primero inicia la batalla: /batalla final iniciar"));
                        return 0;
                    }
                    if (!HydraEvents.forceStart(ctx.getSource().getServer())) {
                        ctx.getSource().sendError(Text.literal("Ya hay un evento en curso."));
                        return 0;
                    }
                    return 1;
                })));
    }
}
