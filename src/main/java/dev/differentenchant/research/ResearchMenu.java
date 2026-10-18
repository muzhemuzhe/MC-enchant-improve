package dev.differentenchant.research;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import dev.differentenchant.resonance.ResonanceItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

/** Vanilla workstation ownership: one temporary inventory per open menu, returned exactly once on close. */
public final class ResearchMenu extends AbstractContainerMenu {
    private static final AtomicLong REVISIONS=new AtomicLong();
    public static final int WIDTH=286,HEIGHT=218,INVENTORY_X=62,INVENTORY_Y=132;
    public final SimpleContainer inputs=new SimpleContainer(4) {
        @Override public void setChanged(){super.setChanged();if(!committing){revision=REVISIONS.incrementAndGet();observed=List.of();}}
    };
    private final Player owner;
    private final ServerLevel server;
    private final BlockPos origin;
    private final ContainerLevelAccess access;
    private long revision=REVISIONS.incrementAndGet(),sentRevision=-1;
    private boolean closed,committing,records;
    private int offset;
    private List<ItemStack> observed=List.of();
    private ResearchPreviewPayload clientPreview;
    private ResearchPolicy.Result completed;
    public ResearchMenu(int id,Inventory inventory){this(id,inventory,null,null);}
    public ResearchMenu(int id,Inventory inventory,ServerLevel server,BlockPos pos) {
        super(ResearchContent.MENU,id);owner=inventory.player;this.server=server;origin=pos==null?BlockPos.ZERO:pos.immutable();access=server==null?ContainerLevelAccess.NULL:ContainerLevelAccess.create(server,pos);
        for(int i=0;i<4;i++) {
            final int index=i;addSlot(new Slot(inputs,i,new int[]{24,56,88,244}[i],48) {
                @Override public boolean mayPlace(ItemStack item){return !closed&&!records&&(index==2?item.is(ResonanceItems.ATTUNED_CORE):index<2&&item.is(Items.ENCHANTED_BOOK));}
                @Override public int getMaxStackSize(){return index<2?1:64;}
                @Override public boolean mayPickup(Player player){return !closed&&!records&&stillValid(player);}
                @Override public boolean isActive(){return !records;}
            });
        }
        addStandardInventorySlots(inventory,INVENTORY_X,INVENTORY_Y);
    }
    @Override public boolean stillValid(Player player){return !closed&&player==owner&&(server==null||(player.level()==server&&server.hasChunkAt(origin)))&&stillValid(access,player,ResearchContent.TABLE);}
    public boolean recordsOpen(){return records;}
    private ResearchPolicy.Recipe recipe() {
        var a=ResearchBooks.read(inputs.getItem(0));var b=ResearchBooks.read(inputs.getItem(1));
        if(a==null||b==null)return null;
        var data=ResearchWorldData.get(server.getServer());var registry=server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for(var book:List.of(a,b))data.ensureInput(book.id(),registry.get(net.minecraft.resources.Identifier.parse(book.id())).orElseThrow().value());
        return new ResearchPolicy.Recipe(a,b);
    }
    public ResearchPreviewPayload snapshot() {
        if(server==null)throw new IllegalStateException("Server snapshot only");
        var data=ResearchWorldData.get(server.getServer());var recipe=recipe();
        int grade=recipe==null?0:data.grade(recipe);
        ResearchPolicy.Result known=recipe!=null&&data.knows(owner.getUUID(),recipe)?data.resolve(recipe):null;
        if(!inputs.getItem(3).isEmpty()&&completed!=null)known=completed;
        int status=!stillValid(owner)?5:!inputs.getItem(3).isEmpty()?3:recipe==null?1:!inputs.getItem(2).is(ResonanceItems.ATTUNED_CORE)?2:0;
        if(known!=null&&ResearchBooks.output(server.registryAccess(),known).isEmpty())status=4;
        var keys=data.records(owner.getUUID());offset=Math.clamp(offset,0,Math.max(0,keys.size()-3));
        var rows=records?keys.stream().skip(offset).limit(3).map(key->new ResearchPreviewPayload.Row(key,data.recorded(key))).toList():List.<ResearchPreviewPayload.Row>of();
        return new ResearchPreviewPayload(containerId,revision,inputs.getItem(0),inputs.getItem(1),inputs.getItem(2),grade,status,known,records,offset,keys.size(),rows);
    }
    public void acceptPreview(ResearchPreviewPayload payload){if(server==null&&!closed&&payload.containerId()==containerId){clientPreview=payload;records=payload.records();}}
    public ResearchPreviewPayload preview() {
        return clientPreview!=null&&ItemStack.matches(clientPreview.a(),inputs.getItem(0))&&ItemStack.matches(clientPreview.b(),inputs.getItem(1))
            &&ItemStack.matches(clientPreview.core(),inputs.getItem(2))?clientPreview:null;
    }
    public void awaitResponse(){clientPreview=null;}
    @Override public void broadcastChanges() {
        if(server!=null&&!closed) {
            var current=java.util.stream.IntStream.range(0,4).mapToObj(i->inputs.getItem(i).copy()).toList();
            boolean changed=observed.size()!=4;for(int i=0;!changed&&i<4;i++)changed=!ItemStack.matches(current.get(i),observed.get(i));
            if(changed){revision=REVISIONS.incrementAndGet();observed=current;}
        }
        super.broadcastChanges();
        if(server!=null&&!closed&&sentRevision!=revision&&owner instanceof ServerPlayer player&&player.containerMenu==this) {
            ServerPlayNetworking.send(player,snapshot());sentRevision=revision;
        }
    }
    public void handle(ServerPlayer player,ResearchActionPayload action) {
        if(server==null||closed||player!=owner||player.containerMenu!=this||action.containerId()!=containerId||!stillValid(player))return;
        broadcastChanges(); // Detect direct component mutation as well as normal slot changes before checking the quote.
        if(action.revision()!=revision)return;
        if(action.action()==1||action.action()==2){records=action.action()==1;offset=Math.max(0,action.offset());revision=REVISIONS.incrementAndGet();broadcastChanges();return;}
        if(action.action()!=0||records||committing||!inputs.getItem(3).isEmpty())return;
        var recipe=recipe();if(recipe==null||!inputs.getItem(2).is(ResonanceItems.ATTUNED_CORE))return;
        var data=ResearchWorldData.get(server.getServer());var result=data.resolve(recipe);var output=ResearchBooks.output(server.registryAccess(),result);
        if(output.isEmpty())return; // Removed/changed content is unavailable; never silently reroll it.
        committing=true;
        try {
            // Everything validated and output prepared before any mutation. Server thread serializes intents.
            if(!result.scroll()){inputs.removeItem(0,1);inputs.removeItem(1,1);}
            inputs.removeItem(2,1);inputs.setItem(3,output);completed=result;data.discover(player.getUUID(),recipe,result);
            revision=REVISIONS.incrementAndGet();observed=List.of();
        } finally {committing=false;}
        server.playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,net.minecraft.sounds.SoundSource.BLOCKS,.7f,1.1f);
        broadcastChanges();
    }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(!stillValid(player)||index<0||index>=slots.size()||(records&&index<4))return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var original=stack.copy();
        if(index<4){if(!moveItemStackTo(stack,4,40,true))return ItemStack.EMPTY;}
        else if(stack.is(ResonanceItems.ATTUNED_CORE)){if(!moveItemStackTo(stack,2,3,false))return ItemStack.EMPTY;}
        else if(stack.is(Items.ENCHANTED_BOOK)){if(!moveItemStackTo(stack,0,2,false))return ItemStack.EMPTY;}
        else if(index<31){if(!moveItemStackTo(stack,31,40,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(stack,4,31,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);broadcastChanges();return original;
    }
    @Override public void removed(Player player) {
        if(closed)return;super.removed(player);closed=true;
        if(server!=null)clearContainer(player,inputs);
        clientPreview=null;
    }
}
