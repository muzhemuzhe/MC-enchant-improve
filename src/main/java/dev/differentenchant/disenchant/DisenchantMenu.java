package dev.differentenchant.disenchant;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

/** Preview stacks cannot leave slots. Every supported pickup commits a fresh server preparation. */
public final class DisenchantMenu extends AbstractContainerMenu {
    public static final int WIDTH=230,HEIGHT=222,INVENTORY_X=34,INVENTORY_Y=136;
    public final SimpleContainer inputs=new SimpleContainer(3);
    private final Player owner;private final ServerLevel server;private final BlockPos origin;private final UUID machine;
    private final DataSlot selected=DataSlot.standalone(),cost=DataSlot.standalone();
    private ItemStack observed=ItemStack.EMPTY,quote=ItemStack.EMPTY;private String selectedId;
    private boolean closed,committing;
    public DisenchantMenu(int id,Inventory inv){this(id,inv,null,null);}
    public DisenchantMenu(int id,Inventory inv,ServerLevel level,BlockPos pos) {
        super(DisenchantContent.MENU,id);owner=inv.player;server=level;origin=pos==null?BlockPos.ZERO:pos.immutable();
        var master=level==null?null:DisenchantStructure.controller(level,pos);machine=master==null?null:master.machineId();selected.set(-1);cost.set(0);addDataSlot(selected);addDataSlot(cost);
        for(int i=0;i<3;i++){final int index=i;addSlot(new Slot(inputs,i,new int[]{16,48,186}[i],34){
            @Override public boolean mayPlace(ItemStack stack){return !closed&&(index==0?DisenchantingService.equipment(stack):index==1&&stack.is(Items.BOOK));}
            @Override public int getMaxStackSize(){return index==0?1:64;}
            @Override public boolean mayPickup(Player p){return index<2&&!closed&&stillValid(p);}
        });}addStandardInventorySlots(inv,INVENTORY_X,INVENTORY_Y);
    }
    @Override public boolean stillValid(Player p){if(closed||p!=owner||p.isSpectator()||!p.isAlive())return false;if(server==null)return true;if(p.level()!=server||p.distanceToSqr(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5)>64)return false;var master=DisenchantStructure.controller(server,origin);return machine!=null&&master!=null&&machine.equals(master.machineId());}
    public List<DisenchantingService.Choice> choices(){return DisenchantingService.choices(inputs.getItem(0));}
    public int selection(){return selected.get();}
    public int cost(){return cost.get();}
    private void refresh() {
        if(!ItemStack.matches(observed,inputs.getItem(0))){observed=inputs.getItem(0).copy();selectedId=null;selected.set(-1);quote=ItemStack.EMPTY;}
        var prepared=selectedId!=null&&ItemStack.matches(quote,inputs.getItem(0))&&stillValid(owner)?DisenchantingService.prepare(inputs.getItem(0),inputs.getItem(1),selectedId):null;
        var display=prepared==null?ItemStack.EMPTY:prepared.book();cost.set(prepared==null?0:prepared.cost());
        if(!ItemStack.matches(display,inputs.getItem(2)))inputs.setItem(2,display);
    }
    @Override public void broadcastChanges(){if(server!=null&&!closed&&!committing)refresh();super.broadcastChanges();}
    @Override public boolean clickMenuButton(Player p,int index) {
        if(server==null||p!=owner||p.containerMenu!=this||!stillValid(p)||committing)return false;
        refresh();var choices=choices();if(index<0||index>=choices.size())return false;
        selectedId=choices.get(index).id();selected.set(index);quote=inputs.getItem(0).copy();broadcastChanges();return true;
    }
    private static boolean fits(ItemStack target,ItemStack output){return target.isEmpty()||ItemStack.isSameItemSameComponents(target,output)&&target.getCount()+output.getCount()<=Math.min(target.getMaxStackSize(),output.getMaxStackSize());}
    private ItemStack take(Player p,int destination) {
        if(server==null||closed||committing||p!=owner||p.containerMenu!=this||!stillValid(p))return ItemStack.EMPTY;
        refresh();if(selectedId==null||!ItemStack.matches(quote,inputs.getItem(0)))return ItemStack.EMPTY;
        var prepared=DisenchantingService.prepare(inputs.getItem(0),inputs.getItem(1),selectedId);if(prepared==null||!p.getAbilities().instabuild&&p.experienceLevel<prepared.cost())return ItemStack.EMPTY;
        var output=prepared.book();
        if(destination==-1&&!fits(getCarried(),output)||destination>=0&&!fits(p.getInventory().getItem(destination),output))return ItemStack.EMPTY;
        if(destination==-2){boolean room=false;for(int i=3;i<39;i++)if(fits(slots.get(i).getItem(),output)){room=true;break;}if(!room)return ItemStack.EMPTY;}
        committing=true;
        try {
            if(destination==-2){var delivery=output.copy();if(!moveItemStackTo(delivery,3,39,true))return ItemStack.EMPTY;}
            else if(destination==-1){if(getCarried().isEmpty())setCarried(output.copy());else getCarried().grow(1);}
            else{var target=p.getInventory().getItem(destination);if(target.isEmpty())p.getInventory().setItem(destination,output.copy());else target.grow(1);}
            inputs.setItem(0,prepared.equipment());inputs.removeItem(1,1);if(!p.getAbilities().instabuild)p.giveExperienceLevels(-prepared.cost());
            selectedId=null;selected.set(-1);quote=ItemStack.EMPTY;
        }finally{committing=false;}
        server.playSound(null,origin,net.minecraft.sounds.SoundEvents.GRINDSTONE_USE,net.minecraft.sounds.SoundSource.BLOCKS,.7f,.8f);broadcastChanges();return output;
    }
    @Override public void clicked(int index,int button,ContainerInput input,Player p) {
        if(!stillValid(p)||p!=owner)return;
        if(index==2){if(input==ContainerInput.PICKUP&&(button==0||button==1))take(p,-1);else if(input==ContainerInput.QUICK_MOVE&&(button==0||button==1))take(p,-2);else if(input==ContainerInput.SWAP&&(button>=0&&button<9||button==40))take(p,button);return;}
        super.clicked(index,button,input,p);
    }
    @Override public boolean canTakeItemForPickAll(ItemStack carried,Slot slot){return slot!=getSlot(2)&&super.canTakeItemForPickAll(carried,slot);}
    @Override public ItemStack quickMoveStack(Player p,int index) {
        if(index==2)return take(p,-2);if(!stillValid(p)||index<0||index>=slots.size())return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var original=stack.copy();
        if(index<3){if(!moveItemStackTo(stack,3,39,true))return ItemStack.EMPTY;}
        else if(stack.is(Items.BOOK)){if(!moveItemStackTo(stack,1,2,false))return ItemStack.EMPTY;}
        else if(DisenchantingService.equipment(stack)){if(!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;}
        else if(index<30){if(!moveItemStackTo(stack,30,39,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(stack,3,30,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);broadcastChanges();return original;
    }
    @Override public void removed(Player p){if(closed)return;super.removed(p);closed=true;if(server!=null){inputs.setItem(2,ItemStack.EMPTY);clearContainer(p,inputs);}}
}
