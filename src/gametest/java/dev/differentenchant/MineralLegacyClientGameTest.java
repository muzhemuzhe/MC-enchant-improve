package dev.differentenchant;

import dev.differentenchant.mineral.MineralContent;
import dev.differentenchant.realm.RealmContent;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.world.level.chunk.LevelChunk;

/** Open an isolated copy of the preserved 0.18 test save, never a player's save. */
public final class MineralLegacyClientGameTest implements FabricClientGameTest {
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    private static int minerals(LevelChunk chunk){int count=0;for(var section:chunk.getSections())if(section.maybeHas(s->s.is(MineralContent.LUMINOUS_STONE)||s.is(MineralContent.HARMONIC_SILVER)||s.is(MineralContent.RIFT_CLUSTER)))for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){var s=section.getBlockState(x,y,z);if(s.is(MineralContent.LUMINOUS_STONE)||s.is(MineralContent.HARMONIC_SILVER)||s.is(MineralContent.RIFT_CLUSTER))count++;}return count;}
    private static long stoneHash(LevelChunk chunk){long hash=1;int rocks=0;for(int y=-64;y<320;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){var p=new net.minecraft.core.BlockPos(x,y,512+z);var state=chunk.getBlockState(p);if(state.is(MineralContent.HOSTS)){rocks++;hash=31*hash+31L*p.hashCode()+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().hashCode();}}check(rocks>0,"legacy fingerprint includes real rock, not just void below a floating island");return hash;}
    @Override public void runTest(ClientGameTestContext c){
        var legacy=Path.of("../legacy-realm-018").toAbsolutePath().normalize();check(Files.isRegularFile(legacy.resolve("level.dat")),"prepared historical test save exists");TestWorldSave handle;
        try(var template=c.worldBuilder().create()){handle=template.getWorldSave();}
        var target=handle.getSaveDirectory().toAbsolutePath().normalize();var safeRoot=Path.of("saves").toAbsolutePath().normalize();var backup=target.resolveSibling(target.getFileName()+"-empty-template");
        // Validate BOTH resolved paths before moving a directory on Windows.
        check(target.startsWith(safeRoot)&&backup.startsWith(safeRoot)&&!target.equals(safeRoot)&&!Files.exists(backup),"move stays inside generated test-save root");
        try{
            Files.move(target,backup);Files.createDirectories(target);
            try(var paths=Files.walk(legacy)){for(var p:paths.toList()){var out=target.resolve(legacy.relativize(p));if(Files.isDirectory(p))Files.createDirectories(out);else if(!p.getFileName().toString().equals("session.lock"))Files.copy(p,out);}}
        }catch(Exception e){throw new RuntimeException(e);}
        var report=new StringBuilder("Loaded byte-copied 0.18.0 historical test save; original untouched.\n");
        try(var world=handle.open()){
            world.getServer().runOnServer(s->{var l=s.getLevel(RealmContent.REALM);var old=l.getChunk(0,32);check(minerals(old)==0,"old generated chunk is not retrofitted with minerals");long before=stoneHash(old);int fresh=0,candidate=-1;
                for(int i=0;i<12&&fresh==0;i++){int x=256+i*32,z=256;check(!Files.exists(legacy.resolve("dimensions/differentenchant/enchanter_realm/region/r."+(x>>5)+"."+(z>>5)+".mca")),"candidate really lies outside historical region files");fresh=minerals(l.getChunk(x,z));candidate=x;}
                check(fresh>0,"new chunks in old save naturally generate minerals");check(minerals(old)==0&&stoneHash(old)==before,"old rock data stays unchanged after new generation");
                report.append("Old chunk=(0,32), mineral count=0; new chunk=(").append(candidate).append(",256), minerals=").append(fresh).append("; old rock fingerprint=").append(before).append(" unchanged.\n");
            });
        }
        try{Files.writeString(Path.of("minerals-legacy-report.txt"),report.toString());}catch(Exception e){throw new RuntimeException(e);}
    }
}
