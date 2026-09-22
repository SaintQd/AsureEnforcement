package org.saintqd.asureenforcement.listeners

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.scheduler.BukkitRunnable
import org.saintqd.asureenforcement.AsureEnforcement
import org.saintqd.asureenforcement.managers.EnforcementManager
import org.saintqd.asurelib.managers.LangManager
import org.saintqd.asurelib.utils.AsureUtils
import java.util.concurrent.ThreadLocalRandom

class PlayerListener : Listener {

    @EventHandler
    fun onPlayerAttack(event : EntityDamageEvent) {
        if (event.isCancelled) return

        val entity = event.entity
        val damager = event.damageSource.causingEntity

        if (entity is Player && damager is Player) {
            if (entity.hasPermission("asureenforcement.bypass") || entity.gameMode == GameMode.CREATIVE || entity.gameMode == GameMode.SPECTATOR)
                return
            val handItem = damager.inventory.itemInMainHand
            if (handItem.type != Material.AIR && handItem.itemMeta.persistentDataContainer.has(EnforcementManager.BATON_KEY)) {

                val batonPermission = AsureEnforcement.inst().config.getString("baton.permission","asureenforcement.baton")!!
                if (batonPermission.isNotEmpty() && !damager.hasPermission(batonPermission)) {
                    event.isCancelled = true
                    return
                }

                val requiredHealthPercent = AsureEnforcement.inst().config.getDouble("baton.min_health_percent_to_stun", 0.5)

                val currentHealth = entity.health
                val maxHealth = entity.getAttribute(Attribute.MAX_HEALTH)!!.value

                if (currentHealth / maxHealth > requiredHealthPercent) {

                    val neededHealth = (maxHealth * requiredHealthPercent).toInt()

                    damager.sendMessage { LangManager.INSTANCE.parseLangString(AsureEnforcement.inst(),"baton_stun_hint",neededHealth.toString()) }
                    event.isCancelled = true
                    return
                }

                val lastStunTimestamp = EnforcementManager.instance.stunnedPlayers[event.entity.uniqueId] ?: 0
                val stunPeriod = AsureEnforcement.inst().config.getLong("baton.stun_period",80L)
                val stunCooldown = AsureEnforcement.inst().config.getLong("baton.cooldown",60L) + stunPeriod

                if (lastStunTimestamp + stunCooldown < AsureUtils.getCurrentTick()) {
                    entity.sendMessage { LangManager.INSTANCE.parseLangString(AsureEnforcement.inst(),"baton_stun_message") }
                    val stunTimestamp = AsureUtils.getCurrentTick()
                    EnforcementManager.instance.stunnedPlayers[event.entity.uniqueId] = stunTimestamp

                    val originalLoc = entity.location.clone()

                    val task = object : BukkitRunnable() {
                        override fun run() {
                            if (entity.isValid)
                                entity.teleport(originalLoc)
                            if (AsureUtils.getCurrentTick() >= stunTimestamp + stunPeriod)
                                this.cancel()
                        }
                    }.runTaskTimer(AsureEnforcement.inst(),2L,2L)

                }
                else
                    event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onPlayerInteractWithPlayer(event : PlayerInteractAtEntityEvent) {
        if (event.hand != EquipmentSlot.HAND)
            return
        val rightClickedEntity = event.rightClicked
        if (rightClickedEntity is Player) {

            if (EnforcementManager.instance.handcuffedPlayers.contains(event.player.uniqueId)) {
                return
            }

            val cufferPlayer = event.player
            val handItem = event.player.inventory.itemInMainHand

            EnforcementManager.instance.handcuffedPlayers[rightClickedEntity.uniqueId]?.let { handcuffedPlayerData ->
                if (handcuffedPlayerData.cufferPlayerUuid == event.player.uniqueId) {
                    if (handItem.type == Material.AIR || (!handItem.itemMeta.persistentDataContainer.has(EnforcementManager.HANDCUFFS_KEYS_KEY) && !handItem.itemMeta.persistentDataContainer.has(EnforcementManager.CUFFBREAKERS_KEY))) {
                        if (!handcuffedPlayerData.isPulled) {
                            handcuffedPlayerData.isPulled = true
                            event.player.sendMessage { LangManager.INSTANCE.parseLangString(AsureEnforcement.inst(),"handcuffs_escort_start",rightClickedEntity.name) }
                            event.isCancelled = true
                            return
                        }
                        else {
                            handcuffedPlayerData.isPulled = false
                            event.player.sendMessage { LangManager.INSTANCE.parseLangString(AsureEnforcement.inst(),"handcuffs_escort_stop",rightClickedEntity.name) }
                            event.isCancelled = true
                            return
                        }
                    }
                }
            }

            if (handItem.type != Material.AIR) {
                if (handItem.itemMeta.persistentDataContainer.has(EnforcementManager.HANDCUFFS_KEY)) {

                    val handcuffsPermission = AsureEnforcement.inst().config.getString("handcuffs.permission","asureenforcement.handcuffs")!!
                    if (handcuffsPermission.isNotEmpty() && !cufferPlayer.hasPermission(handcuffsPermission)) {
                        event.isCancelled = true
                        return
                    }

                    val lastStunTimestamp = EnforcementManager.instance.stunnedPlayers[rightClickedEntity.uniqueId] ?: 0
                    val stunPeriod = AsureEnforcement.inst().config.getLong("baton.stun_period",80L)

                    if (lastStunTimestamp + stunPeriod >= AsureUtils.getCurrentTick()) {
                        var distance = event.player.location.distance(rightClickedEntity.location)
                        val maxCuffDistance = AsureEnforcement.inst().config.getDouble("handcuffs.max_cuff_distance",3.0)

                        if (distance > maxCuffDistance) {
                            event.player.sendMessage { LangManager.INSTANCE.parseLangString(AsureEnforcement.inst(),"handcuffs_over_cuff_distance") }
                            return
                        }
                        val progressFormat = AsureEnforcement.inst().config.getString("handcuffs.progress_format","[||||||||||||||||||||]")!!
                        val completedProgressColor = AsureEnforcement.inst().config.getString("handcuffs.completed_progress_color","<green>")!!
                        val remainingProgressColor = AsureEnforcement.inst().config.getString("handcuffs.remaining_progress_color","<gray>")!!

                        val progressFormatLength = progressFormat.length
                        val minProgress = AsureEnforcement.inst().config.getInt("handcuffs.min_progress",50)
                        val maxProgress = AsureEnforcement.inst().config.getInt("handcuffs.max_progress",60)
                        val requiredProgress = if (minProgress !in 0..<maxProgress) minProgress
                        else ThreadLocalRandom.current().nextInt(minProgress, maxProgress + 1)

                        event.isCancelled = true

                        EnforcementManager.instance.handcuffProgressTasks[event.player.uniqueId]?.cancel()
                        EnforcementManager.instance.handcuffProgress[event.player.uniqueId] = 0

                        val task = object : BukkitRunnable() {
                            override fun run() {
                                if (!rightClickedEntity.isValid || !cufferPlayer.isValid) {
                                    EnforcementManager.instance.handcuffProgressTasks.remove(cufferPlayer.uniqueId)
                                    this.cancel()
                                    return
                                }
                                val handItem = cufferPlayer.inventory.itemInMainHand
                                if (handItem.type == Material.AIR || !handItem.itemMeta.persistentDataContainer.has(EnforcementManager.HANDCUFFS_KEY)) {
                                    EnforcementManager.instance.handcuffProgressTasks.remove(cufferPlayer.uniqueId)
                                    this.cancel()
                                    return
                                }
                                distance = event.player.location.distance(rightClickedEntity.location)
                                if (distance > maxCuffDistance) {
                                    event.player.sendMessage { LangManager.INSTANCE.parseLangString(AsureEnforcement.inst(),"handcuffs_over_cuff_distance") }
                                    this.cancel()
                                    return
                                }
                                var currentProgress = EnforcementManager.instance.handcuffProgress[event.player.uniqueId] ?: 0
                                currentProgress += 5
                                val progressCoef = currentProgress.toDouble() / requiredProgress.toDouble()
                                val stringIndex = (progressFormatLength * progressCoef).toInt()

                                val changedString = StringBuilder(progressFormat)
                                changedString.insert(stringIndex.coerceAtMost(changedString.length - 1),remainingProgressColor)
                                changedString.insert(0, completedProgressColor)

                                cufferPlayer.world.playSound(cufferPlayer.location,Sound.ENTITY_CHICKEN_STEP, SoundCategory.PLAYERS,1f,1f)
                                cufferPlayer.sendActionBar(MiniMessage.miniMessage().deserialize(changedString.toString()))

                                if (currentProgress >= requiredProgress) {
                                    val textDisplay = EnforcementManager.instance.createTextDisplay(rightClickedEntity)
                                    EnforcementManager.instance.handcuffedPlayers[rightClickedEntity.uniqueId] = EnforcementManager.CuffedPlayer(
                                        rightClickedEntity.uniqueId,cufferPlayer.uniqueId,false,0L,textDisplay, AsureUtils.getCurrentTick())
                                    cufferPlayer.sendMessage { LangManager.INSTANCE.parseLangString(AsureEnforcement.inst(),
                                        "handcuffs_cuffer_message",rightClickedEntity.name) }
                                    rightClickedEntity.sendMessage { LangManager.INSTANCE.parseLangString(AsureEnforcement.inst(),
                                        "handcuffs_message",cufferPlayer.name) }
                                    EnforcementManager.instance.handcuffProgressTasks.remove(cufferPlayer.uniqueId)
                                    this.cancel()
                                }
                                else
                                    EnforcementManager.instance.handcuffProgress[event.player.uniqueId] = currentProgress
                            }
                        }.runTaskTimer(AsureEnforcement.inst(),5L,5L)

                        EnforcementManager.instance.handcuffProgressTasks[event.player.uniqueId] = task
                    }
                }
                else if (handItem.itemMeta.persistentDataContainer.has(EnforcementManager.HANDCUFFS_KEYS_KEY)) {
                    EnforcementManager.instance.handcuffedPlayers[rightClickedEntity.uniqueId] ?: return

                    val handcuffsPermission = AsureEnforcement.inst().config.getString("handcuffs.permission","asureenforcement.handcuffs")!!
                    if (handcuffsPermission.isNotEmpty() && !event.player.hasPermission(handcuffsPermission)) {
                        event.isCancelled = true
                        return
                    }

                    val handcuffedPlayerData = EnforcementManager.instance.handcuffedPlayers.remove(rightClickedEntity.uniqueId)
                    handcuffedPlayerData?.textDisplay?.remove()

                    rightClickedEntity.world.playSound(rightClickedEntity.location,Sound.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS,1f,1f)
                    event.player.sendMessage { LangManager.INSTANCE.parseLangString(AsureEnforcement.inst(),"handcuffs_keys_user_message",rightClickedEntity.name) }
                    rightClickedEntity.sendMessage { LangManager.INSTANCE.parseLangString(AsureEnforcement.inst(),"handcuffs_keys_use_message",event.player.name) }
                }
                else if (handItem.itemMeta.persistentDataContainer.has(EnforcementManager.CUFFBREAKERS_KEY)) {
                    EnforcementManager.instance.handcuffedPlayers[rightClickedEntity.uniqueId] ?: return

                    val cuffbreakersPermission = AsureEnforcement.inst().config.getString("cuffbreakers.permission","asureenforcement.cuffbreakers")!!
                    if (cuffbreakersPermission.isNotEmpty() && !event.player.hasPermission(cuffbreakersPermission)) {
                        event.isCancelled = true
                        return
                    }

                    val handcuffedPlayerData = EnforcementManager.instance.handcuffedPlayers.remove(rightClickedEntity.uniqueId)
                    handcuffedPlayerData?.textDisplay?.remove()

                    rightClickedEntity.world.playSound(rightClickedEntity.location,Sound.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS,1f,1f)
                    event.player.sendMessage { LangManager.INSTANCE.parseLangString(AsureEnforcement.inst(),"handcuffs_keys_user_message",rightClickedEntity.name) }
                    rightClickedEntity.sendMessage { LangManager.INSTANCE.parseLangString(AsureEnforcement.inst(),"handcuffs_keys_use_message",event.player.name) }

                    if (AsureEnforcement.inst().config.getBoolean("cuffbreakers.remove_on_use",true))
                        handItem.amount -= 1
                }
            }
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND)
            return
        if (EnforcementManager.instance.handcuffedPlayers.contains(event.player.uniqueId)) {
            val handcuffedPlayerData = EnforcementManager.instance.handcuffedPlayers[event.player.uniqueId]!!
            if (event.player.inventory.itemInMainHand.type == Material.AIR && (event.action == Action.LEFT_CLICK_AIR || event.action == Action.LEFT_CLICK_BLOCK)) {
                val breakTimeAmount = AsureEnforcement.inst().config.getInt("handcuffs.break_time_increase_per_attempt",30)

                if (AsureUtils.getCurrentTick() >= handcuffedPlayerData.lastBreakAttempt + breakTimeAmount) {
                    if (EnforcementManager.instance.advanceBreakProgress(handcuffedPlayerData, breakTimeAmount))
                        EnforcementManager.instance.handcuffedPlayers.remove(handcuffedPlayerData.uuid)
                }
            }
            else {
                event.player.sendMessage { LangManager.INSTANCE.parseLangString(AsureEnforcement.inst(),"handcuffed_message_hint") }
            }
            event.isCancelled = true
            return
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {

        for (cuffedPlayerData in EnforcementManager.instance.handcuffedPlayers.values) {
            if (cuffedPlayerData.cufferPlayerUuid == event.player.uniqueId) {
                cuffedPlayerData.isPulled = false
            }
        }

        EnforcementManager.instance.handcuffedPlayers[event.player.uniqueId]?.textDisplay?.remove()
        EnforcementManager.instance.handcuffedPlayers.remove(event.player.uniqueId)
        EnforcementManager.instance.stunnedPlayers.remove(event.player.uniqueId)
    }
}