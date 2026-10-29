package dev.differentenchant.mineral;

import dev.differentenchant.realm.RealmContent;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

/** Vanilla ore algorithm, with an explicit dimension guard even for moved biomes. */
public final class RealmOreFeature extends OreFeature {
    public RealmOreFeature(){super(OreConfiguration.CODEC);}
    @Override public boolean place(FeaturePlaceContext<OreConfiguration> c){return c.level().getLevel().dimension().equals(RealmContent.REALM)&&super.place(c);}
}
