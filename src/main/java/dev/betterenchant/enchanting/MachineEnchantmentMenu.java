package dev.betterenchant.enchanting;

import java.util.*;
import dev.betterenchant.*;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/** Vanilla inventory semantics with custom geometry; all offers and commits belong to the server. */
public final class MachineEnchantmentMenu extends EnchantmentMenu {
    private final DataSlot tierData=DataSlot.standalone(), shelfData=DataSlot.standalone();
    private static final java.util.concurrent.atomic.AtomicInteger QUOTES=new java.util.concurrent.atomic.AtomicInteger();
    private final DataSlot quoteLow=DataSlot.standalone(), quoteHigh=DataSlot.standalone();
    private final DataSlot visualSeed=DataSlot.standalone();
    private Player owner;
    private ServerLevel server;
    private BlockPos origin;
    private UUID machineId;
    private List<List<EnchantmentInstance>> offers=List.of(List.of(),List.of(),List.of());
    private ItemStack quotedItem=ItemStack.EMPTY;
    private EnchantingConfig.Settings settings;
    private int quotedSeed, quotedTier=-1, quotedShelves=-1;
    private Object registryTag;
    private int poolRevision=-1;
    private boolean committing, closed;
    private int sentQuote=-1;
    private EnchantingPreviewPayload clientPreview;

