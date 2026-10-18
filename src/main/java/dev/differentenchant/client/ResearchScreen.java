package dev.differentenchant.client;

import dev.differentenchant.research.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Paper/wood workstation. All names, known results and available records originate on the server. */
public final class ResearchScreen extends AbstractContainerScreen<ResearchMenu> {
    private static final int TEXT=0xff40382b,MUTED=0xff756952;
    private static final Identifier PANEL=Identifier.withDefaultNamespace("textures/gui/container/enchanting_table.png");
    public ResearchScreen(ResearchMenu menu,Inventory inventory,Component title){super(menu,inventory,title,ResearchMenu.WIDTH,ResearchMenu.HEIGHT);}
    private Component tr(String key,Object... args){return Component.translatable("research.differentenchant."+key,args);}
    private static Component numeral(int n){return Component.translatableWithFallback("enchantment.level."+n,Integer.toString(n));}
    private void region(GuiGraphicsExtractor g,int x,int y,int w,int h,int u,int v,int sw,int sh){g.blit(RenderPipelines.GUI_TEXTURED,PANEL,x,y,u,v,w,h,sw,sh,256,256);}
    private void clipped(GuiGraphicsExtractor g,Component text,int x,int y,int width,int color){
        if(font.width(text)>width)text=Component.literal(font.plainSubstrByWidth(text.getString(),Math.max(0,width-font.width("…")))+"…");g.text(font,text,x,y,color,false);
    }
    private Component book(ResearchPolicy.Book book){return minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(book.id()))
        .<Component>map(h->h.value().description().copy().append(" ").append(numeral(book.level()))).orElseGet(()->Component.literal(book.id()+" "+book.level()));}
    private Component result(ResearchPolicy.Result result){return result.scroll()?Component.translatable("item.differentenchant.lost_scroll"):book(new ResearchPolicy.Book(result.id(),result.level()));}
    private Component pair(String key){var recipe=ResearchPolicy.Recipe.parse(key);return book(recipe.a()).copy().append(" + ").append(book(recipe.b()));}
    private boolean inside(double x,double y,int rx,int ry,int w,int h){return x>=rx&&x<rx+w&&y>=ry&&y<ry+h;}
    private void request(int action,int offset){var state=menu.preview();if(state==null)return;menu.awaitResponse();net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new ResearchActionPayload(menu.containerId,state.revision(),action,offset));}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float partial) {
        super.extractBackground(g,mx,my,partial);g.pose().pushMatrix();g.pose().translate(leftPos,topPos);
        int w=ResearchMenu.WIDTH,h=ResearchMenu.HEIGHT;
        region(g,4,4,w-8,h-8,8,8,1,1);region(g,0,0,4,4,0,0,4,4);region(g,w-4,0,4,4,172,0,4,4);
        region(g,0,h-4,4,4,0,162,4,4);region(g,w-4,h-4,4,4,172,162,4,4);
        region(g,4,0,w-8,4,4,0,168,4);region(g,4,h-4,w-8,4,4,162,168,4);region(g,0,4,4,h-8,0,4,4,70);region(g,w-4,4,4,h-8,172,4,4,70);
        for(var slot:menu.slots)if(slot.isActive())region(g,slot.x-1,slot.y-1,18,18,14,46,18,18);
        var state=menu.preview();boolean records=menu.recordsOpen();
        g.fill(records?88:12,30,records?174:82,31,0xff8d8066);
        if(records) {
            g.fill(10,34,276,112,0xff8a7a60);
            if(state==null)g.text(font,tr("sync"),16,47,MUTED,false);
            else if(state.rows().isEmpty())g.textWithWordWrap(font,tr("empty"),16,48,250,TEXT,false);
            else for(int i=0;i<state.rows().size();i++) {
                var row=state.rows().get(i);int y=35+i*25;
                g.blitSprite(RenderPipelines.GUI_TEXTURED,Identifier.withDefaultNamespace("container/enchanting_table/enchantment_slot"),11,y,263,24);
                if(inside(mx-leftPos,my-topPos,11,y,263,24))g.fill(12,y+1,273,y+23,0x30fff2c0);
                clipped(g,pair(row.recipe()),16,y+3,251,TEXT);
                clipped(g,result(row.result()).copy().append(" · ").append(tr("rarity",numeral(row.result().rarity()))),16,y+13,251,0xff42613c);
            }
            if(state!=null&&state.total()>3){g.fill(278,35,280,110,0xff817561);int y=35+state.offset()*63/Math.max(1,state.total()-3);g.fill(278,y,280,y+12,0xffdfd4b8);}
        } else {
            g.fill(122,34,238,82,0xffae9d7b);g.fill(123,35,237,81,0xffe7dbbb);
            g.fill(112,55,120,57,0xff8d7856);
            if(state!=null&&state.known()!=null) {
                var lines=font.split(result(state.known()),108);for(int i=0;i<Math.min(2,lines.size());i++)g.text(font,lines.get(i),127,40+i*10,TEXT,false);
                clipped(g,tr("rarity",numeral(state.known().rarity())),127,66,108,MUTED);
            } else g.centeredText(font,tr(state==null?"sync":"unknown"),180,53,TEXT);
            boolean enabled=state!=null&&state.status()==0;
            String suffix=enabled?(inside(mx-leftPos,my-topPos,184,96,92,20)?"_highlighted":""):"_disabled";
            g.blitSprite(RenderPipelines.GUI_TEXTURED,Identifier.withDefaultNamespace("widget/button"+suffix),184,96,92,20);
            g.centeredText(font,tr("research"),230,102,enabled?0xffffffff:0xffa0a0a0);
            if(state!=null&&state.grade()>0)clipped(g,tr("grade",numeral(state.grade())),12,76,106,MUTED);
            var lines=font.split(tr(state==null?"sync":"status."+state.status()),166);
            for(int i=0;i<Math.min(2,lines.size());i++)g.text(font,lines.get(i),12,94+i*10,state!=null&&state.status()==0?MUTED:0xff8d4438,false);
        }
        g.pose().popMatrix();
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my) {
        clipped(g,title,8,6,270,TEXT);g.text(font,tr("research"),16,20,TEXT,false);g.text(font,tr("records"),92,20,TEXT,false);
        var state=menu.preview();
        if(!menu.recordsOpen()){clipped(g,tr("books"),24,36,60,MUTED);clipped(g,tr("core"),88,36,32,MUTED);}
        else if(state!=null)clipped(g,tr("record_count",state.total()),12,113,260,MUTED);
        g.text(font,playerInventoryTitle,ResearchMenu.INVENTORY_X,122,TEXT,false);
    }
    @Override protected void extractTooltip(GuiGraphicsExtractor g,int mx,int my) {
        super.extractTooltip(g,mx,my);int x=mx-leftPos,y=my-topPos;var state=menu.preview();if(state==null)return;
        var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();ResearchPolicy.Result result=null;
        if(menu.recordsOpen()) {
            int row=(y-35)/25;if(y>=35&&y<110&&x>=11&&x<274&&row<state.rows().size()) {
                var entry=state.rows().get(row);lines.addAll(font.split(pair(entry.recipe()),220));result=entry.result();
            }
        } else if(inside(x,y,122,34,116,48)) {
            if(state.known()==null)lines.add(tr("unknown").getVisualOrderText());else result=state.known();
        } else if(inside(x,y,184,96,92,20)) {
            lines.addAll(font.split(tr("success_rule"),220));lines.addAll(font.split(tr("scroll_rule"),220));
        }
        if(result!=null){lines.addAll(font.split(result(result),220));lines.addAll(font.split(tr("rarity",numeral(result.rarity())),220));
            String description="enchantment."+result.id().replace(':','.')+".desc";
            if(!result.scroll()&&net.minecraft.client.resources.language.I18n.exists(description))lines.addAll(font.split(Component.translatable(description),220));
            lines.addAll(font.split(tr(result.scroll()?"scroll_rule":"success_rule"),220));}
        if(!lines.isEmpty())g.setTooltipForNextFrame(font,lines,mx,my);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        double x=event.x()-leftPos,y=event.y()-topPos;
        if(event.button()==0) {
            if(inside(x,y,12,18,70,14)){request(2,0);return true;}
            if(inside(x,y,88,18,90,14)){request(1,0);return true;}
            if(!menu.recordsOpen()&&inside(x,y,184,96,92,20)){var state=menu.preview();if(state!=null&&state.status()==0)request(0,0);return true;}
        }
        if(menu.recordsOpen()&&inside(x,y,10,34,270,78))return true;
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy) {
        var state=menu.preview();if(menu.recordsOpen()&&state!=null&&inside(x-leftPos,y-topPos,10,34,270,78)&&sy!=0){request(1,Math.clamp(state.offset()+(sy<0?1:-1),0,Math.max(0,state.total()-3)));return true;}
        return super.mouseScrolled(x,y,sx,sy);
    }
}
