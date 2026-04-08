package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Configuration for the Pet companion system.
 */
public class PetConfig {

    public static final BuilderCodec<PetConfig> CODEC = BuilderCodec
            .builder(PetConfig.class, PetConfig::new)
            .append(new KeyedCodec<Boolean>("Enabled", Codec.BOOLEAN),
                    (c, v) -> c.enabled = v != null ? v : true, c -> c.enabled).add()
            .append(new KeyedCodec<Float>("DragonFrostUnlockChance", Codec.FLOAT),
                    (c, v) -> c.dragonFrostChance = v != null ? clamp(v, 0f, 1f) : 0.90f,
                    c -> c.dragonFrostChance).add()
            .append(new KeyedCodec<Float>("DragonFireUnlockChance", Codec.FLOAT),
                    (c, v) -> c.dragonFireChance = v != null ? clamp(v, 0f, 1f) : 0.90f,
                    c -> c.dragonFireChance).add()
            .append(new KeyedCodec<Float>("GolemVoidUnlockChance", Codec.FLOAT),
                    (c, v) -> c.golemVoidChance = v != null ? clamp(v, 0f, 1f) : 0.90f,
                    c -> c.golemVoidChance).add()
            .append(new KeyedCodec<Float>("HederaUnlockChance", Codec.FLOAT),
                    (c, v) -> c.hederaChance = v != null ? clamp(v, 0f, 1f) : 0.90f,
                    c -> c.hederaChance).add()
            .append(new KeyedCodec<Float>("TeleportDistance", Codec.FLOAT),
                    (c, v) -> c.teleportDistance = v != null ? clamp(v, 10f, 100f) : 40f,
                    c -> c.teleportDistance).add()
            .build();

    private boolean enabled = true;
    private float dragonFrostChance = 0.90f;
    private float dragonFireChance = 0.90f;
    private float golemVoidChance = 0.90f;
    private float hederaChance = 0.90f;
    private float teleportDistance = 15f;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }
    public float getDragonFrostChance() { return dragonFrostChance; }
    public void setDragonFrostChance(float v) { this.dragonFrostChance = clamp(v, 0f, 1f); }
    public float getDragonFireChance() { return dragonFireChance; }
    public void setDragonFireChance(float v) { this.dragonFireChance = clamp(v, 0f, 1f); }
    public float getGolemVoidChance() { return golemVoidChance; }
    public void setGolemVoidChance(float v) { this.golemVoidChance = clamp(v, 0f, 1f); }
    public float getHederaChance() { return hederaChance; }
    public void setHederaChance(float v) { this.hederaChance = clamp(v, 0f, 1f); }
    public float getTeleportDistance() { return teleportDistance; }
    public void setTeleportDistance(float v) { this.teleportDistance = clamp(v, 10f, 100f); }

    public float getChanceForBoss(String bossTypeId) {
        if (bossTypeId == null) return 0f;
        String lower = bossTypeId.toLowerCase();
        if (lower.contains("dragon_frost") || lower.contains("ice_dragon")) return dragonFrostChance;
        if (lower.contains("dragon_fire") || lower.contains("fire_dragon")) return dragonFireChance;
        if (lower.contains("golem_void")) return golemVoidChance;
        if (lower.contains("hedera")) return hederaChance;
        return 0f;
    }

    private static float clamp(float val, float min, float max) { return Math.max(min, Math.min(max, val)); }
}