    /** Client factory: does not build or roll a candidate pool. */
    public MachineEnchantmentMenu(int id, Inventory inventory) {
        super(id,inventory);
        owner=inventory.player;
        relocateSlots();
        addDataSlot(tierData); addDataSlot(shelfData); addDataSlot(quoteLow); addDataSlot(quoteHigh); addDataSlot(visualSeed);
    }
    public MachineEnchantmentMenu(int id, Inventory inventory, ServerLevel level, MachineControllerBlockEntity master) {
        super(id,inventory,ContainerLevelAccess.create(level,master.getBlockPos()));
        owner=inventory.player; server=level; origin=master.getBlockPos().immutable(); machineId=master.machineId();
        relocateSlots();
        addDataSlot(tierData); addDataSlot(shelfData); addDataSlot(quoteLow); addDataSlot(quoteHigh); addDataSlot(visualSeed);
        refresh();
    }
    @Override public MenuType<?> getType() { return BetterEnchant.ENCHANTMENT_MENU; }
    @Override public int getEnchantmentSeed() { return visualSeed.get(); }
    public int machineTier() { return tierData.get(); }
    public int bookshelfCount() { return shelfData.get(); }
    private void relocateSlots() {
        // Slot coordinates are final in 26.1.2. Preserve containers, indices and vanilla filters.
        for(int i=0;i<slots.size();i++) {
            Slot original=slots.get(i); var point=MachineGuiLayout.slot(i);
            Slot replacement=new Slot(original.container,original.getContainerSlot(),point.x(),point.y()) {
                @Override public boolean mayPlace(ItemStack item) { return original.mayPlace(item); }
                @Override public boolean mayPickup(Player player) { return original.mayPickup(player); }
                @Override public int getMaxStackSize() { return original.getMaxStackSize(); }
                @Override public int getMaxStackSize(ItemStack item) { return original.getMaxStackSize(item); }
                @Override public net.minecraft.resources.Identifier getNoItemIcon() { return original.getNoItemIcon(); }
            };
            replacement.index=i; slots.set(i,replacement);
        }
    }
    public EnchantingPreviewPayload previewSnapshot() {
        if(server==null) throw new IllegalStateException("Server only");
        return new EnchantingPreviewPayload(containerId,buttonIdFor(0),quotedItem,offers.stream().map(result->
                new EnchantingPreviewPayload.Offer(PreviewPolicy.reveal(result,machineTier()).stream().map(entry->
                        new EnchantingPreviewPayload.Entry(entry.enchantment().unwrapKey().orElseThrow().identifier(),entry.level())).toList(),
                        PreviewPolicy.revealedCount(machineTier(),result.size())<result.size())).toList());
    }
    public void acceptPreview(EnchantingPreviewPayload payload) {
        if(server==null && !closed && payload.containerId()==containerId) clientPreview=payload;
    }
    public EnchantingPreviewPayload preview() {
        return clientPreview!=null && clientPreview.quote()==buttonIdFor(0)
                && ItemStack.matches(clientPreview.input(),getSlot(0).getItem()) ? clientPreview : null;
    }
    public int buttonIdFor(int option) {
        if(option<0 || option>2) throw new IllegalArgumentException("Invalid option");
        int quote=(quoteHigh.get()<<16) | (quoteLow.get() & 0xFFFF);
        return (quote<<2) | option;
    }
    @Override public void slotsChanged(Container container) {
        if(server!=null && !committing && !closed) { refresh(); super.broadcastChanges(); }
    }
    @Override public void broadcastChanges() {
        if(server!=null && !committing && !closed) refresh();
        super.broadcastChanges();
        sendPreview();
    }
    private void sendPreview() {
        if(server!=null && !closed && owner instanceof ServerPlayer player && player.containerMenu==this
                && sentQuote!=buttonIdFor(0) && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,EnchantingPreviewPayload.TYPE)) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,previewSnapshot());
            sentQuote=buttonIdFor(0);
        }
    }
    private boolean refresh() {
        if(closed || committing || server==null) return false;
        MachineControllerBlockEntity master=MachineAccess.controller(server,origin);
        boolean valid=master!=null && machineId.equals(master.machineId());
        int tier=valid ? Math.min(master.machineTier(),EnchantingConfig.current().maxTier()) : 0;
        int shelves=valid ? MachineAccess.shelves(server,master) : 0;
        ItemStack input=getSlot(0).getItem();
        Object tag=server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(net.minecraft.tags.EnchantmentTags.IN_ENCHANTING_TABLE).orElse(null);
        if(settings==EnchantingConfig.current() && quotedSeed==owner.getEnchantmentSeed() && quotedTier==tier && quotedShelves==shelves
                && ItemStack.matches(quotedItem,input) && registryTag==tag && poolRevision==EnchantmentCandidates.revision()) return false;
        settings=EnchantingConfig.current(); quotedSeed=owner.getEnchantmentSeed(); quotedTier=tier; quotedShelves=shelves; registryTag=tag;
        poolRevision=EnchantmentCandidates.revision();
        quotedItem=input.copy(); tierData.set(tier); shelfData.set(shelves);
        int quote=QUOTES.incrementAndGet() & 0x1FFFFFFF;
        quoteLow.set(quote & 0xFFFF); quoteHigh.set(quote>>>16);
        Arrays.fill(costs,0); Arrays.fill(enchantClue,-1); Arrays.fill(levelClue,-1);
        var next=new ArrayList<List<EnchantmentInstance>>();
        var pool=EnchantmentCandidates.forItem(server.registryAccess(),input);
        var idMap=server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).asHolderIdMap();
        for(int option=0;option<3;option++) {
            List<EnchantmentInstance> result=valid ? pool.resolve(RandomEnchantPolicy.prepare(pool.candidates(),
                    new RandomEnchantPolicy.Context(tier,shelves,option),settings.policy()).roll(new Random(EnchantingSeedData.forPlayer((ServerPlayer)owner,option)))) : List.of();
            next.add(result);
            if(!result.isEmpty()) {
                costs[option]=settings.requiredLevels().get(option);
                enchantClue[option]=idMap.getId(result.getFirst().enchantment());
                levelClue[option]=result.getFirst().level();
            }
        }
        offers=List.copyOf(next);
        quotedSeed=owner.getEnchantmentSeed();
        visualSeed.set(quotedSeed);
        return true;
    }
    @Override public boolean stillValid(Player player) {
        if(server==null) return !closed;
        if(closed || player!=owner || !player.isAlive() || player.isSpectator() || player.distanceToSqr(origin.getCenter())>64) return false;
        var master=MachineAccess.controller(server,origin);
        return master!=null && machineId.equals(master.machineId());
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        // Vanilla client method only gates sending a button request; it cannot commit results.
        if(server==null) return !closed && button>=0 && button<3 && super.clickMenuButton(player,button);
        int option=button & 3;
        if(button<0 || committing || option>2 || !stillValid(player)) return false;
        if(refresh()) { super.broadcastChanges(); return false; } // A stale context must first be shown to the player.
        if(button!=buttonIdFor(option)) return false; // Reject delayed/replayed packets, even after another input was inserted.
        ItemStack input=getSlot(0).getItem(), lapis=getSlot(1).getItem();
        int spent=option+1;
        boolean creative=player.hasInfiniteMaterials();
        if(costs[option]<=0 || !input.isEnchantable() || input.getCount()!=1 || !ItemStack.matches(input,quotedItem)
                || offers.get(option).isEmpty() || (!creative && (player.experienceLevel<costs[option]
                || player.experienceLevel<spent || !lapis.is(Items.LAPIS_LAZULI) || lapis.getCount()<spent))) return false;
        // Revalidate the current data-driven pool before any payment, even after a datapack reload.
        var pool=EnchantmentCandidates.forItem(server.registryAccess(),input);
        var selected=offers.get(option);
        for(var enchantment:selected) {
            var key=enchantment.enchantment().unwrapKey().orElseThrow().identifier().toString();
            if(!pool.holders().containsKey(key) || settings.policy().typeMultipliers().getOrDefault(key,1.0)<=0
                    || enchantment.level()<enchantment.enchantment().value().getMinLevel() || enchantment.level()>enchantment.enchantment().value().getMaxLevel()) return false;
            for(var other:selected) if(other!=enchantment && !Enchantment.areCompatible(enchantment.enchantment(),other.enchantment())) return false;
        }
        ItemStack output=input.is(Items.BOOK) ? input.transmuteCopy(Items.ENCHANTED_BOOK) : input.copy();
        for(var enchantment:selected) output.enchant(enchantment.enchantment(),enchantment.level());
        committing=true;
        try {
            // Prepare a separate result first, then commit exactly once on the server thread.
            int experience=player.experienceLevel;
            player.onEnchantmentPerformed(output,creative ? 0 : spent);
            if(creative) player.experienceLevel=experience;
            if(!creative) lapis.shrink(spent);
            getSlot(1).set(lapis.isEmpty() ? ItemStack.EMPTY : lapis);
            getSlot(0).set(output);
        } finally { committing=false; }
        player.awardStat(Stats.ENCHANT_ITEM);
        CriteriaTriggers.ENCHANTED_ITEM.trigger((ServerPlayer)player,output,spent);
        server.playSound(null,origin,SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.BLOCKS,1,1);
        refresh(); super.broadcastChanges();
        return true;
    }
    @Override public void removed(Player player) {
        if(closed) return;
        closed=true;
        super.removed(player); // Vanilla returns both transient slots or drops them safely if the inventory is full.
    }
}
