package endgame.bossbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable visual theme for a boss's health bar.
 *
 * <p>Build via the {@link #builder(String)} fluent API, then register with
 * {@link BossBarRegistry}. Themes are looked up by {@code npcTypeId} at render time.
 *
 * <p>A theme with a single phase renders as an <b>elite-style</b> bar (no phase
 * markers, simple subtitle). A theme with 2+ phases renders phase markers at each
 * phase transition.
 */
public final class BossBarTheme {

    private final String npcTypeId;
    private final String displayName;
    private final String nameColor;
    private final String barColor;
    private final List<BossBarPhase> phases;    // sorted by threshold desc (highest HP first)

    private BossBarTheme(Builder b) {
        this.npcTypeId   = Objects.requireNonNull(b.npcTypeId, "npcTypeId");
        this.displayName = Objects.requireNonNull(b.displayName, "displayName");
        this.nameColor   = Objects.requireNonNull(b.nameColor, "nameColor");
        this.barColor    = Objects.requireNonNull(b.barColor, "barColor");
        if (b.phases.isEmpty()) {
            throw new IllegalArgumentException("BossBarTheme must have at least one phase");
        }
        List<BossBarPhase> sorted = new ArrayList<>(b.phases);
        sorted.sort(Comparator.comparingDouble(BossBarPhase::thresholdHp).reversed());
        this.phases = Collections.unmodifiableList(sorted);
    }

    public String npcTypeId()   { return npcTypeId; }
    public String displayName() { return displayName; }
    public String nameColor()   { return nameColor; }
    public String barColor()    { return barColor; }
    public List<BossBarPhase> phases() { return phases; }

    /** Resolves a phase by number. Falls back to phase 1 if the number is out of range. */
    public BossBarPhase phase(int number) {
        for (BossBarPhase p : phases) if (p.number() == number) return p;
        return phases.get(0);
    }

    /** Returns the boundary thresholds (0.0–1.0) where phase markers should be drawn.
     *  Excludes phase 1's implicit threshold of 1.0. Returns an empty array for
     *  single-phase (elite) themes. */
    public float[] markerThresholds() {
        if (phases.size() <= 1) return new float[0];
        float[] out = new float[phases.size() - 1];
        int i = 0;
        for (BossBarPhase p : phases) {
            if (p.thresholdHp() >= 1.0f) continue;
            out[i++] = p.thresholdHp();
        }
        return out;
    }

    public static Builder builder(String npcTypeId) {
        return new Builder(npcTypeId);
    }

    public static final class Builder {
        private final String npcTypeId;
        private String displayName;
        private String nameColor = "#ffffff";
        private String barColor = "#ffffff";
        private final List<BossBarPhase> phases = new ArrayList<>();

        private Builder(String npcTypeId) { this.npcTypeId = npcTypeId; }

        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder nameColor(String v)   { this.nameColor = v; return this; }
        public Builder barColor(String v)    { this.barColor = v; return this; }

        /** Add a phase. Thresholds should decrease with phase number
         *  (phase 1 = 1.0, phase 2 = e.g. 0.67, phase 3 = e.g. 0.33). */
        public Builder phase(int number, String name, String textColor, float thresholdHp) {
            this.phases.add(new BossBarPhase(number, name, textColor, thresholdHp));
            return this;
        }

        /** Convenience for single-phase (elite) themes. */
        public Builder elitePhase(String name) {
            return phase(1, name, this.nameColor, 1.0f);
        }

        public BossBarTheme build() { return new BossBarTheme(this); }
    }
}
