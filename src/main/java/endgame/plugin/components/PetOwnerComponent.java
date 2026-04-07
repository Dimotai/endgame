package endgame.plugin.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ECS component attached to pet NPC entities.
 * Links the pet to its owner player and tracks combat stats for the API.
 */
public class PetOwnerComponent implements Component<EntityStore> {

    private static volatile ComponentType<EntityStore, PetOwnerComponent> componentType;

    public static ComponentType<EntityStore, PetOwnerComponent> getComponentType() {
        return componentType;
    }

    public static void setComponentType(ComponentType<EntityStore, PetOwnerComponent> type) {
        componentType = type;
    }

    @Nonnull
    public static final BuilderCodec<PetOwnerComponent> CODEC = BuilderCodec
            .builder(PetOwnerComponent.class, PetOwnerComponent::new)
            .append(new KeyedCodec<UUID>("OwnerUuid", Codec.UUID_BINARY),
                    (c, v) -> c.ownerUuid = v, c -> c.ownerUuid).add()
            .append(new KeyedCodec<String>("PetId", Codec.STRING),
                    (c, v) -> c.petId = v != null ? v : "", c -> c.petId).add()
            .build();

    private UUID ownerUuid;
    private String petId = "";

    // Combat stats (not persisted — runtime only, for API)
    private volatile float totalDamageDealt = 0f;
    private final AtomicInteger totalKills = new AtomicInteger(0);

    public PetOwnerComponent() {}

    public PetOwnerComponent(@Nonnull UUID ownerUuid, @Nonnull String petId) {
        this.ownerUuid = ownerUuid;
        this.petId = petId;
    }

    @Nullable
    public UUID getOwnerUuid() { return ownerUuid; }

    @Nonnull
    public String getPetId() { return petId; }

    public float getTotalDamageDealt() { return totalDamageDealt; }

    public void addDamage(float amount) { this.totalDamageDealt += amount; }

    public int getTotalKills() { return totalKills.get(); }

    public void incrementKills() { totalKills.incrementAndGet(); }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        PetOwnerComponent copy = new PetOwnerComponent(ownerUuid, petId);
        copy.totalDamageDealt = this.totalDamageDealt;
        copy.totalKills.set(this.totalKills.get());
        return copy;
    }
}
