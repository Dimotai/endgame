package endgame.plugin.systems.portal;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Blocks vanilla Portal_Device from accepting Endgame portal keys.
 * Players must use {@code Endgame_Gateway} instead.
 *
 * <p>Hand-only check: fires when the player right-clicks a vanilla Portal_Device
 * with an Endgame portal key in hand.
 */
public class VanillaPortalKeyGuardSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private static final Set<String> ENDGAME_PORTAL_KEY_IDS = Set.of(
            "Endgame_Portal_Void_Realm",
            "Endgame_Portal_Frozen_Dungeon",
            "Endgame_Portal_Swamp_Dungeon"
    );

    public VanillaPortalKeyGuardSystem() {
        super(UseBlockEvent.Pre.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> chunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull UseBlockEvent.Pre event) {
        if (!"Portal_Device".equals(event.getBlockType().getId())) return;

        ItemStack heldItem = event.getContext().getHeldItem();
        if (heldItem == null || heldItem.isEmpty()) return;

        String itemId = heldItem.getItemId();
        if (!ENDGAME_PORTAL_KEY_IDS.contains(itemId)) return;

        PlayerRef pr = chunk.getComponent(index, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw("This key requires an Endgame Gateway.").color("#ff5555"));
        }
        event.setCancelled(true);
    }
}
