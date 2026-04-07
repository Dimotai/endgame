package endgame.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.PlayerEndgameComponent;
import endgame.plugin.config.PetData;
import endgame.plugin.managers.PetManager;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Native .ui page for the Pet system. Shows 4 pet cards in a 2x2 grid.
 * Unlocked pets show bestiary portraits + Summon/Dismiss buttons.
 * Locked pets show "Kill [Boss] to unlock" text.
 */
public class NativePetPage extends InteractiveCustomUIPage<NativePetPage.PetEventData> {

    private static final String PAGE_FILE = "Pages/EndgamePetPage.ui";

    private static final String[][] PET_INFO = {
            // petId, displayName, bossName, description, iconFile
            {"Endgame_Pet_Dragon_Frost", "Dragon Frost", "Dragon Frost",
                    "A miniature frost dragon that alternates between flying and walking. Chases your enemies on command.", "Dragon_Frost@2x.png"},
            {"Endgame_Pet_Dragon_Fire", "Dragon Fire", "Dragon Fire",
                    "A small fire dragon companion. Fast on the ground and fierce in combat.", "Dragon_Fire@2x.png"},
            {"Endgame_Pet_Golem_Void", "Golem Void", "Golem Void",
                    "A tiny void golem that lumbers beside you. Slow but intimidating.", "Golem_Void@2x.png"},
            {"Endgame_Pet_Hedera", "Hedera", "Hedera",
                    "A small Hedera companion. Follows you with nature's grace.", "Hedera@2x.png"}
    };

    // Accent colors per pet (frost blue, fire orange, void purple, hedera green)
    private static final String[] ACCENT_COLORS = {"#5bceff", "#ff6600", "#9040ff", "#33cc33"};
    private static final String[] ACCENT_DIM = {"#1a2a33", "#331a00", "#1a0a33", "#0a330a"};

    private final EndgameQoL plugin;
    private final PlayerRef playerRef;
    private final UUID playerUuid;

