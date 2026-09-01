package org.saintqd.vineriumenforcement.managers

import com.destroystokyo.paper.ParticleBuilder
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.TextDisplay
import org.bukkit.scheduler.BukkitTask
import org.saintqd.vineriumenforcement.VineriumEnforcement
import org.saintqd.vineriumenforcement.utils.VinEnforcementUtils
import org.saintqd.vineriumlib.managers.LangManager
import org.saintqd.vineriumlib.utils.VinUtils
import org.w3c.dom.Text
import java.util.*
import kotlin.math.ceil

class EnforcementManager {

    companion object {
        val instance : EnforcementManager = EnforcementManager()

        val BATON_KEY = NamespacedKey(VineriumEnforcement.inst(),"baton")
        val HANDCUFFS_KEY = NamespacedKey(VineriumEnforcement.inst(),"handcuffs")
        val HANDCUFFS_KEYS_KEY = NamespacedKey(VineriumEnforcement.inst(),"handcuffs_keys")
        val CUFFBREAKERS_KEY = NamespacedKey(VineriumEnforcement.inst(),"cuffbreakers")
    }

    var handcuffsCheckTask : BukkitTask? = null
    var escortTask : BukkitTask? = null

    val stunnedPlayers = hashMapOf<UUID, Long>()

    // Отслеживание прогресса заковывания в наручники
    val handcuffProgress = hashMapOf<UUID, Int>()

    // Отслеживание задач заковывания в наручники
    // необходимо для того, чтобы игрок не мог заковывать в наручники сразу несколько игроков
    val handcuffProgressTasks = hashMapOf<UUID, BukkitTask>()

    // hashMap<UUID игрока в наручниках, Данные о игроке>
    val handcuffedPlayers = hashMapOf<UUID, CuffedPlayer>()

    // UUID игроков, закованных в наручники, которые притягиваются к заковавшему их игроку
    //val pulledPlayers = hashSetOf<UUID>()

    // Отслеживание прогресса освобождения от наручников
    //val handcuffsBreakProgress = hashMapOf<UUID, Long>()

    var textDisplayTask : BukkitTask? = null

    data class CuffedPlayer(
        val uuid : UUID,
        val cufferPlayerUuid : UUID,
        var isPulled : Boolean,
        var breakProgress : Long,
        var textDisplay : TextDisplay,
        var lastBreakAttempt : Long
    )

    fun loadParams() {

        val breakDistance = VineriumEnforcement.inst().config.getDouble("handcuffs.break_distance",15.0)

        val taskCheckPeriod = VineriumEnforcement.inst().config.getLong("handcuffs.break_task_check_period",40L)
        val textDisplayTaskPeriod = VineriumEnforcement.inst().config.getLong("text_display.text_display_task_period",10L)
        val textDisplayHeightOffset = VineriumEnforcement.inst().config.getDouble("text_display.height_offset",1.8)

        val escortTaskPeriod = VineriumEnforcement.inst().config.getLong("escort.task_period",5L)
        val minPullDistance = VineriumEnforcement.inst().config.getDouble("handcuffs.min_pull_distance",2.0)

        val particleBuilder = ParticleBuilder(Particle.DUST)
            .color(Color.GRAY)
            .count(4)
            .offset(0.1,0.1,0.1)

        handcuffsCheckTask?.cancel()
        handcuffsCheckTask = Bukkit.getScheduler().runTaskTimer(VineriumEnforcement.inst(),Runnable {
            val handcuffedPlayersToRemove = hashSetOf<UUID>()
            for (handcuffedPlayerData in handcuffedPlayers.values) {
                val handcuffedPlayer = Bukkit.getPlayer(handcuffedPlayerData.uuid) ?: continue
                val cufferPlayer = Bukkit.getPlayer(handcuffedPlayerData.cufferPlayerUuid)
                if (cufferPlayer != null) {
                    val distance = if (handcuffedPlayer.world == cufferPlayer.world) handcuffedPlayer.location.distance(cufferPlayer.location)
                    else Double.MAX_VALUE

                    if (distance > breakDistance) {
                        if (advanceBreakProgress(handcuffedPlayerData,taskCheckPeriod.toInt()))
                            handcuffedPlayersToRemove.add(handcuffedPlayerData.uuid)
                    }
                    else {
                        if (handcuffedPlayerData.isPulled && handcuffedPlayer.world == cufferPlayer.world && distance > minPullDistance) {
                            VinEnforcementUtils.pullEntity(cufferPlayer,handcuffedPlayer,5.0)

                            val distanceBetween = 0.25
                            val pointsAmount = (ceil(distance / distanceBetween) - 1).toInt()
                            if (pointsAmount > 0) {
                                val vector = handcuffedPlayer.location.toVector().subtract(
                                    cufferPlayer.location.toVector()).normalize().multiply(distanceBetween)
                                val nextPointLocation = cufferPlayer.location.clone().add(0.0,1.0,0.0)

                                for (index in 0..<pointsAmount) {
                                    nextPointLocation.add(vector)

                                    particleBuilder.location(nextPointLocation)
                                        .receivers(25)
                                        .spawn()
                                }
                            }
                        }
                    }
                }
                else {
                    if (advanceBreakProgress(handcuffedPlayerData,taskCheckPeriod.toInt()))
                        handcuffedPlayersToRemove.add(handcuffedPlayerData.uuid)
                }
            }
            handcuffedPlayers.keys.removeAll(handcuffedPlayersToRemove)
        },taskCheckPeriod,taskCheckPeriod)

        escortTask?.cancel()
        escortTask = Bukkit.getScheduler().runTaskTimer(VineriumEnforcement.inst(),Runnable {
            for (handcuffedPlayerData in handcuffedPlayers.values) {
                val handcuffedPlayer = Bukkit.getPlayer(handcuffedPlayerData.uuid) ?: continue
                if (!handcuffedPlayerData.isPulled) continue
                val cufferPlayer = Bukkit.getPlayer(handcuffedPlayerData.cufferPlayerUuid)
                if (cufferPlayer != null) {
                    val distance = if (handcuffedPlayer.world == cufferPlayer.world) handcuffedPlayer.location.distance(cufferPlayer.location)
                    else Double.MAX_VALUE

                    if (distance <= breakDistance && handcuffedPlayer.world == cufferPlayer.world && distance > minPullDistance) {
                        VinEnforcementUtils.pullEntity(cufferPlayer,handcuffedPlayer,5.0)
                        val distanceBetween = 0.25
                        val pointsAmount = (ceil(distance / distanceBetween) - 1).toInt()

                        if (pointsAmount > 0) {
                            val vector = handcuffedPlayer.location.toVector().subtract(
                                cufferPlayer.location.toVector()).normalize().multiply(distanceBetween)
                            val nextPointLocation = cufferPlayer.location.clone().add(0.0,1.0,0.0)

                            for (index in 0..<pointsAmount) {
                                nextPointLocation.add(vector)
                                particleBuilder.location(nextPointLocation)
                                    .receivers(25)
                                    .spawn()
                            }
                        }
                    }
                }
            }
        },escortTaskPeriod,escortTaskPeriod)

        textDisplayTask?.cancel()
        textDisplayTask = Bukkit.getScheduler().runTaskTimer(VineriumEnforcement.inst(),Runnable {
            for (handcuffedPlayerData in handcuffedPlayers.values) {
                val handcuffedPlayer = Bukkit.getPlayer(handcuffedPlayerData.uuid) ?: continue
                if (handcuffedPlayerData.textDisplay.isValid) {
                    val textDisplayLoc = handcuffedPlayer.location.clone()
                    textDisplayLoc.add(0.0,textDisplayHeightOffset,0.0)
                    handcuffedPlayerData.textDisplay.teleport(textDisplayLoc)
                }
                else {
                    val textDisplay = createTextDisplay(handcuffedPlayer)
                    handcuffedPlayerData.textDisplay = textDisplay
                }
            }
        },textDisplayTaskPeriod,textDisplayTaskPeriod)
    }

