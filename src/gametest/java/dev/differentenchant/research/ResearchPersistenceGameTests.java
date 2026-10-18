package dev.differentenchant.research;

import java.nio.file.*;
import java.util.Arrays;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.SavedDataStorage;

/** Native loader intentionally logs a parse error here; the existing future-format file must stay untouched. */
public final class ResearchPersistenceGameTests {
    @GameTest public void futureVersionCannotBeReplacedByFreshDefaults(GameTestHelper h) throws Exception {
        var parent=Path.of("../reports/research");Files.createDirectories(parent);var folder=Files.createTempDirectory(parent,"future-format-");
        var original=ResearchWorldData.get(h.getLevel().getServer());var encoded=(CompoundTag)ResearchWorldData.CODEC.encodeStart(NbtOps.INSTANCE,original).getOrThrow();
        ((CompoundTag)encoded.get("snapshot")).putInt("version",999);
        var envelope=new CompoundTag();envelope.put("data",encoded);envelope.putInt("DataVersion",net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version());
        var file=ResearchWorldData.TYPE.id().withSuffix(".dat").resolveAgainst(folder);Files.createDirectories(file.getParent());NbtIo.writeCompressed(envelope,file);
        var before=Files.readAllBytes(file);boolean refused=false;
        try(var storage=new SavedDataStorage(folder,h.getLevel().getServer().getFixerUpper(),h.getLevel().registryAccess())) {
            try{ResearchPersistence.load(storage,file,ResearchWorldData.TYPE,ResearchWorldData::new);}catch(IllegalStateException expected){refused=true;}
        }
        h.assertTrue(refused&&Arrays.equals(before,Files.readAllBytes(file)),"native parse failure must not overwrite future research snapshot");h.succeed();
    }
}
