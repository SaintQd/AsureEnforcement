package org.saintqd.asureenforcement.worldguard

import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.flags.StringFlag

class AsureEnforcementFlags {

    companion object {
        val ENFORCEMENT_JAIL_REGION : StringFlag = StringFlag("enforcement-jail-region")

        fun registerFlags() {
            val registry = WorldGuard.getInstance().flagRegistry
            registry.register(ENFORCEMENT_JAIL_REGION)
        }
    }
}