    fun clearTasks() {
        for (task in handcuffProgressTasks.values) {
            task.cancel()
        }
    }

    fun createTextDisplay(entity : Entity) : TextDisplay {
        val textDisplay = entity.world.spawn(entity.location, TextDisplay::class.java, function@ { textDisplay ->
            textDisplay.text(LangManager.INSTANCE.parseLangString(VineriumEnforcement.inst(),"handcuffs_title"))
            textDisplay.isDefaultBackground = true
            textDisplay.isSeeThrough = false
            textDisplay.billboard = Display.Billboard.CENTER
            textDisplay.isPersistent = false

            val textDisplayHeightOffset = VineriumEnforcement.inst().config.getDouble("text_display.height_offset",2.5)
            val textDisplayLoc = entity.location.clone()
            textDisplayLoc.add(0.0,textDisplayHeightOffset,0.0)
            textDisplay.teleport(textDisplayLoc)
        })
        return textDisplay
    }

    fun advanceBreakProgress(cuffedPlayer: CuffedPlayer, amount : Int) : Boolean {
        val player = Bukkit.getPlayer(cuffedPlayer.uuid) ?: return false
        val breakTime = VineriumEnforcement.inst().config.getLong("handcuffs.break_time",1200L)
        var currentBreakProgress = cuffedPlayer.breakProgress
        currentBreakProgress += amount
        cuffedPlayer.breakProgress = currentBreakProgress
        cuffedPlayer.lastBreakAttempt = VinUtils.getCurrentTick()

        val progressFormat = VineriumEnforcement.inst().config.getString("handcuffs.break_progress_format","[||||||||||||||||||||]")!!
        val completedProgressColor = VineriumEnforcement.inst().config.getString("handcuffs.break_completed_progress_color","<red>")!!
        val remainingProgressColor = VineriumEnforcement.inst().config.getString("handcuffs.break_remaining_progress_color","<gray>")!!

        val progressFormatLength = progressFormat.length
        val progressCoef = currentBreakProgress.toDouble() / breakTime.toDouble()
        val stringIndex = (progressFormatLength * progressCoef).toInt()

        val changedString = StringBuilder(progressFormat)
        changedString.insert(stringIndex.coerceAtMost(changedString.length - 1),remainingProgressColor)
        changedString.insert(0, completedProgressColor)

        player.world.playSound(player.location,Sound.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 1f,1f)
        player.sendActionBar(MiniMessage.miniMessage().deserialize(changedString.toString()))

        if (currentBreakProgress >= breakTime) {
            cuffedPlayer.textDisplay.remove()
            player.sendMessage { LangManager.INSTANCE.parseLangString(VineriumEnforcement.inst(),
                "handcuffs_break_message") }
            return true
        }
        return false
    }
}