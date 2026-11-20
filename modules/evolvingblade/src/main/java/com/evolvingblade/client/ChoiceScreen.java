package com.evolvingblade.client;

import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.network.EBPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ChoiceScreen extends Screen {
    private GrowthMode selected;
    private Button confirmButton;
    private int scroll, maxScroll;

    public ChoiceScreen() {
        super(Component.translatable("screen.evolvingblade.choice.title"));
    }

    @Override
    protected void init() {
        var p = BladeFolio.page(width,height);
        int cell = (p.width()-18)/3;
        for (GrowthMode mode : GrowthMode.values()) {
            var button = Button.builder(Component.translatable("evolvingblade.mode."+mode.id()), b -> {
                selected = mode;
                scroll = 0;
                rebuildWidgets();
            }).bounds(p.x()+9+cell*mode.ordinal(),p.y()+30,cell-3,19).build();
            button.active = selected != mode;
            addRenderableWidget(button);
        }
        int w = Math.min(110,(p.width()-30)/2);
        confirmButton = addRenderableWidget(Button.builder(Component.translatable("screen.evolvingblade.choice.confirm"),
                b -> confirm()).bounds(p.x()+p.width()/2-w-2,p.footer(),w,19).build());
        confirmButton.active = selected != null && EBClientState.snapshot() != null;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(p.x()+p.width()/2+2,p.footer(),w,19).build());
    }

    @Override
    public void renderBackground(GuiGraphics g,int mx,int my,float tick) {
        BladeFolio.frame(g,BladeFolio.page(width,height),width,height);
    }

    @Override
    public void render(GuiGraphics g,int mx,int my,float tick) {
        var data = EBClientState.snapshot();
        confirmButton.active = selected != null && data != null;
        super.render(g,mx,my,tick);
        var p = BladeFolio.page(width,height);
        int x = p.x()+11, top = p.y()+57;
        int dial = Math.max(28,Math.min(100,Math.min(p.width()/4,p.footer()-top-10)));
        g.drawString(font,title,x,p.y()+9,BladeFolio.GOLD,false);
        BladeFolio.sword(g, selected == null ? 0 : 7,x,top,dial);
        int tx=x+dial+10, right=p.right()-10, w=right-tx;
        g.enableScissor(tx,top,right,p.footer()-5);
        if (selected == null || data == null) {
            Component note = Component.translatable(data == null ? "screen.evolvingblade.choice.waiting" : "screen.evolvingblade.choice.hint");
            g.drawWordWrap(font,note,tx,top+4,w,BladeFolio.TEXT);
        } else {
            Component desc=Component.translatable("evolvingblade.mode."+selected.id()+".desc");
            maxScroll=Math.max(0,top+2+font.split(desc,w).size()*11+19+56-(p.footer()-5));
            scroll=Math.min(scroll,maxScroll);
            int y=top+2-scroll;
            for (var line:font.split(desc,w)) {
                g.drawString(font,line,tx,y,BladeFolio.TEXT,false);
                y+=11;
            }
            g.drawString(font,Component.translatable("screen.evolvingblade.choice.thresholds"),tx,y+4,BladeFolio.GOLD,false);
            y+=19;
            int col=w/2;
            for (int stage=0;stage<EBPayloads.StateSnapshot.STAGES;stage++) {
                int cx=tx+(stage/4)*col, cy=y+(stage%4)*14;
                Component name=Component.translatable("evolvingblade.stage."+stage);
                String values=data.stagePoints(selected,stage)+"/"+data.stageBosses(selected,stage);
                int allowed=col-font.width(values)-8;
                g.drawString(font,font.width(name)<=allowed ? name : Component.literal(Integer.toString(stage)),
                        cx,cy,BladeFolio.QUIET,false);
                g.drawString(font,values,cx+col-font.width(values)-4,cy,BladeFolio.TEXT,false);
            }
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        if (selected == null || maxScroll == 0) return super.mouseScrolled(mx,my,horizontal,vertical);
        scroll=Math.clamp(scroll-(int)Math.signum(vertical)*12,0,maxScroll);
        return true;
    }

    private void confirm() {
        if (this.selected == null || this.minecraft == null) {
            return;
        }
        final GrowthMode target = this.selected;
        Component warning = Component.translatable("screen.evolvingblade.choice.lock_warning",
                Component.translatable("evolvingblade.mode." + target.id()));
        this.minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                answer -> {
                    if (answer) {
                        EBClientState.dismiss();
                        PacketDistributor.sendToServer(new EBPayloads.ChooseMode(target));
                        this.minecraft.setScreen(null);
                    } else {
                        this.minecraft.setScreen(this);
                    }
                },
                Component.translatable("screen.evolvingblade.choice.confirm_title"),
                warning,
                Component.translatable("screen.evolvingblade.choice.confirm_yes"),
                Component.translatable("gui.cancel")));
    }

    @Override
    public void onClose() {
        EBClientState.dismiss();
        super.onClose();
    }
}
