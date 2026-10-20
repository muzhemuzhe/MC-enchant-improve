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
    public static final int WIDTH=230,HEIGHT=218,INVENTORY_X=34,INVENTORY_Y=132;
    public final SimpleContainer inputs=new SimpleContainer(4) {
        @Override public void setChanged(){super.setChanged();if(!committing){revision=REVISIONS.incrementAndGet();observed=List.of();}}
    };
    private final Player owner;
    private final ServerLevel server;
    private final BlockPos origin;
    private final UUID machine;
    private long revision=REVISIONS.incrementAndGet(),sentRevision=-1;
    private boolean closed,committing,records;
    private int offset;
    private List<ItemStack> observed=List.of();
    private ResearchPreviewPayload clientPreview;
    public ResearchMenu(int id,Inventory inventory){this(id,inventory,null,null);}
    public ResearchMenu(int id,Inventory inventory,ServerLevel server,BlockPos pos) {
        super(ResearchContent.MENU,id);owner=inventory.player;this.server=server;origin=pos==null?BlockPos.ZERO:pos.immutable();var master=server==null?null:dev.differentenchant.workstation.WorkstationStructure.controller(server,pos,dev.differentenchant.workstation.WorkstationStructure.Kind.RESEARCH);machine=master==null?null:master.machineId();
        for(int i=0;i<4;i++) {
            final int index=i;addSlot(new Slot(inputs,i,new int[]{24,56,88,180}[i],58) {
                @Override public boolean mayPlace(ItemStack item){return !closed&&!records&&(index==2?item.is(ResonanceItems.ATTUNED_CORE):index<2&&item.is(Items.ENCHANTED_BOOK));}
                @Override public int getMaxStackSize(){return index<2?1:64;}
                // Display only: native safeTake / double-click collection must never deliver this stack.
                @Override public boolean mayPickup(Player player){return index<3&&!closed&&!records&&stillValid(player);}
                @Override public boolean isActive(){return !records;}
            });
        }
        addStandardInventorySlots(inventory,INVENTORY_X,INVENTORY_Y);
    }
    @Override public boolean stillValid(Player player){
        if(closed||player!=owner||!player.isAlive()||player.isSpectator())return false;if(server==null)return true;
        if(player.level()!=server||player.distanceToSqr(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5)>64)return false;
        var master=dev.differentenchant.workstation.WorkstationStructure.controller(server,origin,dev.differentenchant.workstation.WorkstationStructure.Kind.RESEARCH);
        return machine!=null&&master!=null&&machine.equals(master.machineId());
    }
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
        ResearchPolicy.Result known=recipe!=null&&data.knows(owner.getUUID(),recipe)?data.resolve(recipe):null;
        int status=!stillValid(owner)?5:recipe==null?1:!inputs.getItem(2).is(ResonanceItems.ATTUNED_CORE)?2:0;
        if(known!=null&&ResearchBooks.output(server.registryAccess(),known).isEmpty())status=4;
        var keys=data.records(owner.getUUID());offset=Math.clamp(offset,0,Math.max(0,keys.size()-3));
        var rows=records?keys.stream().skip(offset).limit(3).map(key->new ResearchPreviewPayload.Row(key,ResearchPreviewPayload.DisplayResult.of(data.recorded(key)))).toList():List.<ResearchPreviewPayload.Row>of();
        return new ResearchPreviewPayload(containerId,revision,inputs.getItem(0),inputs.getItem(1),inputs.getItem(2),status,known==null?null:ResearchPreviewPayload.DisplayResult.of(known),records,offset,keys.size(),rows);
    }
    public void acceptPreview(ResearchPreviewPayload payload){if(server==null&&!closed&&payload.containerId()==containerId){clientPreview=payload;records=payload.records();}}
    public ResearchPreviewPayload preview() {
        return clientPreview!=null&&ItemStack.matches(clientPreview.a(),inputs.getItem(0))&&ItemStack.matches(clientPreview.b(),inputs.getItem(1))
            &&ItemStack.matches(clientPreview.core(),inputs.getItem(2))?clientPreview:null;
    }
    public void awaitResponse(){clientPreview=null;}
    private void refreshResult() {
        var display=ItemStack.EMPTY;
        if(!records&&stillValid(owner)&&inputs.getItem(2).is(ResonanceItems.ATTUNED_CORE)) {
            var recipe=recipe();var data=ResearchWorldData.get(server.getServer());
            if(recipe!=null&&data.knows(owner.getUUID(),recipe))display=ResearchBooks.output(server.registryAccess(),data.resolve(recipe));
            else if(recipe!=null) {
                display=new ItemStack(Items.BOOK);
                display.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.translatable("research.differentenchant.unknown"));
            }
        }
        if(!ItemStack.matches(display,inputs.getItem(3))) {
            committing=true;try{inputs.setItem(3,display);}finally{committing=false;}
            revision=REVISIONS.incrementAndGet();
        }
    }
    @Override public void broadcastChanges() {
        if(server!=null&&!closed) {
            refreshResult();
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
        // Actions only navigate pages. The former research-button action is intentionally inert.
    }
    private static boolean fits(ItemStack target,ItemStack output) {
        return target.isEmpty()||(ItemStack.isSameItemSameComponents(target,output)&&target.getCount()+output.getCount()<=target.getMaxStackSize());
    }
    /** No preview is ever transferred. Prepare and validate a real server output before any mutation. */
    private ItemStack takeResult(Player player,int destination) {
        if(server==null||closed||committing||records||player!=owner||player.containerMenu!=this||!stillValid(player))return ItemStack.EMPTY;
        var recipe=recipe();if(recipe==null||!inputs.getItem(2).is(ResonanceItems.ATTUNED_CORE))return ItemStack.EMPTY;
        var data=ResearchWorldData.get(server.getServer());var result=data.resolve(recipe);var output=ResearchBooks.output(server.registryAccess(),result);
        if(output.isEmpty())return ItemStack.EMPTY;
        if(destination==-1&&!fits(getCarried(),output))return ItemStack.EMPTY;
        if(destination>=0&&!fits(player.getInventory().getItem(destination),output))return ItemStack.EMPTY;
        if(destination==-2) {
            boolean room=false;for(int i=4;i<40;i++)if(fits(slots.get(i).getItem(),output)){room=true;break;}
            if(!room)return ItemStack.EMPTY;
        }
        committing=true;
        try {
            // One-item output; capacity was checked on this same server thread. Never drop a failed delivery.
            if(destination==-2) {
                var delivery=output.copy();if(!moveItemStackTo(delivery,4,40,true))return ItemStack.EMPTY;
            } else if(destination==-1) {
                if(getCarried().isEmpty())setCarried(output.copy());else getCarried().grow(output.getCount());
            } else {
                var target=player.getInventory().getItem(destination);
                if(target.isEmpty())player.getInventory().setItem(destination,output.copy());else target.grow(output.getCount());
            }
            if(!result.scroll()){inputs.removeItem(0,1);inputs.removeItem(1,1);}
            inputs.removeItem(2,1);data.discover(player.getUUID(),recipe,result);
            revision=REVISIONS.incrementAndGet();observed=List.of();
        } finally {committing=false;}
        if(player instanceof ServerPlayer recipient){
            dev.differentenchant.advancement.ModAdvancements.obtained(recipient,output);
            if(result.scroll())dev.differentenchant.advancement.ModAdvancements.committed(recipient,"price_of_knowledge");
        }
        server.playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,net.minecraft.sounds.SoundSource.BLOCKS,.7f,1.1f);
        broadcastChanges();
        return output;
    }
    @Override public void clicked(int index,int button,ContainerInput input,Player player) {
        if(!stillValid(player)||player!=owner)return;
        if(index==3) {
            // Client prediction stays inert; the native container packet/synchronizer supplies the real delivery.
            if(input==ContainerInput.PICKUP&&(button==0||button==1))takeResult(player,-1);
            else if(input==ContainerInput.QUICK_MOVE&&(button==0||button==1))takeResult(player,-2);
            else if(input==ContainerInput.SWAP&&((button>=0&&button<9)||button==40))takeResult(player,button);
            return;
        }
        super.clicked(index,button,input,player);
    }
    @Override public boolean canTakeItemForPickAll(ItemStack carried,Slot target){return target!=getSlot(3)&&super.canTakeItemForPickAll(carried,target);}
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(index==3)return takeResult(player,-2);
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
        if(server!=null){inputs.setItem(3,ItemStack.EMPTY);clearContainer(player,inputs);}
        clientPreview=null;
    }
}
