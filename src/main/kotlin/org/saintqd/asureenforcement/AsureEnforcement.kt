package org.saintqd.asureenforcement

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.saintqd.asureenforcement.commands.AsureEnforcementCommands
import org.saintqd.asureenforcement.listeners.PlayerListener
import org.saintqd.asureenforcement.managers.EnforcementManager
import org.saintqd.asureenforcement.worldguard.AsureEnforcementFlags
import org.saintqd.asurelib.AsureLib
import org.saintqd.asurelib.utils.AsureUtils
import org.saintqd.asurelib.utils.ResourceUtils
import java.io.File

class AsureEnforcement : JavaPlugin() {

    var worldGuardEnabled = false

    companion object {
        private var plugin : AsureEnforcement? = null

        fun inst() : AsureEnforcement {
            return plugin!!
        }
    }

    override fun onLoad() {
        plugin = this

        val worldGuard = Bukkit.getPluginManager().getPlugin("WorldGuard")
        if (worldGuard != null) {
            worldGuardEnabled = true
            AsureUtils.sendDebugMessage(0, "WorldGuard found, compatibility features enabled.")
            AsureEnforcementFlags.registerFlags()
        }
    }

    override fun onEnable() {
        ResourceUtils.fetchAllResources(this, file)

        loadData()

        AsureEnforcementCommands.setupCommands(this)

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
        val langLines = AsureLib.inst().langManager.loadLanguageFile(
            this,
            dataFolder.path + File.separator + "lang" + File.separator + selectedLang + ".yml"
        )
        AsureLib.inst().langManager.registerLangLines(langLines)

        EnforcementManager.instance.loadParams()
    }
}