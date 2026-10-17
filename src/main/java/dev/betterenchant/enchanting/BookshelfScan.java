package dev.betterenchant.enchanting;

import java.util.ArrayList;
import java.util.List;
import dev.betterenchant.structure.StructurePattern;
import dev.betterenchant.structure.StructurePattern.Offset;

/** Whole-machine bookshelf geometry and capped counting, independent of world/chunk APIs. */
public final class BookshelfScan {
    public static final int MAX_EFFECTIVE=36, EXTENSION=2;
    public static final List<Offset> OFFSETS;
    static {
        var cells=StructurePattern.CELLS;
        int minX=cells.stream().mapToInt(c->c.offset().x()).min().orElseThrow(),maxX=cells.stream().mapToInt(c->c.offset().x()).max().orElseThrow();
        int minZ=cells.stream().mapToInt(c->c.offset().z()).min().orElseThrow(),maxZ=cells.stream().mapToInt(c->c.offset().z()).max().orElseThrow();
        int minY=cells.stream().mapToInt(c->c.offset().y()).min().orElseThrow(),maxY=cells.stream().mapToInt(c->c.offset().y()).max().orElseThrow();
        var positions=new ArrayList<Offset>();
        for(int y=minY;y<=maxY;y++) for(int z=minZ-EXTENSION;z<=maxZ+EXTENSION;z++) for(int x=minX-EXTENSION;x<=maxX+EXTENSION;x++)
            if(x<minX || x>maxX || z<minZ || z>maxZ) positions.add(new Offset(x,y,z));
        OFFSETS=List.copyOf(positions);
    }
    public interface View {
        boolean isLoaded(Offset relativePosition);
        boolean isProvider(Offset relativePosition);
    }
    public static int count(View world) {
        int count=0;
        for(var position:OFFSETS) {
            if(world.isLoaded(position) && world.isProvider(position) && ++count==MAX_EFFECTIVE) return count;
        }
        return count;
    }
    private BookshelfScan() {}
}
