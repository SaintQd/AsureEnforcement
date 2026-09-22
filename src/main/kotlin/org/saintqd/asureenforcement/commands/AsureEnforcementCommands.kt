package org.saintqd.asureenforcement.commands

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
import org.saintqd.asureenforcement.AsureEnforcement
import org.saintqd.asureenforcement.managers.EnforcementManager
import org.saintqd.asureenforcement.worldguard.AsureEnforcementFlags
import org.saintqd.asurelib.AsureLib

class AsureEnforcementCommands {

    companion object {
        fun setupCommands(plugin : AsureEnforcement) {
            val manager = plugin.lifecycleManager
            manager.registerEventHandler(LifecycleEvents.COMMANDS) {
                val commands: Commands = it.registrar()
                commands.register(
                    Commands.literal("asureenforcement")
                        .executes { commandContext: CommandContext<CommandSourceStack> ->
                            commandContext.getSource().sender.sendMessage(
                                AsureLib.inst().langManager.parseLangString(
                                    AsureEnforcement.inst(),
                                    "not_enough_arguments"
                                )
                            )
                            Command.SINGLE_SUCCESS
                        }
                        .then(
                            Commands.literal("reload")
                                .requires { predicate: CommandSourceStack ->
                                    predicate.sender.hasPermission("asureenforcement.admin")
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
                                    predicate.sender.hasPermission("asureenforcement.admin")
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
                                    predicate.sender.hasPermission("asureenforcement.jail")
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
                                    predicate.sender.hasPermission("asureenforcement.unjail")
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
            AsureEnforcement.inst().loadData()
            sender.sendMessage(AsureLib.inst().langManager.parseLangString(AsureEnforcement.inst(), "command_reload_message"))
        }

        private fun clearTasksCommand(sender: CommandSender) {
            EnforcementManager.instance.clearTasks()
            sender.sendMessage(AsureLib.inst().langManager.parseLangString(AsureEnforcement.inst(), "command_clear_tasks_message"))
        }

        private fun jailPlayerCommand(sender: CommandSender, player : Player, time: Int)  {

            if (!AsureEnforcement.inst().worldGuardEnabled) {
                sender.sendMessage(AsureLib.inst().langManager.parseLangString(AsureEnforcement.inst(), "jail_no_worldguard"))
                return
            }

            val container = com.sk89q.worldguard.WorldGuard.getInstance().platform.regionContainer
            val localPlayer = com.sk89q.worldguard.bukkit.WorldGuardPlugin.inst().wrapPlayer(player)

            val cuffedPlayerData = EnforcementManager.instance.handcuffedPlayers[player.uniqueId]
            if (cuffedPlayerData == null) {
                sender.sendMessage(AsureLib.inst().langManager.parseLangString(AsureEnforcement.inst(), "jail_player_not_cuffed",player.name))
                return
            }

            val maxJailTime = AsureEnforcement.inst().config.getInt("jail.max_time",240)
            if (time > maxJailTime) {
                sender.sendMessage(AsureLib.inst().langManager.parseLangString(AsureEnforcement.inst(), "jail_wrong_time",maxJailTime.toString()))
                return
            }

            val jailName = container.createQuery().queryValue(localPlayer.location,localPlayer, AsureEnforcementFlags.ENFORCEMENT_JAIL_REGION)
            if (jailName == null) {
                sender.sendMessage(AsureLib.inst().langManager.parseLangString(AsureEnforcement.inst(), "jail_wrong_region"))
                return
            }

            val defaultCellName = AsureEnforcement.inst().config.getString("jail.default_cell_name","1")!!

            val silent = if (AsureEnforcement.inst().config.getBoolean("jail.silent", true))
                AsureEnforcement.inst().config.getString("jail.silent_format","-s")!!
            else ""

            val jailCommand = AsureEnforcement.inst().config.getString("jail.command","jail %player_name% %time% %jail_name% %cell_name% %silent%")!!
                .replace("%player_name%", player.name)
                .replace("%time%", time.toString())
                .replace("%jail_name%",jailName)
                .replace("%cell_name%",defaultCellName)
                .replace("%silent%",silent)

            Bukkit.getServer().dispatchCommand(Bukkit.getServer().consoleSender,jailCommand)

            sender.sendMessage(AsureLib.inst().langManager.parseLangString(AsureEnforcement.inst(), "jail_receiver_message",sender.name,time.toString()))
            sender.sendMessage(AsureLib.inst().langManager.parseLangString(AsureEnforcement.inst(), "jail_success_message",player.name,time.toString()))

        }

        private fun unjailPlayerCommand(sender: CommandSender, player : Player)  {

            val unjailCommand = AsureEnforcement.inst().config.getString("jail.unjail_command","unjail %player_name%")!!
                .replace("%player_name%", player.name)

            Bukkit.getServer().dispatchCommand(Bukkit.getServer().consoleSender,unjailCommand)

            sender.sendMessage(AsureLib.inst().langManager.parseLangString(AsureEnforcement.inst(), "unjail_success_message",player.name))

        }

    }
}