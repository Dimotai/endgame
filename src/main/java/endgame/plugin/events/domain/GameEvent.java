package endgame.plugin.events.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Domain events for EndgameQoL game systems.
 * Decouples producers (death systems, combo tracker) from consumers (bounty, achievement, sound).
 * All events are immutable records — safe to pass between systems.
 */
public sealed interface GameEvent {

    /** A boss was killed. Fired from boss death systems. */
    record BossKillEvent(
            Set<UUID> creditedPlayers,
            UUID killerUuid,
            String bossTypeId,
            String displayName,
            long encounterDurationSeconds
    ) implements GameEvent {}

    /** A non-boss NPC was killed by a player. */
    record NPCKillEvent(
            UUID killerUuid,
            String npcTypeId
    ) implements GameEvent {}

    /** A player's combo tier changed. */
    record ComboTierChangeEvent(
            UUID playerUuid,
            int oldTier,
            int newTier,
            int comboCount
    ) implements GameEvent {}

    /** A dungeon instance was entered by a player. */
    record DungeonEnterEvent(
            UUID playerUuid,
            String dungeonType
    ) implements GameEvent {}

    /** An achievement was unlocked. */
    record AchievementUnlockEvent(
            UUID playerUuid,
            String achievementId,
            String achievementName
    ) implements GameEvent {}
}
