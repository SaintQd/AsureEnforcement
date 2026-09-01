package org.saintqd.vineriumenforcement

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.saintqd.vineriumenforcement.commands.VinEnforcementCommands
import org.saintqd.vineriumenforcement.listeners.PlayerListener
import org.saintqd.vineriumenforcement.managers.EnforcementManager
import org.saintqd.vineriumenforcement.worldguard.VinEnforcementFlags
import org.saintqd.vineriumlib.VineriumLib
import org.saintqd.vineriumlib.utils.ResourceUtils
import org.saintqd.vineriumlib.utils.VinUtils
import java.io.File

class VineriumEnforcement : JavaPlugin() {

    var worldGuardEnabled = false

    companion object {
        private var plugin : VineriumEnforcement? = null

        fun inst() : VineriumEnforcement {
            return plugin!!
        }
    }

    override fun onLoad() {
        plugin = this

        val worldGuard = Bukkit.getPluginManager().getPlugin("WorldGuard")
        if (worldGuard != null) {
            worldGuardEnabled = true
            VinUtils.sendDebugMessage(0, "WorldGuard found, compatibility features enabled.")
            VinEnforcementFlags.registerFlags()
        }
    }

    override fun onEnable() {
        ResourceUtils.fetchAllResources(this, file)

        loadData()

        VinEnforcementCommands.setupCommands(this)

        server.pluginManager.registerEvents(PlayerListener(), this)
    }

    override fun onDisable() {
        for (handcuffedPlayerData in EnforcementManager.instance.handcuffedPlayers.values) {
            if (handcuffedPlayerData.textDisplay.isValid) {
                handcuffedPlayerData.textDisplay.remove()
            }
        }
    }

    fun loadData() {
        reloadConfig()

        val selectedLang = getConfig().getString("language")
        val langLines = VineriumLib.inst().langManager.loadLanguageFile(
            this,
            dataFolder.path + File.separator + "lang" + File.separator + selectedLang + ".yml"
        )
        VineriumLib.inst().langManager.registerLangLines(langLines)

        EnforcementManager.instance.loadParams()
    }
}