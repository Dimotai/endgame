package endgame.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.GauntletLeaderboard;
import endgame.plugin.managers.GauntletManager;

import javax.annotation.Nonnull;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Native .ui gauntlet leaderboard page replacing HyUI GauntletUI.
 * Read-only — no interactions.
 */
public class NativeGauntletPage extends InteractiveCustomUIPage<NativeGauntletPage.GauntletEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.NativeGauntlet");
    private static final String PAGE_FILE = "Pages/EndgameGauntletPage.ui";

    private final EndgameQoL plugin;

    public NativeGauntletPage(@Nonnull PlayerRef playerRef, @Nonnull EndgameQoL plugin) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, GauntletEventData.CODEC);
        this.plugin = plugin;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append(PAGE_FILE);

        GauntletManager manager = plugin.getGauntletManager();
        List<GauntletLeaderboard.LeaderboardEntry> entries =
                manager != null ? manager.getLeaderboardTop(10) : List.of();

        if (entries.isEmpty()) {
            cmd.set("#EmptyMsg.Visible", true);
            return;
        }

        cmd.set("#EmptyMsg.Visible", false);
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd");

        for (int i = 0; i < 10; i++) {
            String prefix = "#LB" + i;
            if (i < entries.size()) {
                GauntletLeaderboard.LeaderboardEntry entry = entries.get(i);
                cmd.set(prefix + ".Visible", true);
                cmd.set(prefix + "Name.Text", entry.getPlayerName());
                cmd.set(prefix + "Wave.Text", "Wave " + entry.getWave());
                cmd.set(prefix + "Date.Text", entry.getTimestamp() > 0
                        ? dateFormat.format(new Date(entry.getTimestamp())) : "");
            } else {
                cmd.set(prefix + ".Visible", false);
            }
        }

        int totalEntries = manager != null ? manager.getLeaderboardEntryCount() : 0;
        if (totalEntries > 10) {
            cmd.set("#TotalCount.Visible", true);
            cmd.set("#TotalCount.Text", "Showing top 10 of " + totalEntries + " entries");
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull GauntletEventData data) {
        // Read-only page
    }

    public static void open(EndgameQoL plugin, PlayerRef playerRef, Store<EntityStore> store) {
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return;
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;
            player.getPageManager().openCustomPage(ref, store, new NativeGauntletPage(playerRef, plugin));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[NativeGauntlet] Failed to open");
        }
    }

    public static class GauntletEventData {
        public static final BuilderCodec<GauntletEventData> CODEC = BuilderCodec
                .builder(GauntletEventData.class, GauntletEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING, true),
                        (d, v) -> d.action = v, d -> d.action).add()
                .build();
        String action;
    }
}
