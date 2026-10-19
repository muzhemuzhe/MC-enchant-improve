package dev.differentenchant.client;

import dev.differentenchant.disenchant.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.enchantment.Enchantment;

public final class DisenchantScreen extends AbstractContainerScreen<DisenchantMenu> {
    private static final Identifier PANEL=Identifier.withDefaultNamespace("textures/gui/container/enchanting_table.png");
    private int offset;
    public DisenchantScreen(DisenchantMenu m,Inventory inv,Component title){super(m,inv,title,DisenchantMenu.WIDTH,DisenchantMenu.HEIGHT);}
    private Component tr(String key,Object... args){return Component.translatable("disenchant.differentenchant."+key,args);}
    private boolean inside(double x,double y,int rx,int ry,int w,int h){return x>=rx&&x<rx+w&&y>=ry&&y<ry+h;}
    private void region(GuiGraphicsExtractor g,int x,int y,int w,int h,int u,int v,int sw,int sh){g.blit(RenderPipelines.GUI_TEXTURED,PANEL,x,y,u,v,w,h,sw,sh,256,256);}
    private void clipped(GuiGraphicsExtractor g,Component text,int x,int y,int width,int color){if(font.width(text)>width)text=Component.literal(font.plainSubstrByWidth(text.getString(),Math.max(0,width-font.width("…")))+"…");g.text(font,text,x,y,color,false);}
    private Component label(DisenchantingService.Choice c){return Enchantment.getFullname(c.enchantment(),c.level());}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float partial) {
        super.extractBackground(g,mx,my,partial);g.pose().pushMatrix();g.pose().translate(leftPos,topPos);int w=DisenchantMenu.WIDTH,h=DisenchantMenu.HEIGHT;
        region(g,4,4,w-8,h-8,8,8,1,1);region(g,0,0,4,4,0,0,4,4);region(g,w-4,0,4,4,172,0,4,4);region(g,0,h-4,4,4,0,162,4,4);region(g,w-4,h-4,4,4,172,162,4,4);
        region(g,4,0,w-8,4,4,0,168,4);region(g,4,h-4,w-8,4,4,162,168,4);region(g,0,4,4,h-8,0,4,4,70);region(g,w-4,4,4,h-8,172,4,4,70);
        for(var slot:menu.slots)region(g,slot.x-1,slot.y-1,18,18,14,46,18,18);
        g.fill(91,41,151,45,0xff8b8b8b);for(int i=0;i<7;i++)g.fill(151+i,37+i,152+i,49-i,0xff8b8b8b);
        var choices=menu.choices();offset=Math.clamp(offset,0,Math.max(0,choices.size()-4));
        for(int row=0;row<4&&offset+row<choices.size();row++) {
            int index=offset+row,y=62+row*15;boolean selected=menu.selection()==index;
            g.blitSprite(RenderPipelines.GUI_TEXTURED,Identifier.withDefaultNamespace("container/enchanting_table/enchantment_slot"),11,y,151,14);
            if(selected||inside(mx-leftPos,my-topPos,11,y,151,14))g.fill(12,y+1,161,y+13,selected?0x604d915c:0x30fff2c0);
            clipped(g,Component.literal(label(choices.get(index)).getString()),15,y+3,142,0xff40382b);
        }
        if(choices.size()>4){g.fill(165,62,167,121,0xff817561);int y=62+offset*47/Math.max(1,choices.size()-4);g.fill(165,y,167,y+12,0xffdfd4b8);}
        g.pose().popMatrix();
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my) {
        clipped(g,title,8,6,214,0xff40382b);g.text(font,playerInventoryTitle,DisenchantMenu.INVENTORY_X,126,0xff40382b,false);
        if(menu.choices().isEmpty())g.textWithWordWrap(font,tr(menu.getSlot(0).hasItem()?"none":"select"),13,66,146,0xff756952,false);
        else if(menu.selection()<0)g.textWithWordWrap(font,tr("choose"),174,65,46,0xff756952,false);
        if(menu.cost()>0)g.textWithWordWrap(font,tr("cost",menu.cost()),174,65,48,minecraft.player.getAbilities().instabuild||minecraft.player.experienceLevel>=menu.cost()?0xff3f682c:0xffa03324,false);
    }
    @Override protected void extractTooltip(GuiGraphicsExtractor g,int mx,int my) {
        super.extractTooltip(g,mx,my);int x=mx-leftPos,y=my-topPos;var choices=menu.choices();
        if(inside(x,y,11,62,151,60)){int index=offset+(y-62)/15;if(index<choices.size()){
            var c=choices.get(index);var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();lines.addAll(font.split(label(c),220));lines.addAll(font.split(tr("result",Enchantment.getFullname(c.enchantment(),c.outputLevel())),220));lines.addAll(font.split(tr("cost",c.cost()),220));g.setTooltipForNextFrame(font,lines,mx,my);}}
        for(int i=0;i<2;i++)if(menu.getSlot(i).getItem().isEmpty()&&inside(x,y,new int[]{16,48}[i],34,16,16))g.setTooltipForNextFrame(font,font.split(tr(i==0?"equipment":"book"),200),mx,my);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        double x=event.x()-leftPos,y=event.y()-topPos;
        if(event.button()==0&&inside(x,y,11,62,151,60)){int index=offset+(int)(y-62)/15;if(index<menu.choices().size())minecraft.gameMode.handleInventoryButtonClick(menu.containerId,index);return true;}
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){if(inside(x-leftPos,y-topPos,11,62,157,60)&&sy!=0){offset=Math.clamp(offset+(sy<0?1:-1),0,Math.max(0,menu.choices().size()-4));return true;}return super.mouseScrolled(x,y,sx,sy);}
}
