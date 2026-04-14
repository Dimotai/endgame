package endgame.plugin.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/**
 * Tracks the Frost Dragon's current phase mode for the damage immunity filter.
 * Updated by FrostDragonSkyBoltSystem when HP-based phase transitions occur.
 *
 * - flying = true → immune to melee (player must use ranged/projectile weapons)
 * - flying = false → immune to projectiles (player must use melee weapons)
 */
public class FrostDragonPhaseComponent implements Component<EntityStore> {

    private volatile boolean flying;
    private volatile boolean immunityMessageSent;

    public FrostDragonPhaseComponent() {
        this.flying = true; // dragons start airborne
    }

    public FrostDragonPhaseComponent(boolean flying) {
        this.flying = flying;
    }

    public boolean isFlying() { return flying; }
    public void setFlying(boolean f) { this.flying = f; this.immunityMessageSent = false; }

    public boolean isImmunityMessageSent() { return immunityMessageSent; }
    public void setImmunityMessageSent(boolean v) { this.immunityMessageSent = v; }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        FrostDragonPhaseComponent c = new FrostDragonPhaseComponent(this.flying);
        c.immunityMessageSent = this.immunityMessageSent;
        return c;
    }
}
