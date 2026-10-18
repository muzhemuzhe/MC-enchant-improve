package dev.differentenchant.research;

import java.nio.file.*;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.*;
import net.minecraft.world.level.storage.LevelResource;

/** Native loading can return null for corrupt/future data. An existing file must never be replaced by defaults. */
final class ResearchPersistence {
    static <T extends SavedData> T load(MinecraftServer server,SavedDataType<T> type,Supplier<T> create) {
        return load(server.getDataStorage(),type.id().withSuffix(".dat").resolveAgainst(server.getWorldPath(LevelResource.DATA)),type,create);
    }
    static <T extends SavedData> T load(net.minecraft.world.level.storage.SavedDataStorage storage,Path file,SavedDataType<T> type,Supplier<T> create) {
        T data=storage.get(type);
        if(data==null) {
            if(Files.exists(file))throw new IllegalStateException("Cannot read existing research data; refusing to replace "+file+". Check the preceding load error and restore/migrate the file.");
            data=create.get();storage.set(type,data);
        }
        return data;
    }
    private ResearchPersistence(){}
}
