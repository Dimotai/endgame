package endgame.plugin.config;

import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

/**
 * Crafting config — recipe enable/disable is now handled entirely by RecipeOverrideConfig.
 * This class is kept for codec compatibility (existing config files won't error on load).
 */
public class CraftingConfig {

    @Nonnull
    public static final BuilderCodec<CraftingConfig> CODEC = BuilderCodec
            .builder(CraftingConfig.class, CraftingConfig::new)
            .build();
}
