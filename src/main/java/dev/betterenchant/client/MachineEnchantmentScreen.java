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
        bookOpen+=((menu.costs[0]>0?1f:0f)-bookOpen)*.2f;
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
        if(font.width(text)<=max) g.text(font,text,x,y,color);
        else g.text(font,Component.literal(font.plainSubstrByWidth(text.getString(),Math.max(0,max-font.width("...")))+"..."),x,y,color);
    }
    private boolean enabled(int i) {
        return menu.preview()!=null && menu.costs[i]>0 && (minecraft.player.hasInfiniteMaterials()
                || (minecraft.player.experienceLevel>=menu.costs[i] && menu.getGoldCount()>=i+1));
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
        g.fill(RANDOM_TAB.x(),RANDOM_TAB.y()+11,RANDOM_TAB.x()+RANDOM_TAB.width(),RANDOM_TAB.y()+12,0xFF8D8066);
        hoveredOption=-1;
        if(lastQuote!=menu.buttonIdFor(0)) { java.util.Arrays.fill(tooltipOffsets,0);lastQuote=menu.buttonIdFor(0); }
        EnchantmentNames.getInstance().initSeed(menu.getEnchantmentSeed());
        for(int i=0;i<3;i++) {
            var r=option(i);boolean hover=r.contains(mouseX-leftPos,mouseY-topPos),active=enabled(i);
            if(hover) hoveredOption=i;
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,sprite(menu.costs[i]<=0 || !active?"enchantment_slot_disabled":hover?"enchantment_slot_highlighted":"enchantment_slot"),r.x(),r.y(),r.width(),r.height());
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,sprite("level_"+(i+1)+(active?"":"_disabled")),r.x()+1,r.y()+1,16,16);
            int runeWidth=r.width()-39;
            g.textWithWordWrap(font,EnchantmentNames.getInstance().getRandomName(font,runeWidth),r.x()+20,r.y()+2,runeWidth,active?(hover?0xFFFFFF80:0xFF685E4A):0xFF817860,false);
            if(menu.costs[i]>0) {
                String level=Integer.toString(menu.costs[i]);
                g.text(font,level,r.x()+r.width()-font.width(level)-3,r.y()+9,active?0xFF80FF20:0xFF536D34);
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
        g.centeredText(font,tr("directed"),DIRECTED_TAB.x()+DIRECTED_TAB.width()/2,19,0xFF888276);
        clipped(g,tr("shelves",menu.bookshelfCount()),10,101,76,MUTED);
        clipped(g,tr("hover_preview"),92,101,158,MUTED);
        g.text(font,playerInventoryTitle,49,115,0xFF404040,false);
    }
    @Override protected void extractTooltip(GuiGraphicsExtractor g,int x,int y) {
        super.extractTooltip(g,x,y);
        if(DIRECTED_TAB.contains(x-leftPos,y-topPos)) {
            g.setTooltipForNextFrame(font,font.split(tr("unavailable"),Math.min(230,width-30)),x,y); return;
        }
        if(hoveredOption<0) return;
        int i=hoveredOption;
        if(menu.costs[i]<=0) {
            g.setTooltipForNextFrame(font,font.split(tr(menu.getSlot(0).getItem().isEnchanted()?"already_enchanted":"insert"),Math.min(240,width-30)),x,y);return;
        }
        if(menu.preview()==null) {
            g.setTooltipForNextFrame(font,font.split(tr("syncing"),240),x,y);return;
        }
        var lines=new ArrayList<FormattedCharSequence>();
        lines.addAll(font.split(tr("required_full",menu.costs[i]).withStyle(ChatFormatting.GOLD),Math.min(240,width-30)));
        lines.addAll(font.split(tr("spent_full",i+1,i+1).withStyle(ChatFormatting.GRAY),Math.min(240,width-30)));
        if(minecraft.player.hasInfiniteMaterials()) lines.addAll(font.split(tr("creative"),240));
        else if(!enabled(i) && menu.costs[i]>0) lines.addAll(font.split(tr("insufficient").withStyle(ChatFormatting.RED),240));
        lines.addAll(font.split(tr("revealed_count",revealed(i).size()).withStyle(ChatFormatting.GRAY),240));
        for(var name:revealed(i)) lines.addAll(font.split(name,Math.min(240,width-30)));
        if(menu.preview()!=null && menu.preview().offers().get(i).hidden()) lines.addAll(font.split(tr("hidden").withStyle(ChatFormatting.DARK_PURPLE),240));
        // Page long wrapped names/results by line; no assumption about result count.
        int page=Math.max(3,(height-45)/10-1);
        int offset=Math.clamp(tooltipOffsets[i],0,Math.max(0,lines.size()-page)); tooltipOffsets[i]=offset;
        var visible=new ArrayList<>(lines.subList(offset,Math.min(lines.size(),offset+page)));
        if(lines.size()>page) visible.add(tr("scroll",offset+1,Math.min(lines.size(),offset+page),lines.size()).withStyle(ChatFormatting.YELLOW).getVisualOrderText());
        g.setTooltipForNextFrame(font,visible,x,y);
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy) {
        for(int i=0;i<3;i++) if(sy!=0 && option(i).contains(x-leftPos,y-topPos)) {
            int lines=(int)Math.clamp(Math.round(Math.abs(sy)),1,100);
            tooltipOffsets[i]=Math.max(0,tooltipOffsets[i]+(sy<0?lines:-lines)); return true;
        }
        return super.mouseScrolled(x,y,sx,sy);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        for(int i=0;i<3;i++) if(option(i).contains(event.x()-leftPos,event.y()-topPos)) {
            if(event.button()==0 && enabled(i)) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,menu.buttonIdFor(i));
            return true;
        }
        if(DIRECTED_TAB.contains(event.x()-leftPos,event.y()-topPos)) return true;
        return super.mouseClicked(event,doubleClick);
    }
}
