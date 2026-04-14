package endgame.wavearena;

/**
 * A single particle emission point relative to the arena center.
 * Arenas can layer multiple points (dome, cardinals, pillars) by adding
 * a list of these via {@link WaveArenaConfig.Builder#extraZoneParticles}.
 */
public record ZoneParticleLayer(
        String systemId,
        float scale,
        double offsetX,
        double offsetY,
        double offsetZ,
        long intervalMs
) {
    public static ZoneParticleLayer at(String systemId, float scale,
                                       double offsetX, double offsetY, double offsetZ) {
        return new ZoneParticleLayer(systemId, scale, offsetX, offsetY, offsetZ, 1500L);
    }
}
