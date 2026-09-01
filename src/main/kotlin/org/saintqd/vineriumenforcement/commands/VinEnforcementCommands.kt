package org.saintqd.vineriumenforcement.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.saintqd.vineriumenforcement.VineriumEnforcement
import org.saintqd.vineriumenforcement.managers.EnforcementManager
import org.saintqd.vineriumenforcement.worldguard.VinEnforcementFlags
import org.saintqd.vineriumlib.VineriumLib
import org.saintqd.vineriumlib.utils.VinUtils

class VinEnforcementCommands {

    companion object {
        fun setupCommands(plugin : VineriumEnforcement) {
            val manager = plugin.lifecycleManager
            manager.registerEventHandler(LifecycleEvents.COMMANDS) {
                val commands: Commands = it.registrar()
                commands.register(
                    Commands.literal("vinenforcement")
                        .executes { commandContext: CommandContext<CommandSourceStack> ->
                            commandContext.getSource().sender.sendMessage(
                                VineriumLib.inst().langManager.parseLangString(
                                    VineriumEnforcement.inst(),
                                    "not_enough_arguments"
                                )
                            )
                            Command.SINGLE_SUCCESS
                        }
                        .then(
                            Commands.literal("reload")
                                .requires { predicate: CommandSourceStack ->
                                    predicate.sender.hasPermission("vineriumenforcement.admin")
                                }
                                .executes { ctx: CommandContext<CommandSourceStack> ->
                                    reloadCommand(
                                        ctx.getSource().sender
                                    )
                                    Command.SINGLE_SUCCESS
                                }
                        )
                        .then(
                            Commands.literal("cleartasks")
                                .requires { predicate: CommandSourceStack ->
                                    predicate.sender.hasPermission("vineriumenforcement.admin")
                                }
                                .executes { ctx: CommandContext<CommandSourceStack> ->
                                    clearTasksCommand(
                                        ctx.getSource().sender
                                    )
                                    Command.SINGLE_SUCCESS
                                }
                        )
                        .then(
                            Commands.literal("jail")
                                .requires { predicate: CommandSourceStack ->
                                    predicate.sender.hasPermission("vineriumenforcement.jail")
                                }
                                .then(
                                    Commands.argument("player", ArgumentTypes.player())
                                        .then(
                                            Commands.argument("time", IntegerArgumentType.integer(1))
                                                .executes { ctx: CommandContext<CommandSourceStack> ->
                                                    jailPlayerCommand(
                                                        ctx.getSource().sender,
                                                        ctx.lastChild.getArgument(
                                                            "player",
                                                            PlayerSelectorArgumentResolver::class.java
                                                        ).resolve(ctx.getSource()).first(),
                                                        ctx.getArgument("time", Int::class.java)
                                                    )
                                                    Command.SINGLE_SUCCESS
                                                }
                                        )
                                )
                        )
                        .then(
                            Commands.literal("unjail")
                                .requires { predicate: CommandSourceStack ->
                                    predicate.sender.hasPermission("vineriumenforcement.unjail")
                                }
                                .then(
                                    Commands.argument("player", ArgumentTypes.player())
                                        .executes { ctx: CommandContext<CommandSourceStack> ->
                                            unjailPlayerCommand(
                                                ctx.getSource().sender,
                                                ctx.getArgument(
                                                    "player",
                                                    PlayerSelectorArgumentResolver::class.java
                                                ).resolve(ctx.getSource()).first()
                                            )
                                            Command.SINGLE_SUCCESS
                                        }
                                )
                        )
                        .build(),
                    "Основная команда."
                )
            }
        }

        private fun reloadCommand(sender: CommandSender) {
            VineriumEnforcement.inst().loadData()
            sender.sendMessage(VineriumLib.inst().langManager.parseLangString(VineriumEnforcement.inst(), "command_reload_message"))
        }

        private fun clearTasksCommand(sender: CommandSender) {
            EnforcementManager.instance.clearTasks()
            sender.sendMessage(VineriumLib.inst().langManager.parseLangString(VineriumEnforcement.inst(), "command_clear_tasks_message"))
        }

        private fun jailPlayerCommand(sender: CommandSender, player : Player, time: Int)  {

            if (!VineriumEnforcement.inst().worldGuardEnabled) {
                sender.sendMessage(VineriumLib.inst().langManager.parseLangString(VineriumEnforcement.inst(), "jail_no_worldguard"))
                return
            }

            val container = com.sk89q.worldguard.WorldGuard.getInstance().platform.regionContainer
            val localPlayer = com.sk89q.worldguard.bukkit.WorldGuardPlugin.inst().wrapPlayer(player)

            val cuffedPlayerData = EnforcementManager.instance.handcuffedPlayers[player.uniqueId]
            if (cuffedPlayerData == null) {
                sender.sendMessage(VineriumLib.inst().langManager.parseLangString(VineriumEnforcement.inst(), "jail_player_not_cuffed",player.name))
                return
            }

            val maxJailTime = VineriumEnforcement.inst().config.getInt("jail.max_time",240)
            if (time > maxJailTime) {
                sender.sendMessage(VineriumLib.inst().langManager.parseLangString(VineriumEnforcement.inst(), "jail_wrong_time",maxJailTime.toString()))
                return
            }

            val jailName = container.createQuery().queryValue(localPlayer.location,localPlayer, VinEnforcementFlags.ENFORCEMENT_JAIL_REGION)
            if (jailName == null) {
                sender.sendMessage(VineriumLib.inst().langManager.parseLangString(VineriumEnforcement.inst(), "jail_wrong_region"))
                return
            }

            val defaultCellName = VineriumEnforcement.inst().config.getString("jail.default_cell_name","1")!!

            val silent = if (VineriumEnforcement.inst().config.getBoolean("jail.silent", true))
                VineriumEnforcement.inst().config.getString("jail.silent_format","-s")!!
            else ""

            val jailCommand = VineriumEnforcement.inst().config.getString("jail.command","jail %player_name% %time% %jail_name% %cell_name% %silent%")!!
                .replace("%player_name%", player.name)
                .replace("%time%", time.toString())
                .replace("%jail_name%",jailName)
                .replace("%cell_name%",defaultCellName)
                .replace("%silent%",silent)

            Bukkit.getServer().dispatchCommand(Bukkit.getServer().consoleSender,jailCommand)

            sender.sendMessage(VineriumLib.inst().langManager.parseLangString(VineriumEnforcement.inst(), "jail_receiver_message",sender.name,time.toString()))
            sender.sendMessage(VineriumLib.inst().langManager.parseLangString(VineriumEnforcement.inst(), "jail_success_message",player.name,time.toString()))

        }

        private fun unjailPlayerCommand(sender: CommandSender, player : Player)  {

            val unjailCommand = VineriumEnforcement.inst().config.getString("jail.unjail_command","unjail %player_name%")!!
                .replace("%player_name%", player.name)

            Bukkit.getServer().dispatchCommand(Bukkit.getServer().consoleSender,unjailCommand)

            sender.sendMessage(VineriumLib.inst().langManager.parseLangString(VineriumEnforcement.inst(), "unjail_success_message",player.name))

        }

    }
}