    public NativePetPage(@Nonnull PlayerRef playerRef, @Nonnull EndgameQoL plugin, @Nonnull UUID playerUuid) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PetEventData.CODEC);
        this.plugin = plugin;
        this.playerRef = playerRef;
        this.playerUuid = playerUuid;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append(PAGE_FILE);

        PlayerEndgameComponent comp = plugin.getPlayerComponent(playerUuid);
        PetData petData = comp != null ? comp.getPetData() : new PetData();
        String activePetId = petData.getActivePetId();

        int unlocked = 0;
        for (int i = 0; i < PET_INFO.length; i++) {
            String petId = PET_INFO[i][0];
            String name = PET_INFO[i][1];
            String boss = PET_INFO[i][2];
            String desc = PET_INFO[i][3];
            String icon = PET_INFO[i][4];
            boolean isUnlocked = petData.isUnlocked(petId);
            boolean isActive = petId.equals(activePetId);

            String prefix = "#Pet" + i;

            if (isUnlocked) {
                unlocked++;
                cmd.set(prefix + "Name.Text", name);
                cmd.set(prefix + "Name.Style.TextColor", isActive ? "#4ade80" : "#ffffff");
                cmd.set(prefix + "Boss.Text", boss);
                cmd.set(prefix + "Boss.Style.TextColor", "#666666");
                cmd.set(prefix + "Status.Text", isActive ? "ACTIVE" : "UNLOCKED");
                cmd.set(prefix + "Status.Style.TextColor", isActive ? "#4ade80" : "#ffaa00");
                cmd.set(prefix + "Desc.Text", desc);
                cmd.set(prefix + "Portrait.AssetPath", "UI/Custom/Bestiary/" + icon);
                cmd.set(prefix + "Portrait.Visible", true);
                cmd.set(prefix + "PortraitBg.Background.Color", isActive ? "#1a3a1a" : "#141e2a");
                cmd.set(prefix + ".Background.Color", isActive ? "#F20d1a0d" : "#E60a1119");
                cmd.set(prefix + "Accent.Background.Color", ACCENT_COLORS[i]);

                cmd.set(prefix + "Action.Visible", true);
                if (isActive) {
                    cmd.set(prefix + "Action.Text", "Dismiss");
                    events.addEventBinding(CustomUIEventBindingType.Activating,
                            prefix + "Action", EventData.of("Action", "despawn"), false);
                } else {
                    cmd.set(prefix + "Action.Text", "Summon");
                    events.addEventBinding(CustomUIEventBindingType.Activating,
                            prefix + "Action", EventData.of("Action", "spawn:" + petId), false);
                }
            } else {
                cmd.set(prefix + "Name.Text", "???");
                cmd.set(prefix + "Name.Style.TextColor", "#555555");
                cmd.set(prefix + "Boss.Text", "Defeat " + boss + " to unlock");
                cmd.set(prefix + "Boss.Style.TextColor", "#444444");
                cmd.set(prefix + "Status.Text", "LOCKED");
                cmd.set(prefix + "Status.Style.TextColor", "#ff5555");
                cmd.set(prefix + "Desc.Text", "");
                cmd.set(prefix + "Portrait.Visible", false);
                cmd.set(prefix + "PortraitBg.Background.Color", "#0a0a0a");
                cmd.set(prefix + "Action.Visible", false);
                cmd.set(prefix + ".Background.Color", "#E6060d14");
                cmd.set(prefix + "Accent.Background.Color", ACCENT_DIM[i]);
            }
        }

        cmd.set("#UnlockCount.Text", unlocked + "/4 Unlocked");
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PetEventData data) {
        if (data.action == null) {
            plugin.getLogger().atFine().log("[PetPage] handleDataEvent: action is null");
            return;
        }

        plugin.getLogger().atInfo().log("[PetPage] handleDataEvent: action=%s", data.action);

        PetManager petManager = plugin.getSystemRegistry() != null
                ? plugin.getSystemRegistry().getPetManager() : null;
        if (petManager == null) {
            plugin.getLogger().atWarning().log("[PetPage] PetManager is null");
            return;
        }

        World world = store.getExternalData().getWorld();

        if (data.action.equals("despawn")) {
            if (world != null) {
                world.execute(() -> petManager.despawnPet(store, playerUuid));
            }
        } else if (data.action.startsWith("spawn:")) {
            String petId = data.action.substring(6);
            plugin.getLogger().atInfo().log("[PetPage] Spawn requested: petId=%s, playerUuid=%s", petId, playerUuid);
            PlayerEndgameComponent comp = plugin.getPlayerComponent(playerUuid);
            if (comp != null && comp.getPetData().isUnlocked(petId)) {
                TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                if (tc != null && world != null) {
                    Vector3d pos = new Vector3d(
                            tc.getPosition().x + 2, tc.getPosition().y + 1, tc.getPosition().z + 2);
                    world.execute(() -> petManager.spawnPet(store, playerUuid, petId, pos));
                } else {
                    plugin.getLogger().atWarning().log("[PetPage] TransformComponent or world is null");
                }
            } else {
                plugin.getLogger().atWarning().log("[PetPage] Pet not unlocked or comp is null: %s", petId);
            }
        }

        // Close page after action — player reopens /eg pet to see updated state
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().setPage(ref, store, com.hypixel.hytale.protocol.packets.interface_.Page.None);
        }
    }

    public static void open(@Nonnull EndgameQoL plugin, @Nonnull PlayerRef playerRef,
                             @Nonnull Store<EntityStore> store, @Nonnull UUID uuid) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        player.getPageManager().openCustomPage(ref, store, new NativePetPage(playerRef, plugin, uuid));
    }

    public static class PetEventData {
        public static final BuilderCodec<PetEventData> CODEC = BuilderCodec
                .builder(PetEventData.class, PetEventData::new)
                .append(new KeyedCodec<String>("Action", Codec.STRING),
                        (d, v) -> d.action = v, d -> d.action).add()
                .build();
        String action;
    }
}
