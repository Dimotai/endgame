package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player pet unlock and active pet state.
 * Persisted inside PlayerEndgameComponent via BuilderCodec.
 */
public class PetData {

    @Nonnull
    public static final BuilderCodec<PetData> CODEC = BuilderCodec
            .builder(PetData.class, PetData::new)
            .append(new KeyedCodec<String[]>("UnlockedPets", Codec.STRING_ARRAY),
                    (d, v) -> { if (v != null) for (String s : v) d.unlockedPets.add(s); },
                    d -> d.unlockedPets.toArray(new String[0])).add()
            .append(new KeyedCodec<String>("ActivePetId", Codec.STRING),
                    (d, v) -> d.activePetId = v != null ? v : "",
                    d -> d.activePetId).add()
            .build();

    private final Set<String> unlockedPets = ConcurrentHashMap.newKeySet();
    private volatile String activePetId = "";

    public PetData() {}

    public PetData(PetData other) {
        this.unlockedPets.addAll(other.unlockedPets);
        this.activePetId = other.activePetId;
    }

    public boolean isUnlocked(String petId) {
        return unlockedPets.contains(petId);
    }

    public boolean unlock(String petId) {
        return unlockedPets.add(petId);
    }

    public Set<String> getUnlockedPets() {
        return unlockedPets;
    }

    public int getUnlockedCount() {
        return unlockedPets.size();
    }

    @Nonnull
    public String getActivePetId() {
        return activePetId;
    }

    public void setActivePetId(@Nonnull String petId) {
        this.activePetId = petId != null ? petId : "";
    }
}
