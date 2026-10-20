package dev.differentenchant.realm;

import java.util.Set;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** Explicit admin-only travel. No background scans, terrain repair, tickets or forced chunk sets. */
public final class RealmTravel {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->dispatcher.register(
                Commands.literal("differentenchant").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("dimension")
                    .then(Commands.literal("enter").executes(c->enter(c.getSource())))
                    .then(Commands.literal("return").executes(c->leave(c.getSource()))))));
    }
    public static boolean safe(ServerLevel level,BlockPos feet) {
        if(!level.hasChunkAt(feet) || !level.getWorldBorder().isWithinBounds(feet) || feet.getY()<=level.getMinY() || feet.getY()+2>=level.getMaxY()) return false;
        var floor=level.getBlockState(feet.below());
        if(!floor.isFaceSturdy(level,feet.below(),net.minecraft.core.Direction.UP) || floor.is(BlockTags.LEAVES)
                || floor.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) || floor.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                || floor.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                || floor.is(net.minecraft.world.level.block.Blocks.CACTUS) || !floor.getFluidState().isEmpty()) return false;
        for(var at:new BlockPos[]{feet,feet.above()}) {
            var state=level.getBlockState(at);
            if(state.is(BlockTags.FIRE) || state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                    || state.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE) || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) return false;
        }
        return level.getBlockState(feet).getFluidState().isEmpty() && level.getBlockState(feet.above()).getFluidState().isEmpty()
                && level.noCollision(new AABB(feet.getX()+.2,feet.getY(),feet.getZ()+.2,feet.getX()+.8,feet.getY()+1.9,feet.getZ()+.8));
    }
    /** At most one explicitly requested FULL chunk, then only its 16x16 columns. */
    private static BlockPos landing(ServerLevel level,int x,int z) {
        level.getChunk(x>>4,z>>4);
        for(int radius=0;radius<=15;radius++) for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius || (x+dx>>4)!=(x>>4) || (z+dz>>4)!=(z>>4)) continue;
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x+dx,z+dz);
            for(int down=0;down<20 && y-down>level.getMinY();down++) {
                var at=new BlockPos(x+dx,y-down,z+dz);
                if(!level.getBlockState(at.below()).is(BlockTags.LOGS) && safe(level,at)) return at;
            }
        }
        return null;
    }
    private static int fail(CommandSourceStack source,String text) {source.sendFailure(Component.literal(text));return 0;}
    public static int enter(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=source.getPlayerOrException();
        if(player.level().dimension().equals(RealmContent.REALM)) return fail(source,"Already in Enchanter's Realm; return anchor preserved.");
        var target=source.getServer().getLevel(RealmContent.REALM);
        if(target==null) return fail(source,"Enchanter's Realm is unavailable.");
        // Terrain-only height probes do not load chunks. At most one chosen FULL entry chunk.
        BlockPos candidate=null;
        int[][] probes={{8,8},{136,8},{8,136},{-120,8},{8,-120},{264,264},{-248,264},{264,-248},{-248,-248},
                {520,8},{8,520},{-504,8},{8,-504},{776,776},{-760,776},{776,-760},{-760,-760},
                {1032,8},{8,1032},{-1016,8},{8,-1016},{1544,1544},{-1528,1544},{1544,-1528},{-1528,-1528}};
        for(var probe:probes) {
            int y=target.getChunkSource().getGenerator().getFirstOccupiedHeight(probe[0],probe[1],Heightmap.Types.OCEAN_FLOOR_WG,target,target.getChunkSource().randomState());
            if(y>=86 && y<170) {candidate=new BlockPos(probe[0],y+1,probe[1]);break;}
        }
        if(candidate==null) return fail(source,"No dry continental entry found in the bounded terrain probes. No chunks were requested.");
        var feet=landing(target,candidate.getX(),candidate.getZ());
        if(feet==null) return fail(source,"No safe dry entry in the destination chunk. No terrain was changed.");
        var data=RealmReturnData.get(source.getServer());
        var anchor=new RealmReturnData.Anchor(player.level().dimension().identifier().toString(),player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot());
        if(!player.teleportTo(target,feet.getX()+.5,feet.getY(),feet.getZ()+.5,Set.of(),player.getYRot(),player.getXRot(),true)) return fail(source,"Teleport rejected.");
        player.resetFallDistance();player.setOnGround(true);
        data.put(player.getUUID(),anchor);
        source.sendSuccess(()->Component.literal("Entered Enchanter's Realm (development access)."),false);return 1;
    }
    public static int leave(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=source.getPlayerOrException();
        if(!player.level().dimension().equals(RealmContent.REALM)) return fail(source,"Use return while inside Enchanter's Realm.");
        var data=RealmReturnData.get(source.getServer());var anchor=data.get(player.getUUID());
        var target=anchor==null?source.getServer().overworld():source.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(anchor.dimension())));
        if(target==null) return fail(source,"Original dimension unavailable; return anchor preserved.");
        double x=anchor==null?8.5:anchor.x(), y=anchor==null?80:anchor.y(), z=anchor==null?8.5:anchor.z();
        target.getChunk(((int)Math.floor(x))>>4,((int)Math.floor(z))>>4);
        if(!safe(target,BlockPos.containing(x,y,z)) || !target.noCollision(player,new AABB(x-.3,y,z-.3,x+.3,y+1.9,z+.3))) {
            var feet=landing(target,(int)Math.floor(x),(int)Math.floor(z));
            if(feet==null) return fail(source,"Return location is obstructed; no safe nearby floor. Anchor preserved.");
            x=feet.getX()+.5;y=feet.getY();z=feet.getZ()+.5;
        }
        if(!player.teleportTo(target,x,y,z,Set.of(),anchor==null?0:anchor.yaw(),anchor==null?0:anchor.pitch(),true)) return fail(source,"Teleport rejected; anchor preserved.");
        player.resetFallDistance();player.setOnGround(true);
        data.remove(player.getUUID());source.sendSuccess(()->Component.literal("Returned from Enchanter's Realm."),false);return 1;
    }
    private RealmTravel() {}
}
