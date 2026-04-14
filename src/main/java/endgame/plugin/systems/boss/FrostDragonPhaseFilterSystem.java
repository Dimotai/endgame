package endgame.plugin.systems.boss;

import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.components.FrostDragonPhaseComponent;
import endgame.plugin.utils.I18n;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Damage filter for the Frost Dragon phase-based immunity system.
 * Runs in the FILTER damage group (before damage is applied).
 *
 * When the dragon is FLYING: melee attacks deal 0 damage.
 * When the dragon is GROUNDED: projectile attacks deal 0 damage.
 */
public class FrostDragonPhaseFilterSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final ComponentType<EntityStore, FrostDragonPhaseComponent> phaseType;

    public FrostDragonPhaseFilterSystem(
            @Nonnull ComponentType<EntityStore, FrostDragonPhaseComponent> phaseType) {
        this.phaseType = phaseType;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(NPCEntity.getComponentType());
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull Damage damage) {
        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) return;

        FrostDragonPhaseComponent phase = store.getComponent(targetRef, phaseType);
        if (phase == null) return; // not a frost dragon with phase tracking

        boolean isProjectile = isProjectileDamage(store, damage);
        boolean isMelee = damage.getSource() instanceof Damage.EntitySource && !isProjectile;

        LOGGER.atFine().log("[FrostDragon] hit → src=%s projectile=%s melee=%s flying=%s",
                damage.getSource() == null ? "null" : damage.getSource().getClass().getSimpleName(),
                isProjectile, isMelee, phase.isFlying());

        boolean immune = (phase.isFlying() && isMelee)
                || (!phase.isFlying() && isProjectile);

        if (immune) {
            damage.setAmount(0);

            if (!phase.isImmunityMessageSent()
                    && damage.getSource() instanceof Damage.EntitySource es
                    && sendImmunityHint(resolveShooter(store, es.getRef()), phase.isFlying())) {
                phase.setImmunityMessageSent(true);
            }
        }
    }

    /**
     * Multi-path projectile detection — covers the three known damage paths:
     *   1. ProjectileSource — spears, bombs, NPC projectiles (ProjectileComponent.onProjectileHitEvent)
     *   2. EntitySource with ref = arrow entity — vanilla bows/crossbows (DamageEntityInteraction)
     *   3. EntitySource with ref = player holding a ranged weapon — fallback
     */
    private boolean isProjectileDamage(Store<EntityStore> store, Damage damage) {
        // Path 1: explicit ProjectileSource
        if (damage.getSource() instanceof Damage.ProjectileSource) return true;

        if (!(damage.getSource() instanceof Damage.EntitySource es)) return false;
        Ref<EntityStore> ref = es.getRef();
        if (ref == null || !ref.isValid()) return false;

        // Path 2: ref is a projectile entity (arrow, bolt)
        Store<EntityStore> refStore = ref.getStore();
        if (refStore.getComponent(ref, ProjectileComponent.getComponentType()) != null) return true;

        // Path 3: ref is a player currently holding a ranged weapon
        return isHoldingRangedWeapon(refStore, ref);
    }

    /** Checks whether the entity (if a player) currently holds a ranged weapon in the active hotbar slot. */
    private boolean isHoldingRangedWeapon(Store<EntityStore> store, Ref<EntityStore> ref) {
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return false;
            var inv = player.getInventory();
            byte activeSlot = inv.getActiveSlot(-1);
            if (activeSlot < 0) return false;
            ItemStack active = inv.getHotbar().getItemStack((short) activeSlot);
            if (active == null || active.getItemId() == null) return false;
            return isRangedWeaponId(active.getItemId());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isRangedWeaponId(String id) {
        // Vanilla: Shortbow, Two_Handed_Bow, Crossbow, Sling, ThrowingKnife, Staff, Wand
        // Endgame: Endgame_Staff_*, Weapon_Spear_*, Endgame_Spear_*
        return id.contains("Bow") || id.contains("bow")
                || id.contains("Crossbow") || id.contains("crossbow")
                || id.contains("Spear") || id.contains("spear")
                || id.contains("Staff") || id.contains("staff")
                || id.contains("Wand") || id.contains("wand")
                || id.contains("Sling") || id.contains("sling")
                || id.contains("Arrow") || id.contains("arrow")
                || id.contains("Throwing") || id.contains("throwing");
    }

    /** Resolves the shooter (player) ref when the source ref is a projectile entity. */
    private Ref<EntityStore> resolveShooter(Store<EntityStore> store, Ref<EntityStore> sourceRef) {
        if (sourceRef == null || !sourceRef.isValid()) return null;
        // If the source IS a player, return it directly
        if (sourceRef.getStore().getComponent(sourceRef, Player.getComponentType()) != null) {
            return sourceRef;
        }
        // Otherwise (arrow entity), skip — we can't cheaply resolve the shooter from the projectile
        return null;
    }

    /** Sends the immunity hint to the attacker. Returns true if delivered. */
    private boolean sendImmunityHint(Ref<EntityStore> attackerRef, boolean dragonFlying) {
        try {
            if (attackerRef == null || !attackerRef.isValid()) return false;

            PlayerRef playerRef = attackerRef.getStore().getComponent(attackerRef, PlayerRef.getComponentType());
            if (playerRef == null) return false;

            String body = I18n.getForPlayer(playerRef,
                    dragonFlying ? "dragon_frost.immune_melee" : "dragon_frost.immune_projectile");
            playerRef.sendMessage(Message.raw("[Dragon Frost] " + body).color("#66ccff"));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
