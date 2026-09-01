package org.saintqd.vineriumenforcement.utils

import org.bukkit.entity.Entity
import kotlin.math.abs

class VinEnforcementUtils {

    companion object {

        fun pullEntity(caster : Entity, pulled : Entity, velocity : Double) {
            val velocity: Double = velocity / 10.0
            val casterLoc = caster.location

            if (casterLoc.world != pulled.world)
                return
            val distance = casterLoc.distance(pulled.location)
            val modXZ = distance * 0.5 * velocity
            var modY = distance * 0.34 * velocity
            modY = if (casterLoc.y - pulled.location.y != 0.0) modY *
                    abs(casterLoc.y - pulled.location.y) * 0.5
            else modY
            var v = pulled.location.toVector().subtract(casterLoc.toVector()).normalize().multiply(velocity)

            v.setX(v.getX() * -1.0 * modXZ)
            v.setZ(v.getZ() * -1.0 * modXZ)
            v.setY(v.getY() * -1.0 * modY)

            if (v.length() > 4.0)
                v = v.normalize().multiply(4)

            if (java.lang.Double.isNaN(v.getX()))
                v.setX(0)
            if (java.lang.Double.isNaN(v.getY()))
                v.setY(0)
            if (java.lang.Double.isNaN(v.getZ()))
                v.setZ(0)

            pulled.velocity = v
        }
    }
}