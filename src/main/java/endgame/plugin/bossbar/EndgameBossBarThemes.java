package endgame.plugin.bossbar;

import endgame.bossbar.BossBarRegistry;
import endgame.bossbar.BossBarTheme;

/**
 * EndgameQoL-specific registrations for {@link BossBarRegistry}. Called once at
 * plugin startup (from {@code SystemRegistry}). This is the <b>only</b> place where
 * per-boss theming is declared — adding a new boss means adding one entry here.
 *
 * <p>Each theme registers under the NPC type ID so managers can render without
 * knowing anything about the theme's internals.
 */
public final class EndgameBossBarThemes {

    private EndgameBossBarThemes() {}

    public static void registerAll() {
        // ===== Bosses (multi-phase) =====

        // Void Golem — Phase 2 @ 67%, Phase 3 @ 33%
        BossBarRegistry.register(BossBarTheme.builder("Endgame_Golem_Void")
                .displayName("VOID GOLEM")
                .nameColor("#bb44ff")
                .barColor("#aa44ee")
                .phase(1, "Awakened", "#c8bfff", 1.00f)
                .phase(2, "Enraged",  "#ffb060", 0.67f)
                .phase(3, "FURY",     "#ff4466", 0.33f)
                .build());

        // Frost Dragon — Phase 2 @ 70%, Phase 3 @ 40% (matches Dragon_Frost_Hybrid rework)
        BossBarRegistry.register(BossBarTheme.builder("Endgame_Dragon_Frost")
                .displayName("FROST DRAGON")
                .nameColor("#66ccff")
                .barColor("#4a9fe0")
                .phase(1, "Sky Sentinel",  "#bfe0ff", 1.00f)
                .phase(2, "Ground Fury",   "#ffb060", 0.70f)
                .phase(3, "Hybrid Frenzy", "#ff4466", 0.40f)
                .build());

        // Hedera — Phase 2 @ 66%, Phase 3 @ 33%
        BossBarRegistry.register(BossBarTheme.builder("Endgame_Hedera")
                .displayName("HEDERA")
                .nameColor("#55cc55")
                .barColor("#3ba03b")
                .phase(1, "Nature's Wrath", "#c8ffbf", 1.00f)
                .phase(2, "Toxic Bloom",    "#ffd060", 0.66f)
                .phase(3, "Death Blossom",  "#ff4466", 0.33f)
                .build());

        // ===== Elites (single-phase) =====

        BossBarRegistry.register(BossBarTheme.builder("Endgame_Alpha_Rex")
                .displayName("ALPHA REX")
                .nameColor("#ff8844")
                .barColor("#c86633")
                .elitePhase("Elite")
                .build());

        BossBarRegistry.register(BossBarTheme.builder("Endgame_Swamp_Crocodile")
                .displayName("SWAMP CROCODILE")
                .nameColor("#6ba64a")
                .barColor("#4a7c3f")
                .elitePhase("Elite")
                .build());

        BossBarRegistry.register(BossBarTheme.builder("Endgame_Bramble_Elite")
                .displayName("BRAMBLE ELITE")
                .nameColor("#88cc44")
                .barColor("#66aa33")
                .elitePhase("Elite")
                .build());

        BossBarRegistry.register(BossBarTheme.builder("Endgame_Dragon_Fire")
                .displayName("FIRE DRAGON")
                .nameColor("#ff8844")
                .barColor("#e05e22")
                .elitePhase("Elite")
                .build());

        BossBarRegistry.register(BossBarTheme.builder("Endgame_Zombie_Aberrant")
                .displayName("ZOMBIE ABERRANT")
                .nameColor("#aa66cc")
                .barColor("#8844aa")
                .elitePhase("Elite")
                .build());
    }
}
