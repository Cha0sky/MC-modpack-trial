package com.evolvingblade.client;

import java.util.List;
import com.evolvingblade.network.EBPayloads.StateSnapshot;
import com.evolvingblade.network.EBPayloads.FolioOpened;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DashboardScreen extends Screen {
    private enum Tab { GROWTH, FORMS, SKILLS }

    private Tab tab = Tab.GROWTH;
    private int preview, scroll, maxScroll;

    public DashboardScreen() {
        super(Component.translatable("screen.evolvingblade.dashboard.title"));
        StateSnapshot snapshot = EBClientState.snapshot();
        preview = snapshot == null ? 0 : snapshot.stage();
    }

    @Override
    public void added() {
        super.added();
        PacketDistributor.sendToServer(FolioOpened.INSTANCE);
    }

    @Override
    protected void init() {
        var p = BladeFolio.page(width, height);
        int gap = 3, buttonWidth = (p.width() - 20 - gap * 3) / 4;
        int x = p.x() + 10;
        for (Tab target : Tab.values()) {
            var button = Button.builder(BladeFolio.title(target.name().toLowerCase(java.util.Locale.ROOT)), b -> {
                tab = target;
                scroll = 0;
                rebuildWidgets();
            }).bounds(x, p.footer(), buttonWidth, 19).build();
            button.active = target != tab;
            addRenderableWidget(button);
            x += buttonWidth + gap;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x, p.footer(), buttonWidth, 19).build());
        if (tab == Tab.FORMS) {
            int cell = (p.width() - 20) / StateSnapshot.STAGES;
            for (int i = 0; i < StateSnapshot.STAGES; i++) {
                final int stage = i;
                var button = Button.builder(Component.literal(Integer.toString(i)), b -> {
                    preview = stage;
                    rebuildWidgets();
                }).bounds(p.x()+10+i*cell, p.footer()-28, cell-2, 18).build();
                button.active = preview != stage;
                addRenderableWidget(button);
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float tick) {
        BladeFolio.frame(g, BladeFolio.page(width,height), width, height);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float tick) {
        super.render(g,mx,my,tick);
        var p = BladeFolio.page(width,height);
        int x = p.x()+11, top = p.y()+32;
        g.drawString(font,title,x,p.y()+9,BladeFolio.GOLD,false);
        var data = EBClientState.snapshot();
        if (data == null) {
            g.drawString(font, Component.translatable("screen.evolvingblade.dashboard.waiting"), x, top, BladeFolio.TEXT, false);
            return;
        }
        Component route = Component.translatable("evolvingblade.mode."+data.modeId());
        g.drawString(font,route,p.right()-11-font.width(route),p.y()+9,BladeFolio.QUIET,false);
        if (tab == Tab.SKILLS) {
            drawSkills(g, p, data);
            return;
        }

        boolean gallery = tab == Tab.FORMS;
        int stage = gallery ? preview : data.stage();
        int bottom = p.footer() - (gallery ? 32 : 8);
        int dial = Math.max(28, Math.min(Math.min(p.width()/3, bottom-top-2), 114));
        BladeFolio.sword(g, stage, x, top, dial);
        int tx = x+dial+10, w = p.right()-tx-11;
        var stats = gallery ? List.<FormattedCharSequence>of() : BladeFolio.wrap(font,ModeCardText.stats(data),w);
        maxScroll = Math.max(0,top + (gallery ? 78 : 83 + stats.size()*11) - bottom);
        scroll = Math.min(scroll,maxScroll);
        g.enableScissor(tx,top,p.right()-8,bottom);
        top -= scroll;
        g.drawString(font, Component.translatable("screen.evolvingblade.folio.stage",
                stage, Component.translatable("evolvingblade.stage."+stage)), tx,top+2,BladeFolio.GOLD,false);
        if (gallery) {
            g.drawString(font,BladeFolio.title(stage <= data.stage() ? "unlocked" : "upcoming"),tx,top+16,BladeFolio.QUIET,false);
            BladeFolio.meter(g,font,tx,top+37,w,BladeFolio.title("points"),data.growthPoints(),
                    data.thresholdsPoints().get(stage),BladeFolio.AQUA);
            BladeFolio.meter(g,font,tx,top+60,w,BladeFolio.title("bosses"),data.bossKills(),
                    data.thresholdsBosses().get(stage),BladeFolio.GOLD);
        } else {
            int next = Math.min(stage+1,StateSnapshot.STAGES-1);
            Component goal = stage == next ? BladeFolio.title("complete") :
                    Component.translatable("screen.evolvingblade.folio.toward",Component.translatable("evolvingblade.stage."+next));
            g.drawString(font,goal,tx,top+17,BladeFolio.TEXT,false);
            BladeFolio.meter(g,font,tx,top+36,w,BladeFolio.title("points"),data.growthPoints(),
                    data.thresholdsPoints().get(next),BladeFolio.AQUA);
            BladeFolio.meter(g,font,tx,top+58,w,BladeFolio.title("bosses"),data.bossKills(),
                    data.thresholdsBosses().get(next),BladeFolio.GOLD);
            int y = top+83;
            for (FormattedCharSequence line : stats) {
                g.drawString(font,line,tx,y,BladeFolio.QUIET,false);
                y += 11;
            }
        }
        g.disableScissor();
    }

    private void drawSkills(GuiGraphics g, BladeFolio.Page p, StateSnapshot data) {
        int x = p.x()+12, y = p.y()+32, bottom = p.footer()-9;
        var lines = BladeFolio.wrap(font,ModeCardText.skills(data),p.width()-32);
        int visible = Math.max(1,(bottom-y)/12);
        maxScroll = Math.max(0,lines.size()-visible);
        scroll = Math.min(scroll,maxScroll);
        for (int i = scroll; i < Math.min(lines.size(),scroll+visible); i++) {
            g.drawString(font,lines.get(i),x,y+(i-scroll)*12,BladeFolio.TEXT,false);
        }
        if (maxScroll > 0) {
            int track = bottom-y, handle = Math.max(8,track*visible/lines.size());
            int offset = (track-handle)*scroll/maxScroll;
            g.fill(p.right()-10,y,p.right()-8,bottom,BladeFolio.RIM);
            g.fill(p.right()-10,y+offset,p.right()-8,y+offset+handle,BladeFolio.GOLD);
        }
    }

    @Override
    public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        if (maxScroll == 0) return super.mouseScrolled(mx,my,horizontal,vertical);
        int step = tab == Tab.SKILLS ? 2 : 12;
        scroll = Math.clamp(scroll - (int)Math.signum(vertical)*step,0,maxScroll);
        return true;
    }
}
