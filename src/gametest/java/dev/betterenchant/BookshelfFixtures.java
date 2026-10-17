package dev.betterenchant;

import java.util.ArrayList;
import java.util.List;
import dev.betterenchant.enchanting.BookshelfScan;
import dev.betterenchant.structure.StructurePattern.Offset;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Ordinary open-front library: two layers, adjacent side/back shelves plus a second back row. */
final class BookshelfFixtures {
    static final List<Offset> PLAYER_LAYOUT;
    static {
        var positions=new ArrayList<Offset>();
        for(int y=-1;y<=0;y++) {
            for(int z=-1;z<=1;z++) {positions.add(new Offset(-2,y,z));positions.add(new Offset(2,y,z));}
            for(int x=-2;x<=2;x++) positions.add(new Offset(x,y,2));
            for(int x=-3;x<=3;x++) positions.add(new Offset(x,y,3));
        }
        PLAYER_LAYOUT=List.copyOf(positions);
    }
    static BlockPos position(BlockPos origin,Offset offset) {return origin.offset(offset.x(),offset.y(),offset.z());}
    static void playerLayout(ServerLevel level,BlockPos origin,int count) {
        for(int i=0;i<PLAYER_LAYOUT.size();i++) level.setBlockAndUpdate(position(origin,PLAYER_LAYOUT.get(i)),i<count?Blocks.BOOKSHELF.defaultBlockState():Blocks.AIR.defaultBlockState());
    }
    static void scanLayout(ServerLevel level,BlockPos origin,int count) {
        for(int i=0;i<BookshelfScan.OFFSETS.size();i++) level.setBlockAndUpdate(position(origin,BookshelfScan.OFFSETS.get(i)),i<count?Blocks.BOOKSHELF.defaultBlockState():Blocks.AIR.defaultBlockState());
    }
    private BookshelfFixtures() {}
}
