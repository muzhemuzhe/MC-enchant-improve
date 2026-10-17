package dev.differentenchant.enchanting;

import java.security.SecureRandom;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Server-only salt, persisted once per world, shared across machines and dimensions. */
public final class EnchantingSeedData extends SavedData {
    private final long salt;
    public static final SavedDataType<EnchantingSeedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("differentenchant", "enchanting_seed"),
            () -> { var data = new EnchantingSeedData(new SecureRandom().nextLong()); data.setDirty(); return data; },
            Codec.LONG.fieldOf("salt").xmap(EnchantingSeedData::new, data -> data.salt).codec(), null);
    private EnchantingSeedData(long salt) { this.salt = salt; }
    public static long forPlayer(ServerPlayer player, int option) {
        // A brand-new player can still have vanilla's unsaved zero sentinel. Vanilla replaces
        // zero on loading, so initialize it before quoting to keep first-save previews stable.
        while (player.getEnchantmentSeed()==0) player.onEnchantmentPerformed(net.minecraft.world.item.ItemStack.EMPTY,0);
        MinecraftServer server = player.level().getServer();
        long salt = server.overworld().getDataStorage().computeIfAbsent(TYPE).salt;
        long seed = salt ^ player.getUUID().getMostSignificantBits() ^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 23)
                ^ Integer.toUnsignedLong(player.getEnchantmentSeed()) ^ (0x9E3779B97F4A7C15L * (option + 1));
        seed = (seed ^ (seed >>> 30)) * 0xBF58476D1CE4E5B9L;
        seed = (seed ^ (seed >>> 27)) * 0x94D049BB133111EBL;
        return seed ^ (seed >>> 31);
    }
}
