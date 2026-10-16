package dev.betterenchant.client;

import java.util.ArrayList;
import java.util.List;
import dev.betterenchant.enchanting.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentNames;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.enchantment.Enchantment;
import static dev.betterenchant.enchanting.MachineGuiLayout.*;

/** Pixel geometry only. Native container rendering owns items, tooltips and inventory shortcuts. */
public final class MachineEnchantmentScreen extends AbstractContainerScreen<MachineEnchantmentMenu> {
    private static final int TEXT=0xFF40382B, MUTED=0xFF71695B;
    private static final net.minecraft.resources.Identifier PANEL=net.minecraft.resources.Identifier.withDefaultNamespace("textures/gui/container/enchanting_table.png");
    private static final net.minecraft.resources.Identifier BOOK=net.minecraft.resources.Identifier.withDefaultNamespace("textures/entity/enchantment/enchanting_table_book.png");
    private net.minecraft.client.model.object.book.BookModel bookModel;
    private float bookOpen,previousBookOpen;
    private final int[] tooltipOffsets=new int[3];
    private int hoveredOption=-1, lastQuote=-1;
    private int directedScroll,hoveredDirected=-1,directedTooltipOffset,lastDirectedHover=-1;
    private net.minecraft.world.item.ItemStack directedInput=net.minecraft.world.item.ItemStack.EMPTY;
    public MachineEnchantmentScreen(MachineEnchantmentMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title,WIDTH,HEIGHT);
    }
    private net.minecraft.network.chat.MutableComponent tr(String key,Object... args) { return Component.translatable("gui.better_enchant."+key,args); }
    @Override protected void init() {
        super.init();
        bookModel=new net.minecraft.client.model.object.book.BookModel(minecraft.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.BOOK));
    }
    @Override public void containerTick() {
        super.containerTick();previousBookOpen=bookOpen;
        bookOpen+=((menu.costs[0]>0 || (menu.isDirected() && menu.displayControls()!=null && !menu.displayControls().rows().isEmpty())?1f:0f)-bookOpen)*.2f;
    }
    private void request(int action,String id) {
        var state=menu.controlState();if(state==null) return;
        menu.awaitDirectedResponse();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new DirectedActionPayload(menu.containerId,state.revision(),action,id));
    }
    private Component level(int value) {return value==0?Component.literal("-"):Component.translatableWithFallback("enchantment.level."+value,Integer.toString(value));}
    private Component name(DirectedPreviewPayload.Row row) {
        return minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(row.id()).<Component>map(h->h.value().description()).orElseGet(()->Component.literal(row.id().toString()));
    }
    /** The label is the planned result, never an upgrade path or a second prediction. */
    public Component plannedLabel(DirectedPreviewPayload.Row row) {
        return row.target()>0?name(row).copy().append(" ").append(level(row.target())):name(row);
    }
    private boolean shiftDown() {
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(minecraft.getWindow(),com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(minecraft.getWindow(),com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT);
    }
    private void button(GuiGraphicsExtractor g,Rect r,Component label,boolean enabled,boolean hover) {
        String state=!enabled?"_disabled":hover?"_highlighted":"";
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,net.minecraft.resources.Identifier.withDefaultNamespace("widget/button"+state),r.x(),r.y(),r.width(),r.height());
        g.centeredText(font,label,r.x()+r.width()/2,r.y()+(r.height()-8)/2,enabled?0xFFFFFFFF:0xFFA0A0A0);
    }
    private void drawDirected(GuiGraphicsExtractor g,int mx,int my) {
        if(!net.minecraft.world.item.ItemStack.matches(directedInput,menu.getSlot(0).getItem())) {directedScroll=0;directedInput=menu.getSlot(0).getItem().copy();}
        hoveredDirected=-1;var state=menu.displayControls();boolean ready=menu.controlState()!=null;
        if(state==null) {clipped(g,tr("syncing"),94,50,150,TEXT);return;}
        directedScroll=Math.clamp(directedScroll,0,Math.max(0,state.rows().size()-3));
        g.fill(90,33,252,93,0xFF6C6352);
        if(state.rows().isEmpty()) g.textWithWordWrap(font,tr("directed_insert"),94,39,150,TEXT,false);
        for(int i=0;i<3 && i+directedScroll<state.rows().size();i++) {
            var row=state.rows().get(i+directedScroll);var r=directedRow(i);boolean hover=r.contains(mx,my);
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,sprite("enchantment_slot"),r.x(),r.y(),r.width(),r.height()-1);
            if(hover) {
                g.fill(r.x()+1,r.y()+1,r.x()+r.width()-1,r.y()+r.height()-2,0x30FFF2C0);
                g.fill(r.x()+1,r.y()+1,r.x()+2,r.y()+r.height()-2,0xFFFFE4A0);
            }
            // Reserve the level suffix even when a modded name needs clipping.
            Component suffix=row.target()>0?Component.empty().append(" ").append(level(row.target())):Component.empty();
            int nameWidth=122-font.width(suffix),labelX=r.x()+5;
            Component label=name(row);
            if(font.width(label)>nameWidth) label=Component.literal(font.plainSubstrByWidth(label.getString(),Math.max(0,nameWidth-font.width("...")))+"...");
            g.text(font,label.copy().append(suffix),labelX,r.y()+6,row.target()>row.current()?0xFF285220:TEXT,false);
            button(g,minus(i),Component.literal("-"),ready && row.minus(),minus(i).contains(mx,my));
            if(row.target()<row.max()) button(g,plus(i),Component.literal("+"),ready && row.plus(),plus(i).contains(mx,my));
            if(hover) hoveredDirected=i+directedScroll;
        }
        if(state.rows().size()>3) {
            g.fill(254,33,256,93,0xFF777166);int thumb=33+directedScroll*48/(state.rows().size()-3);g.fill(254,thumb,256,thumb+12,0xFFE2DBCA);
        }
        int stones=state.reagent().is(dev.betterenchant.BetterEnchant.RUNIC_STONE)?state.reagent().getCount():0;
        clipped(g,tr("plan_stones",state.upgrades(),stones),90,97,101,TEXT);
        clipped(g,tr("plan_cost",state.levels()),90,107,101,TEXT);
        button(g,ENCHANT_BUTTON,tr("enchant"),ready && state.canEnchant(),ENCHANT_BUTTON.contains(mx,my));
    }
    private void directedTooltip(GuiGraphicsExtractor g,int x,int y) {
        var state=menu.displayControls();if(state==null) return;
        var lines=new ArrayList<FormattedCharSequence>();
        if(ENCHANT_BUTTON.contains(x-leftPos,y-topPos)) {
            lines.addAll(font.split(tr("directed_total",state.levels(),state.upgrades()),164));
            if(!state.canEnchant()) lines.addAll(font.split(tr("directed_unready").withStyle(ChatFormatting.RED),164));
        } else if(hoveredDirected>=0 && hoveredDirected<state.rows().size()) {
            var row=state.rows().get(hoveredDirected);
            var description=font.split(plannedLabel(row).copy().withStyle(ChatFormatting.WHITE),164);
            if(shiftDown()) lines.addAll(description);
            else {
                if(description.size()<=2) lines.addAll(description);
                else {
                    lines.add(description.getFirst());
                    lines.add(Component.literal("...").getVisualOrderText());
                }
                if(row.nextCost()>0) lines.addAll(font.split(tr("directed_next",row.nextCost()).withStyle(ChatFormatting.GRAY),164));
                if(row.blocked()>0) lines.addAll(font.split(tr("blocked_"+row.blocked()).withStyle(ChatFormatting.RED),164));
                if(description.size()>2) lines.addAll(font.split(tr("shift_more").withStyle(ChatFormatting.YELLOW),164));
            }
        } else {
            var slot=MachineGuiLayout.slot(1);
            if(menu.getSlot(1).getItem().isEmpty() && new Rect(slot.x(),slot.y(),16,16).contains(x-leftPos,y-topPos)) lines.addAll(font.split(tr("insert_stone"),164));
        }
        if(lines.isEmpty()) return;
        if(lastDirectedHover!=hoveredDirected) {lastDirectedHover=hoveredDirected;directedTooltipOffset=0;}
        // Small vanilla tooltip below the pool: it never covers rows or +/- targets.
        int page=5;directedTooltipOffset=shiftDown()?Math.clamp(directedTooltipOffset,0,Math.max(0,lines.size()-page)):0;
        var shown=new ArrayList<>(lines.subList(directedTooltipOffset,Math.min(lines.size(),directedTooltipOffset+page)));
        if(lines.size()>page) shown.add(tr("shift_scroll").withStyle(ChatFormatting.YELLOW).getVisualOrderText());
        tooltipBelowPool(g,shown,x,y);
    }
    private void tooltipBelowPool(GuiGraphicsExtractor g,List<FormattedCharSequence> lines,int x,int y) {
        g.setTooltipForNextFrame(font,lines,(sw,sh,mx,my,w,h)->new org.joml.Vector2i(
                Math.clamp(leftPos+94,5,Math.max(5,sw-w-5)),Math.clamp(topPos+120,topPos+DIRECTED_LIST.y()+DIRECTED_LIST.height()+6,Math.max(topPos+DIRECTED_LIST.y()+DIRECTED_LIST.height()+6,sh-h-6))),x,y,false);
    }
    private void region(GuiGraphicsExtractor g,int x,int y,int w,int h,int u,int v,int sw,int sh) {
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,PANEL,x,y,u,v,w,h,sw,sh,256,256);
    }
    private void panel(GuiGraphicsExtractor g) {
        // Reference vanilla pixels directly, keeping bevels at native thickness. No bundled copies.
        region(g,4,4,WIDTH-8,HEIGHT-8,8,8,1,1);
        region(g,0,0,4,4,0,0,4,4);region(g,WIDTH-4,0,4,4,172,0,4,4);
        region(g,0,HEIGHT-4,4,4,0,162,4,4);region(g,WIDTH-4,HEIGHT-4,4,4,172,162,4,4);
        region(g,4,0,WIDTH-8,4,4,0,168,4);region(g,4,HEIGHT-4,WIDTH-8,4,4,162,168,4);
        region(g,0,4,4,HEIGHT-8,0,4,4,70);region(g,WIDTH-4,4,4,HEIGHT-8,172,4,4,70);
    }
    private static net.minecraft.resources.Identifier sprite(String path) {
        return net.minecraft.resources.Identifier.withDefaultNamespace("container/enchanting_table/"+path);
    }
    private void clipped(GuiGraphicsExtractor g,Component text,int x,int y,int max,int color) {
        if(font.width(text)<=max) g.text(font,text,x,y,color,false);
        else g.text(font,Component.literal(font.plainSubstrByWidth(text.getString(),Math.max(0,max-font.width("...")))+"..."),x,y,color,false);
    }
    private boolean enoughLapis(int i) {return menu.getSlot(1).getItem().is(net.minecraft.world.item.Items.LAPIS_LAZULI) && menu.getGoldCount()>=i+1;}
    private boolean enabled(int i) {
        return !menu.isDirected() && menu.controlState()!=null && menu.preview()!=null && menu.costs[i]>0 && (minecraft.player.hasInfiniteMaterials()
                || (minecraft.player.experienceLevel>=menu.costs[i] && enoughLapis(i)));
    }
    private List<Component> revealed(int option) {
        var snapshot=menu.preview(); var names=new ArrayList<Component>();
        if(snapshot==null) return names;
        var registry=minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for(var entry:snapshot.offers().get(option).revealed()) names.add(registry.get(entry.id())
                .<Component>map(holder->Enchantment.getFullname(holder,entry.level())).orElseGet(()->Component.literal(entry.id()+" "+entry.level())));
        return names;
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float a) {
        super.extractBackground(g,mouseX,mouseY,a);
        g.pose().pushMatrix();g.pose().translate(leftPos,topPos);
        panel(g);
        for(var slot:menu.slots) region(g,slot.x-1,slot.y-1,18,18,14,46,18,18);
        // Modest text tabs share the panel, rather than becoming separate framed panels.
        var activeTab=menu.isDirected()?DIRECTED_TAB:RANDOM_TAB;
        g.fill(activeTab.x(),activeTab.y()+11,activeTab.x()+activeTab.width(),activeTab.y()+12,0xFF8D8066);
        hoveredOption=-1;
        if(lastQuote!=menu.buttonIdFor(0)) { java.util.Arrays.fill(tooltipOffsets,0);lastQuote=menu.buttonIdFor(0); }
        EnchantmentNames.getInstance().initSeed(menu.getEnchantmentSeed());
        if(menu.isDirected()) drawDirected(g,mouseX-leftPos,mouseY-topPos);
        else for(int i=0;i<3;i++) {
            var r=option(i);boolean hover=r.contains(mouseX-leftPos,mouseY-topPos),active=enabled(i);
            if(hover) hoveredOption=i;
            boolean offer=menu.costs[i]>0 && menu.preview()!=null && menu.controlState()!=null;
            boolean xpMissing=offer && !minecraft.player.hasInfiniteMaterials() && minecraft.player.experienceLevel<menu.costs[i];
            boolean lapisMissing=offer && !minecraft.player.hasInfiniteMaterials() && !enoughLapis(i);
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,sprite(hover && offer?"enchantment_slot_highlighted":active?"enchantment_slot":"enchantment_slot_disabled"),r.x(),r.y(),r.width(),r.height());
            if(hover) {
                g.fill(r.x()+1,r.y()+1,r.x()+r.width()-1,r.y()+2,0xFFFFE4A0);
                g.fill(r.x()+1,r.y()+1,r.x()+2,r.y()+r.height()-1,0xFFFFE4A0);
            }
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,sprite("level_"+(i+1)+(active?"":"_disabled")),r.x()+1,r.y()+1,16,16);
            int runeWidth=r.width()-39;
            var runes=EnchantmentNames.getInstance().getRandomName(font,runeWidth);
            if(menu.costs[i]>0) g.textWithWordWrap(font,runes,r.x()+20,r.y()+2,runeWidth,hover?0xFFFFFF80:active?0xFF685E4A:0xFF817860,false);
            if(xpMissing || lapisMissing) {
                int mid=r.x()+20+(r.width()-23)/2;
                if(xpMissing) g.fill(r.x()+20,r.y()+r.height()-2,lapisMissing?mid:r.x()+r.width()-3,r.y()+r.height()-1,0xFFB5473C);
                if(lapisMissing) g.fill(xpMissing?mid:r.x()+20,r.y()+r.height()-2,r.x()+r.width()-3,r.y()+r.height()-1,0xFF4775B8);
            }
            if(menu.costs[i]>0) {
                String level=Integer.toString(menu.costs[i]);
                g.text(font,level,r.x()+r.width()-font.width(level)-3,r.y()+9,xpMissing?0xFFFF7060:active?0xFF80FF20:0xFF879575);
            }
        }
        g.pose().popMatrix();
        // Book viewports are absolute screen pixels in the target API (not pose transformed).
        float open=net.minecraft.util.Mth.lerp(a,previousBookOpen,bookOpen);
        g.book(bookModel,BOOK,40f,open,.2f,leftPos+14,topPos+28,leftPos+76,topPos+72);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mouseX,int mouseY) {
        clipped(g,Component.translatable("container.better_enchant.title"),8,6,170,TEXT);
        var tier=tr("tier_short",menu.machineTier());g.text(font,tier,WIDTH-8-font.width(tier),6,MUTED,false);
        g.centeredText(font,tr("random"),RANDOM_TAB.x()+RANDOM_TAB.width()/2,19,TEXT);
        g.centeredText(font,tr("directed"),DIRECTED_TAB.x()+DIRECTED_TAB.width()/2,19,menu.isDirected()?TEXT:MUTED);
        clipped(g,tr("shelves",menu.bookshelfCount()),10,101,76,MUTED);
        if(!menu.isDirected()) clipped(g,tr("hover_preview"),92,101,158,MUTED);
        g.text(font,playerInventoryTitle,49,115,0xFF404040,false);
    }
    @Override protected void extractTooltip(GuiGraphicsExtractor g,int x,int y) {
        super.extractTooltip(g,x,y);
        if(menu.isDirected()) {directedTooltip(g,x,y);return;}
        if(hoveredOption<0) return;
        int i=hoveredOption;
        if(menu.costs[i]<=0) {
            g.setTooltipForNextFrame(font,font.split(tr(menu.getSlot(0).getItem().isEnchanted()?"already_enchanted":"insert"),Math.min(240,width-30)),x,y);return;
        }
        if(menu.preview()==null) {
            g.setTooltipForNextFrame(font,font.split(tr("syncing"),240),x,y);return;
        }
        var lines=new ArrayList<FormattedCharSequence>();
        int wrap=Math.min(180,width-30);
        lines.addAll(font.split(tr("required_full",menu.costs[i]).withStyle(ChatFormatting.GOLD),wrap));
        lines.addAll(font.split(tr("spent_full",i+1,i+1).withStyle(ChatFormatting.GRAY),wrap));
        if(minecraft.player.hasInfiniteMaterials()) lines.addAll(font.split(tr("creative"),wrap));
        else {
            if(minecraft.player.experienceLevel<menu.costs[i]) lines.addAll(font.split(tr("need_experience").withStyle(ChatFormatting.RED),wrap));
            if(!enoughLapis(i)) lines.addAll(font.split(tr("need_lapis").withStyle(ChatFormatting.BLUE),wrap));
        }
        lines.addAll(font.split(tr("revealed_count",revealed(i).size()).withStyle(ChatFormatting.GRAY),wrap));
        for(var name:revealed(i)) lines.addAll(font.split(name,wrap));
        if(menu.preview()!=null && menu.preview().offers().get(i).hidden()) lines.addAll(font.split(tr("hidden").withStyle(ChatFormatting.DARK_PURPLE),wrap));
        // Page long wrapped names/results by line; no assumption about result count.
        int page=8;
        int offset=Math.clamp(tooltipOffsets[i],0,Math.max(0,lines.size()-page)); tooltipOffsets[i]=offset;
        var visible=new ArrayList<>(lines.subList(offset,Math.min(lines.size(),offset+page)));
        if(lines.size()>page) visible.add(tr("scroll",offset+1,Math.min(lines.size(),offset+page),lines.size()).withStyle(ChatFormatting.YELLOW).getVisualOrderText());
        tooltipBelowPool(g,visible,x,y);
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy) {
        if(menu.isDirected() && DIRECTED_LIST.contains(x-leftPos,y-topPos) && sy!=0) {
            int delta=(int)Math.clamp(Math.round(Math.abs(sy)),1,100)*(sy<0?1:-1);
            if(shiftDown()) directedTooltipOffset=Math.max(0,directedTooltipOffset+delta);
            else {var state=menu.displayControls();directedScroll=Math.clamp(directedScroll+delta,0,state==null?0:Math.max(0,state.rows().size()-3));directedTooltipOffset=0;}
            return true;
        }
        for(int i=0;i<3;i++) if(sy!=0 && option(i).contains(x-leftPos,y-topPos)) {
            int lines=(int)Math.clamp(Math.round(Math.abs(sy)),1,100);
            tooltipOffsets[i]=Math.max(0,tooltipOffsets[i]+(sy<0?lines:-lines)); return true;
        }
        return super.mouseScrolled(x,y,sx,sy);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        double x=event.x()-leftPos,y=event.y()-topPos;
        if(event.button()==0) {
            if(RANDOM_TAB.contains(x,y)) {request(DirectedActionPayload.RANDOM,"");return true;}
            if(DIRECTED_TAB.contains(x,y)) {request(DirectedActionPayload.DIRECTED,"");return true;}
            if(menu.isDirected()) {
                var state=menu.controlState();
                if(ENCHANT_BUTTON.contains(x,y)) {if(state!=null && state.canEnchant()) request(DirectedActionPayload.ENCHANT,"");return true;}
                if(DIRECTED_LIST.contains(x,y)) {
                    int visible=(int)(y-DIRECTED_LIST.y())/20,index=directedScroll+visible;
                    if(state!=null && index<state.rows().size()) {
                        var row=state.rows().get(index);
                        if(plus(visible).contains(x,y) && row.plus()) request(DirectedActionPayload.ADD,row.id().toString());
                        if(minus(visible).contains(x,y) && row.minus()) request(DirectedActionPayload.REMOVE,row.id().toString());
                    }
                    return true;
                }
            }
        }
        if(menu.isDirected()) return super.mouseClicked(event,doubleClick);
        for(int i=0;i<3;i++) if(option(i).contains(event.x()-leftPos,event.y()-topPos)) {
            if(event.button()==0 && enabled(i)) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,menu.buttonIdFor(i));
            return true;
        }
        if(DIRECTED_TAB.contains(event.x()-leftPos,event.y()-topPos)) return true;
        return super.mouseClicked(event,doubleClick);
    }
}
