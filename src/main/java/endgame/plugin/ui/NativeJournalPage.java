package endgame.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.AchievementData.PlayerAchievementState;
import endgame.plugin.config.BestiaryData.NPCEntry;
import endgame.plugin.config.BestiaryData.PlayerBestiaryState;
import endgame.plugin.config.BountyData.ActiveBounty;
import endgame.plugin.config.BountyData.PlayerBountyState;
import endgame.plugin.managers.AchievementManager;
import endgame.plugin.managers.AchievementTemplate;
import endgame.plugin.managers.BountyManager;
import endgame.plugin.managers.BountyTemplate;
import endgame.plugin.utils.BestiaryRegistry;
import endgame.plugin.utils.BestiaryRegistry.Category;
import endgame.plugin.utils.BestiaryRegistry.KillMilestone;
import endgame.plugin.utils.BestiaryRegistry.MobInfo;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Native .ui journal page combining Bounty Board, Bestiary, and Achievements
 * into a single InteractiveCustomUIPage with 3 tabs.
 * Replaces HyUI-based BountyUI and BestiaryUI.
 */
public class NativeJournalPage extends InteractiveCustomUIPage<NativeJournalPage.JournalEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.NativeJournal");
    private static final String PAGE_FILE = "Pages/EndgameJournalPage.ui";

    private static final String[] TAB_IDS = {"Bounty", "Bestiary", "Achievements"};
    private static final String[] TAB_BTN_IDS = {"#TabBounty", "#TabBestiary", "#TabAchievements"};

    // Difficulty styling (EASY=0, MEDIUM=1, HARD=2)
    private static final String[] DIFF_BADGE_COLORS = {"#2d5a2d", "#5a5a2d", "#5a2d2d"};
    private static final String[] DIFF_TEXT_COLORS = {"#55ff55", "#ffff55", "#ff5555"};
    private static final String[] DIFF_NAMES = {"EASY", "MEDIUM", "HARD"};
    private static final String[] DIFF_CARD_BG = {"#141f14", "#1f1f14", "#1f1414"};
    private static final String[] DIFF_BAR_COLORS = {"#55ff55", "#ffff55", "#ff5555"};

    // Filter button IDs
    private static final String[] FILTER_IDS = {"All", "Boss", "Elite", "Elemental", "Creature", "Endgame", "Special"};
    private static final String[] FILTER_BTN_IDS = {
        "#FilterAll", "#FilterBoss", "#FilterElite", "#FilterElemental",
        "#FilterCreature", "#FilterEndgame", "#FilterSpecial"
    };

    // Achievement category colors
    private static final String[] ACH_CATEGORY_COLORS = {
        "#ff5555", "#ff8800", "#FFD700", "#55ff55", "#55aaff", "#bb88ff", "#ff66cc", "#cc8844"
    };

    private static final Map<String, String> RANK_COLORS = Map.of(
        "Novice", "#888888",
        "Veteran", "#55ff55",
        "Elite", "#ffff55",
        "Legend", "#ff8800"
    );

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private static final int MOBS_PER_PAGE = 10;
    private static final int ACHS_PER_PAGE = 10;
    private static final int MAX_MILESTONES_PER_MOB = 4;

    private final EndgameQoL plugin;
    private final PlayerRef playerRef;
    private final UUID playerUuid;
    private String activeTab;
    private String bestiaryFilter = "All";
    private int bestiaryPage = 0;
    private int achievementPage = 0;

    // Cached filtered mob list for pagination stability
    private List<MobInfo> filteredMobs = new ArrayList<>();

    public NativeJournalPage(@Nonnull PlayerRef playerRef, @Nonnull EndgameQoL plugin,
                             @Nonnull UUID playerUuid, @Nonnull String initialTab) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, JournalEventData.CODEC);
        this.plugin = plugin;
        this.playerRef = playerRef;
        this.playerUuid = playerUuid;
        this.activeTab = initialTab;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append(PAGE_FILE);

        // Bind all tab buttons
        for (int i = 0; i < TAB_IDS.length; i++) {
            events.addEventBinding(CustomUIEventBindingType.Activating, TAB_BTN_IDS[i],
                    EventData.of("Action", "tab:" + TAB_IDS[i]), false);
        }

        populateAllTabs(cmd, events);
    }

    // ==================== TAB MANAGEMENT ====================

    private void populateAllTabs(UICommandBuilder cmd, UIEventBuilder events) {
        // Tab visibility + button styles
        for (int i = 0; i < TAB_IDS.length; i++) {
            boolean active = TAB_IDS[i].equals(activeTab);
            cmd.set("#Content" + TAB_IDS[i] + ".Visible", active);
            cmd.set(TAB_BTN_IDS[i] + ".Style.Default.Background", active ? "#1a2633" : "#0a1119");
            cmd.set(TAB_BTN_IDS[i] + ".Style.Default.LabelStyle.TextColor", active ? "#ffffff" : "#96a9be");
            cmd.set(TAB_BTN_IDS[i] + ".Style.Default.LabelStyle.RenderBold", active);
        }

        populateBounty(cmd, events);
        populateBestiary(cmd, events);
        populateAchievements(cmd, events);
    }

    // ==================== BOUNTY TAB ====================

    private void populateBounty(UICommandBuilder cmd, UIEventBuilder events) {
        BountyManager manager = plugin.getBountyManager();
        if (manager == null) return;

        PlayerBountyState state = manager.getPlayerBounties(playerUuid);
        if (state == null) return;

        List<ActiveBounty> bounties = state.getBounties();

        // Header: rank + reputation bar + refresh timer
        String rank = state.getReputationRank();
        String rankColor = RANK_COLORS.getOrDefault(rank, "#888888");
        int reputation = state.getReputation();
        int nextThreshold = state.getNextRankThreshold();
        int repPct = nextThreshold > 0 ? Math.round(100f * reputation / nextThreshold) : 100;

        cmd.set("#BountyRank.Text", rank.toUpperCase());
        cmd.set("#BountyRank.Style.TextColor", rankColor);
        cmd.set("#BountyRepText.Text", reputation + " / " + nextThreshold + " (" + repPct + "%)");

        long remainingMs = manager.getTimeUntilRefresh(playerUuid);
        long hours = remainingMs / 3600000;
        long minutes = (remainingMs % 3600000) / 60000;
        cmd.set("#BountyRefresh.Text", String.format("Refresh: %dh %dm", hours, minutes));

        // Bounty cards
        for (int i = 0; i < 3; i++) {
            String prefix = "#Bounty" + i;
            if (i < bounties.size()) {
                ActiveBounty bounty = bounties.get(i);
                BountyTemplate template = BountyTemplate.getById(bounty.getTemplateId());
                String desc = template != null ? template.getDescription() : "Unknown bounty";
                int diffIdx = Math.min(i, 2);

                cmd.set(prefix + ".Visible", true);
                cmd.set(prefix + ".Background", DIFF_CARD_BG[diffIdx]);
                cmd.set(prefix + "Diff.Text", DIFF_NAMES[diffIdx]);
                cmd.set(prefix + "Diff.Style.TextColor", DIFF_TEXT_COLORS[diffIdx]);
                cmd.set(prefix + "Diff.Background", DIFF_BADGE_COLORS[diffIdx]);
                cmd.set(prefix + "Desc.Text", desc);

                // Progress bar
                float progress = bounty.getTarget() > 0 ?
                        Math.min(1f, (float) bounty.getProgress() / bounty.getTarget()) : 0f;

                if (bounty.isClaimed()) {
                } else if (bounty.isCompleted()) {
                } else {
                }

                int pct = bounty.getTarget() > 0 ? Math.round(100f * bounty.getProgress() / bounty.getTarget()) : 0;
                cmd.set(prefix + "Prog.Text", bounty.getProgress() + " / " + bounty.getTarget() + " (" + Math.min(pct, 100) + "%)");

                // Reward text
                if (template != null) {
                    StringBuilder rewardSb = new StringBuilder("Reward: ");
                    if (template.getXpReward() > 0) rewardSb.append(template.getXpReward()).append(" XP");
                    if (template.getReputationReward() > 0) {
                        if (template.getXpReward() > 0) rewardSb.append(" + ");
                        rewardSb.append(template.getReputationReward()).append(" Rep");
                    }
                    rewardSb.append(" + Items");
                    cmd.set(prefix + "Reward.Text", rewardSb.toString());
                }

                // Bonus text
                String bonusTypeId = bounty.getBonusType();
                if (bonusTypeId != null && !bonusTypeId.isEmpty()) {
                    BountyTemplate.BonusType bonusType = BountyTemplate.BonusType.fromId(bonusTypeId);
                    if (bonusType != BountyTemplate.BonusType.NONE) {
                        cmd.set(prefix + "Bonus.Visible", true);
                        String bonusColor = bounty.isBonusCompleted() ? "#55ff55" : "#666666";
                        String bonusPrefix = bounty.isBonusCompleted() ? "BONUS COMPLETE" : "BONUS";
                        cmd.set(prefix + "Bonus.Text", bonusPrefix + ": " + bonusType.getDescription() + " (+50% reward)");
                        cmd.set(prefix + "Bonus.Style.TextColor", bonusColor);
                    } else {
                        cmd.set(prefix + "Bonus.Visible", false);
                    }
                } else {
                    cmd.set(prefix + "Bonus.Visible", false);
                }

                // Status + claim
                if (bounty.isClaimed()) {
                    cmd.set(prefix + "Status.Text", "CLAIMED");
                    cmd.set(prefix + "Status.Style.TextColor", "#336633");
                    cmd.set(prefix + "Claim.Visible", false);
                } else if (bounty.isCompleted()) {
                    cmd.set(prefix + "Status.Text", "COMPLETE");
                    cmd.set(prefix + "Status.Style.TextColor", "#55ff55");
                    cmd.set(prefix + "Claim.Visible", true);
                    events.addEventBinding(CustomUIEventBindingType.Activating, prefix + "Claim",
                            EventData.of("Action", "bounty:claim:" + i), false);
                } else {
                    cmd.set(prefix + "Status.Text", "");
                    cmd.set(prefix + "Claim.Visible", false);
                }
            } else {
                cmd.set(prefix + ".Visible", false);
            }
        }

        // Streak section
        boolean streakEnabled = plugin.getConfig().get().isBountyStreakEnabled();
        cmd.set("#StreakSection.Visible", streakEnabled);
        if (streakEnabled) {
            boolean allCompleted = state.allCompleted();
            boolean streakClaimed = state.isStreakClaimed();

            String streakBg = streakClaimed ? "#142014" : allCompleted ? "#1f1f0a" : "#141418";
            cmd.set("#StreakSection.Background", streakBg);

            // Pips
            for (int i = 0; i < 3; i++) {
                String pipColor = (i < bounties.size() && bounties.get(i).isCompleted()) ? "#55ff55" : "#333333";
                cmd.set("#StreakPip" + i + ".Background", pipColor);
            }

            // Text
            if (streakClaimed) {
                cmd.set("#StreakText.Text", "Streak Bonus Claimed!");
                cmd.set("#StreakText.Style.TextColor", "#55ff55");
            } else if (allCompleted) {
                cmd.set("#StreakText.Text", "All bounties complete! Claim your streak bonus");
                cmd.set("#StreakText.Style.TextColor", "#FFD700");
            } else {
                cmd.set("#StreakText.Text", "Complete all " + bounties.size() + " bounties for a streak bonus");
                cmd.set("#StreakText.Style.TextColor", "#666666");
            }

            // Claim button
            if (allCompleted && !streakClaimed) {
                cmd.set("#StreakClaim.Visible", true);
                events.addEventBinding(CustomUIEventBindingType.Activating, "#StreakClaim",
                        EventData.of("Action", "bounty:claimStreak"), false);
            } else {
                cmd.set("#StreakClaim.Visible", false);
            }
        }

        // Weekly section
        boolean weeklyEnabled = plugin.getConfig().get().isBountyWeeklyEnabled();
        ActiveBounty weeklyBounty = state.getWeeklyBounty();
        boolean showWeekly = weeklyEnabled && weeklyBounty != null;

        cmd.set("#WeeklyDivider.Visible", showWeekly);
        cmd.set("#WeeklySpacer.Visible", showWeekly);
        cmd.set("#WeeklyBounty.Visible", showWeekly);

        if (showWeekly) {
            BountyTemplate weeklyTemplate = BountyTemplate.getById(weeklyBounty.getTemplateId());
            String weeklyDesc = weeklyTemplate != null ? weeklyTemplate.getDescription() : "Unknown weekly";

            cmd.set("#WeeklyDesc.Text", weeklyDesc);

            float wProgress = weeklyBounty.getTarget() > 0 ?
                    Math.min(1f, (float) weeklyBounty.getProgress() / weeklyBounty.getTarget()) : 0f;

            String wBarColor;
            if (weeklyBounty.isClaimed()) {
                wBarColor = "#333366";
            } else if (weeklyBounty.isCompleted()) {
                wBarColor = "#bb88ff";
            } else {
                wBarColor = "#bb88ff";
            }

            int wPct = weeklyBounty.getTarget() > 0 ? Math.round(100f * weeklyBounty.getProgress() / weeklyBounty.getTarget()) : 0;
            cmd.set("#WeeklyProg.Text", weeklyBounty.getProgress() + " / " + weeklyBounty.getTarget() + " (" + Math.min(wPct, 100) + "%)");

            if (weeklyTemplate != null) {
                StringBuilder rewardSb = new StringBuilder("Reward: ");
                if (weeklyTemplate.getXpReward() > 0) rewardSb.append(weeklyTemplate.getXpReward()).append(" XP");
                if (weeklyTemplate.getReputationReward() > 0) {
                    if (weeklyTemplate.getXpReward() > 0) rewardSb.append(" + ");
                    rewardSb.append(weeklyTemplate.getReputationReward()).append(" Rep");
                }
                rewardSb.append(" + Items");
                cmd.set("#WeeklyReward.Text", rewardSb.toString());
            }

            long weeklyRemainingMs = manager.getWeeklyTimeUntilRefresh(playerUuid);
            long weeklyDays = weeklyRemainingMs / 86400000L;
            long weeklyHours = (weeklyRemainingMs % 86400000L) / 3600000L;

            if (weeklyBounty.isClaimed()) {
                cmd.set("#WeeklyStatus.Text", "CLAIMED");
                cmd.set("#WeeklyStatus.Style.TextColor", "#333366");
                cmd.set("#WeeklyClaim.Visible", false);
            } else if (weeklyBounty.isCompleted()) {
                cmd.set("#WeeklyStatus.Text", "COMPLETE");
                cmd.set("#WeeklyStatus.Style.TextColor", "#bb88ff");
                cmd.set("#WeeklyClaim.Visible", true);
                events.addEventBinding(CustomUIEventBindingType.Activating, "#WeeklyClaim",
                        EventData.of("Action", "bounty:claimWeekly"), false);
            } else {
                cmd.set("#WeeklyStatus.Text", String.format("Resets in %dd %dh", weeklyDays, weeklyHours));
                cmd.set("#WeeklyStatus.Style.TextColor", "#666666");
                cmd.set("#WeeklyClaim.Visible", false);
            }
        }
    }

    // ==================== BESTIARY TAB ====================

    private void populateBestiary(UICommandBuilder cmd, UIEventBuilder events) {
        AchievementManager manager = plugin.getAchievementManager();
        if (manager == null) return;

        PlayerBestiaryState state = manager.getBestiaryState(playerUuid);

        // Count discovered and total kills
        int discovered = 0;
        int totalKills = 0;
        for (MobInfo mob : BestiaryRegistry.getAll()) {
            NPCEntry entry = state.getEntries().get(mob.npcTypeId());
            if (entry != null) {
                if (entry.isDiscovered()) discovered++;
                totalKills += entry.getKillCount();
            }
        }

        cmd.set("#BestiaryStats.Text", discovered + " / " + BestiaryRegistry.getTotalCount()
                + " Discovered | " + totalKills + " Total Kills");

        // Discovery milestones (6 badges: indices 0-5)
        int claimedDiscovery = state.getClaimedDiscoveryMilestone();
        for (int i = 0; i < BestiaryRegistry.DISCOVERY_MILESTONES.length; i++) {
            int mt = BestiaryRegistry.DISCOVERY_MILESTONES[i];
            int effective = mt == -1 ? BestiaryRegistry.getTotalCount() : mt;
            String label = mt == -1 ? "ALL" : String.valueOf(mt);
            boolean reached = discovered >= effective;
            boolean claimed = claimedDiscovery >= effective;

            String prefix = "#Disc" + i;
            cmd.set(prefix + ".Text", label);

            if (claimed) {
                // Green = claimed
                cmd.set(prefix + ".Style.Default.Background", "#1a3a1a");
                cmd.set(prefix + ".Style.Default.LabelStyle.TextColor", "#55ff55");
                cmd.set(prefix + ".Style.Hovered.Background", "#1a3a1a");
                cmd.set(prefix + ".Style.Hovered.LabelStyle.TextColor", "#55ff55");
            } else if (reached) {
                // Gold = claimable
                cmd.set(prefix + ".Style.Default.Background", "#1a3d2e");
                cmd.set(prefix + ".Style.Default.LabelStyle.TextColor", "#4aff7f");
                cmd.set(prefix + ".Style.Hovered.Background", "#2a5a3e");
                cmd.set(prefix + ".Style.Hovered.LabelStyle.TextColor", "#6aff9f");
                events.addEventBinding(CustomUIEventBindingType.Activating, prefix,
                        EventData.of("Action", "bestiary:claimDisc:" + i), false);
            } else {
                // Gray = locked
                cmd.set(prefix + ".Style.Default.Background", "#1a1a1a");
                cmd.set(prefix + ".Style.Default.LabelStyle.TextColor", "#444444");
                cmd.set(prefix + ".Style.Hovered.Background", "#1a1a1a");
                cmd.set(prefix + ".Style.Hovered.LabelStyle.TextColor", "#444444");
            }
        }

        // Category filter buttons
        for (int i = 0; i < FILTER_IDS.length; i++) {
            boolean active = FILTER_IDS[i].equals(bestiaryFilter);
            cmd.set(FILTER_BTN_IDS[i] + ".Style.Default.Background", active ? "#3a3a5a" : "#1e1e2e");
            cmd.set(FILTER_BTN_IDS[i] + ".Style.Default.LabelStyle.TextColor", active ? "#ffffff" : "#999999");
            events.addEventBinding(CustomUIEventBindingType.Activating, FILTER_BTN_IDS[i],
                    EventData.of("Action", "bestiary:filter:" + FILTER_IDS[i]), false);
        }

        // Build filtered mob list — discovered first
        filteredMobs.clear();
        Collection<MobInfo> source;
        if ("All".equals(bestiaryFilter)) {
            source = BestiaryRegistry.getAll();
        } else {
            try {
                Category cat = Category.valueOf(bestiaryFilter.toUpperCase());
                source = BestiaryRegistry.getByCategory(cat);
            } catch (IllegalArgumentException e) {
                source = BestiaryRegistry.getAll();
            }
        }
        // Sort: discovered mobs first, then undiscovered
        java.util.List<MobInfo> discoveredList = new java.util.ArrayList<>();
        java.util.List<MobInfo> undiscoveredList = new java.util.ArrayList<>();
        for (MobInfo mob : source) {
            NPCEntry e = state.getEntries().get(mob.npcTypeId());
            if (e != null && e.isDiscovered()) {
                discoveredList.add(mob);
            } else {
                undiscoveredList.add(mob);
            }
        }
        filteredMobs.addAll(discoveredList);
        filteredMobs.addAll(undiscoveredList);

        // Pagination
        int totalMobPages = Math.max(1, (filteredMobs.size() + MOBS_PER_PAGE - 1) / MOBS_PER_PAGE);
        if (bestiaryPage >= totalMobPages) bestiaryPage = totalMobPages - 1;
        cmd.set("#BestiaryPage.Text", (bestiaryPage + 1) + " / " + totalMobPages);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BestiaryPrev",
                EventData.of("Action", "bestiary:prev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BestiaryNext",
                EventData.of("Action", "bestiary:next"), false);

        // Populate 10 mob card slots
        int start = bestiaryPage * MOBS_PER_PAGE;
        boolean anyVisible = false;
        for (int i = 0; i < MOBS_PER_PAGE; i++) {
            String prefix = "#Mob" + i;
            int idx = start + i;
            if (idx < filteredMobs.size()) {
                anyVisible = true;
                MobInfo mob = filteredMobs.get(idx);
                NPCEntry entry = state.getEntries().get(mob.npcTypeId());
                boolean isDiscovered = entry != null && entry.isDiscovered();

                cmd.set(prefix + ".Visible", true);
                cmd.set(prefix + ".Background", isDiscovered ? "#1a1a2a" : "#0a0a15");

                // Portrait image
                String catColor = mob.category().getColor();
                if (isDiscovered && mob.iconFile() != null) {
                    String iconName = mob.iconFile().replace(".png", "@2x.png");
                    cmd.set(prefix + "Portrait.AssetPath", "UI/Custom/Bestiary/" + iconName);
                    cmd.set(prefix + "Portrait.Visible", true);
                } else {
                    cmd.set(prefix + "Portrait.Visible", false);
                }

                // Category badge
                cmd.set(prefix + "Cat.Text", mob.category().getLabel().toUpperCase());
                cmd.set(prefix + "Cat.Style.TextColor", catColor);
                cmd.set(prefix + "Cat.Background", catColor + "22");

                // Name
                if (isDiscovered) {
                    cmd.set(prefix + "Name.Text", mob.displayName());
                    cmd.set(prefix + "Name.Style.TextColor", "#dddddd");
                } else {
                    cmd.set(prefix + "Name.Text", "???");
                    cmd.set(prefix + "Name.Style.TextColor", "#555555");
                }

                // HP
                if (isDiscovered) {
                    if (mob.health() > 0) {
                        cmd.set(prefix + "HP.Text", "HP: " + mob.health());
                        cmd.set(prefix + "HP.Style.TextColor", "#ff5555");
                    } else {
                        cmd.set(prefix + "HP.Text", "NPC");
                        cmd.set(prefix + "HP.Style.TextColor", "#55ff55");
                    }
                } else {
                    cmd.set(prefix + "HP.Text", "HP: ???");
                    cmd.set(prefix + "HP.Style.TextColor", "#333333");
                }

                // Damage + Location
                if (isDiscovered) {
                    cmd.set(prefix + "Dmg.Text", mob.damageInfo());
                    cmd.set(prefix + "Dmg.Style.TextColor", "#ff8800");
                } else {
                    cmd.set(prefix + "Dmg.Text", "???");
                    cmd.set(prefix + "Dmg.Style.TextColor", "#333333");
                }
                cmd.set(prefix + "Loc.Text", mob.location());

                // Description
                if (isDiscovered) {
                    cmd.set(prefix + "DescText.Text", mob.description());
                    cmd.set(prefix + "DescText.Style.TextColor", "#888888");
                } else {
                    cmd.set(prefix + "DescText.Text", "Kill this creature to discover it.");
                    cmd.set(prefix + "DescText.Style.TextColor", "#444444");
                }

                // Drops
                if (isDiscovered && mob.notableDrops().length > 0) {
                    cmd.set(prefix + "Drops.Visible", true);
                    cmd.set(prefix + "Drops.Text", "Drops: " + String.join(", ", mob.notableDrops()));
                } else {
                    cmd.set(prefix + "Drops.Visible", false);
                }

                // Kill stats
                if (isDiscovered && entry != null && entry.getKillCount() > 0) {
                    cmd.set(prefix + "Kills.Visible", true);
                    String killText = "Kills: " + entry.getKillCount();
                    if (entry.getFirstKillTimestamp() > 0) {
                        String date = DATE_FMT.format(Instant.ofEpochMilli(entry.getFirstKillTimestamp()));
                        killText += "  |  First kill: " + date;
                    }
                    cmd.set(prefix + "Kills.Text", killText);
                } else {
                    cmd.set(prefix + "Kills.Visible", false);
                }

                // Kill milestones
                if (isDiscovered && entry != null) {
                    List<KillMilestone> milestones = BestiaryRegistry.getKillMilestones(mob.category());
                    if (!milestones.isEmpty()) {
                        cmd.set(prefix + "Miles.Visible", true);
                        int kills = entry.getKillCount();
                        int claimedMs = entry.getClaimedMilestone();
                        for (int mi = 0; mi < MAX_MILESTONES_PER_MOB; mi++) {
                            String msPrefix = prefix + "Ms" + mi;
                            if (mi < milestones.size()) {
                                KillMilestone km = milestones.get(mi);
                                boolean reached = kills >= km.threshold();
                                boolean isClaimed = claimedMs >= km.threshold();

                                cmd.set(msPrefix + ".Visible", true);
                                cmd.set(msPrefix + ".Text", String.valueOf(km.threshold()));

                                if (isClaimed) {
                                    cmd.set(msPrefix + ".Style.Default.Background", "#1a3a1a");
                                    cmd.set(msPrefix + ".Style.Default.LabelStyle.TextColor", "#55ff55");
                                    cmd.set(msPrefix + ".Style.Hovered.Background", "#1a3a1a");
                                    cmd.set(msPrefix + ".Style.Hovered.LabelStyle.TextColor", "#55ff55");
                                } else if (reached) {
                                    cmd.set(msPrefix + ".Style.Default.Background", "#1a3d2e");
                                    cmd.set(msPrefix + ".Style.Default.LabelStyle.TextColor", "#4aff7f");
                                    cmd.set(msPrefix + ".Style.Hovered.Background", "#2a5a3e");
                                    cmd.set(msPrefix + ".Style.Hovered.LabelStyle.TextColor", "#6aff9f");
                                    events.addEventBinding(CustomUIEventBindingType.Activating, msPrefix,
                                            EventData.of("Action", "bestiary:claimKill:" + mob.npcTypeId() + ":" + km.threshold()), false);
                                } else {
                                    cmd.set(msPrefix + ".Style.Default.Background", "#1a1a1a");
                                    cmd.set(msPrefix + ".Style.Default.LabelStyle.TextColor", "#444444");
                                    cmd.set(msPrefix + ".Style.Hovered.Background", "#1a1a1a");
                                    cmd.set(msPrefix + ".Style.Hovered.LabelStyle.TextColor", "#444444");
                                }
                            } else {
                                cmd.set(msPrefix + ".Visible", false);
                            }
                        }
                    } else {
                        cmd.set(prefix + "Miles.Visible", false);
                    }
                } else {
                    cmd.set(prefix + "Miles.Visible", false);
                }
            } else {
                cmd.set(prefix + ".Visible", false);
            }
        }

        cmd.set("#BestiaryEmpty.Visible", !anyVisible);
    }

    // ==================== ACHIEVEMENTS TAB ====================

    private void populateAchievements(UICommandBuilder cmd, UIEventBuilder events) {
        AchievementManager manager = plugin.getAchievementManager();
        if (manager == null) return;

        PlayerAchievementState state = manager.getAchievementState(playerUuid);
        List<AchievementTemplate> allAchievements = AchievementTemplate.getAll();

        int completed = state.getCompletedCount();
        int total = allAchievements.size();

        cmd.set("#AchStats.Text", completed + " / " + total + " completed");

        // Pagination
        int totalAchPages = Math.max(1, (allAchievements.size() + ACHS_PER_PAGE - 1) / ACHS_PER_PAGE);
        if (achievementPage >= totalAchPages) achievementPage = totalAchPages - 1;
        cmd.set("#AchPage.Text", (achievementPage + 1) + " / " + totalAchPages);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#AchPrev",
                EventData.of("Action", "ach:prev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#AchNext",
                EventData.of("Action", "ach:next"), false);

        // Populate 10 achievement card slots
        int start = achievementPage * ACHS_PER_PAGE;
        for (int i = 0; i < ACHS_PER_PAGE; i++) {
            String prefix = "#Ach" + i;
            int idx = start + i;
            if (idx < allAchievements.size()) {
                AchievementTemplate ach = allAchievements.get(idx);
                boolean isCompleted = state.isCompleted(ach.getId());
                boolean isClaimed = state.isClaimed(ach.getId());
                int progress = state.getProgress(ach.getId());
                String catColor = ACH_CATEGORY_COLORS[ach.getCategory().ordinal() % ACH_CATEGORY_COLORS.length];

                cmd.set(prefix + ".Visible", true);
                cmd.set(prefix + ".Background", isCompleted ? "#142014" : "#1a1a2a");

                // Name
                String checkmark = isCompleted ? " [DONE]" : "";
                cmd.set(prefix + "Name.Text", ach.getName() + checkmark);
                cmd.set(prefix + "Name.Style.TextColor", isCompleted ? "#55ff55" : "#dddddd");

                // Category badge
                cmd.set(prefix + "Cat.Text", ach.getCategory().name());
                cmd.set(prefix + "Cat.Style.TextColor", catColor);
                cmd.set(prefix + "Cat.Background", catColor + "22");

                // Description
                cmd.set(prefix + "DescText.Text", ach.getDescription());

                // Progress bar
                float pct = ach.getTarget() > 0 ? Math.min(1f, (float) progress / ach.getTarget()) : 0f;

                int achPct = ach.getTarget() > 0 ? Math.round(100f * progress / ach.getTarget()) : 0;
                cmd.set(prefix + "Prog.Text", Math.min(progress, ach.getTarget()) + " / " + ach.getTarget() + " (" + Math.min(achPct, 100) + "%)");

                // Reward text
                String rewardText = ach.getRewardDropTable() != null ? "Reward: Item Drop" : "Reward: XP only";
                if (ach.getXpReward() > 0) rewardText += " + " + ach.getXpReward() + " XP";
                cmd.set(prefix + "Reward.Text", rewardText);

                // Claim button
                if (isCompleted && !isClaimed) {
                    cmd.set(prefix + "Claim.Visible", true);
                    cmd.set(prefix + "Claim.Text", "Claim");
                    events.addEventBinding(CustomUIEventBindingType.Activating, prefix + "Claim",
                            EventData.of("Action", "ach:claim:" + ach.getId()), false);
                } else if (isCompleted && isClaimed) {
                    cmd.set(prefix + "Claim.Visible", true);
                    cmd.set(prefix + "Claim.Text", "Claimed");
                    cmd.set(prefix + "Claim.Style.Default.Background", "#1a3a1a");
                    cmd.set(prefix + "Claim.Style.Default.LabelStyle.TextColor", "#55ff55");
                } else {
                    cmd.set(prefix + "Claim.Visible", false);
                }
            } else {
                cmd.set(prefix + ".Visible", false);
            }
        }
    }

    // ==================== EVENT HANDLING ====================

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull JournalEventData data) {
        if (data.action == null || data.action.isEmpty()) return;
        String action = data.action;

        // Tab switch
        if (action.startsWith("tab:")) {
            activeTab = action.substring(4);
            this.rebuild();
            return;
        }

        // Bounty claims
        if (action.startsWith("bounty:claim:")) {
            String rest = action.substring(13);
            int bountyIdx = Integer.parseInt(rest);
            BountyManager manager = plugin.getBountyManager();
            if (manager != null) {
                String dropTable = manager.claimBounty(playerUuid, bountyIdx);
                if (dropTable != null) {
                    giveDropTableRewards(dropTable);
                    this.sendRefreshUpdate();
                }
            }
            return;
        }

        if ("bounty:claimStreak".equals(action)) {
            BountyManager manager = plugin.getBountyManager();
            if (manager != null && manager.claimStreak(playerUuid)) {
                giveDropTableRewards("Endgame_Drop_Bounty_Streak");
                this.sendRefreshUpdate();
            }
            return;
        }

        if ("bounty:claimWeekly".equals(action)) {
            BountyManager manager = plugin.getBountyManager();
            if (manager != null) {
                String dropTable = manager.claimBounty(playerUuid, 3);
                if (dropTable != null) {
                    giveDropTableRewards(dropTable);
                    this.sendRefreshUpdate();
                }
            }
            return;
        }

        // Bestiary filter
        if (action.startsWith("bestiary:filter:")) {
            bestiaryFilter = action.substring(16);
            bestiaryPage = 0;
            this.rebuild();
            return;
        }

        // Bestiary pagination
        if ("bestiary:prev".equals(action)) {
            if (bestiaryPage > 0) { bestiaryPage--; this.rebuild(); }
            return;
        }
        if ("bestiary:next".equals(action)) {
            int totalMobPages = Math.max(1, (filteredMobs.size() + MOBS_PER_PAGE - 1) / MOBS_PER_PAGE);
            if (bestiaryPage < totalMobPages - 1) { bestiaryPage++; this.rebuild(); }
            return;
        }

        // Discovery milestone claim
        if (action.startsWith("bestiary:claimDisc:")) {
            int discIdx = Integer.parseInt(action.substring(19));
            if (discIdx >= 0 && discIdx < BestiaryRegistry.DISCOVERY_MILESTONES.length) {
                int mt = BestiaryRegistry.DISCOVERY_MILESTONES[discIdx];
                AchievementManager manager = plugin.getAchievementManager();
                if (manager != null) {
                    String dropTable = manager.claimDiscoveryMilestone(playerUuid, mt);
                    if (dropTable != null) {
                        giveDropTableRewards(dropTable);
                        this.sendRefreshUpdate();
                    }
                }
            }
            return;
        }

        // Kill milestone claim: bestiary:claimKill:npcTypeId:threshold
        if (action.startsWith("bestiary:claimKill:")) {
            String rest = action.substring(19);
            int lastColon = rest.lastIndexOf(':');
            if (lastColon > 0) {
                String npcId = rest.substring(0, lastColon);
                int threshold = Integer.parseInt(rest.substring(lastColon + 1));
                AchievementManager manager = plugin.getAchievementManager();
                if (manager != null) {
                    String dropTable = manager.claimBestiaryMilestone(playerUuid, npcId, threshold);
                    if (dropTable != null) {
                        giveDropTableRewards(dropTable);
                        this.sendRefreshUpdate();
                    }
                }
            }
            return;
        }

        // Achievement pagination
        if ("ach:prev".equals(action)) {
            if (achievementPage > 0) { achievementPage--; this.rebuild(); }
            return;
        }
        if ("ach:next".equals(action)) {
            List<AchievementTemplate> allAchs = AchievementTemplate.getAll();
            int totalAchPages = Math.max(1, (allAchs.size() + ACHS_PER_PAGE - 1) / ACHS_PER_PAGE);
            if (achievementPage < totalAchPages - 1) { achievementPage++; this.rebuild(); }
            return;
        }

        // Achievement claim
        if (action.startsWith("ach:claim:")) {
            String achId = action.substring(10);
            AchievementManager manager = plugin.getAchievementManager();
            if (manager != null) {
                String dropTable = manager.claimAchievement(playerUuid, achId);
                if (dropTable != null) {
                    giveDropTableRewards(dropTable);
                }
                // Always refresh UI after claim (XP-only achievements return null dropTable but still mark claimed)
                this.rebuild();
            }
            return;
        }
    }

    // ==================== HELPERS ====================

    /**
     * Use sendUpdate instead of rebuild for claim operations to preserve scroll position.
     */
    private void sendRefreshUpdate() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        populateAllTabs(cmd, events);
        this.sendUpdate(cmd, events, false);
    }

    private void giveDropTableRewards(String dropTable) {
        try {
            PlayerRef playerRef = this.playerRef;
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return;

            Store<EntityStore> playerStore = ref.getStore();
            Player player = playerStore.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            List<ItemStack> rewards = ItemModule.get().getRandomItemDrops(dropTable);
            if (rewards == null || rewards.isEmpty()) return;

            for (ItemStack item : rewards) {
                ItemStackTransaction transaction = player.giveItem(item, ref, playerStore);
                ItemStack remainder = transaction.getRemainder();
                if (remainder != null && !remainder.isEmpty()) {
                    ItemUtils.dropItem(ref, remainder, playerStore);
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[NativeJournal] Error giving rewards: %s", e.getMessage());
        }
    }

    // ==================== STATIC OPEN ====================

    public static void open(EndgameQoL plugin, PlayerRef playerRef, Store<EntityStore> store,
                            UUID playerUuid, String tab) {
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return;
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;
            player.getPageManager().openCustomPage(ref, store,
                    new NativeJournalPage(playerRef, plugin, playerUuid, tab));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[NativeJournal] Failed to open");
        }
    }

    // ==================== EVENT DATA ====================

    public static class JournalEventData {
        public static final BuilderCodec<JournalEventData> CODEC = BuilderCodec
                .builder(JournalEventData.class, JournalEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING, true),
                        (d, v) -> d.action = v, d -> d.action).add()
                .build();
        String action;
    }
}
