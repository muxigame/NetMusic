package net.muxigame.musiccompat.burn;
import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.*;import net.minecraft.client.gui.screens.Screen;import net.minecraft.network.chat.Component;import java.util.function.Consumer;
/** Compatibility entry: one explicit user choice, no manual song or author fields. */
public final class BurnPermissionScreen extends Screen {
 private final Screen parent;private final Consumer<Boolean> saved;private boolean value;
 public BurnPermissionScreen(Screen parent,boolean initial,Consumer<Boolean> saved){super(Component.literal("刻录确认"));this.parent=parent;this.value=initial;this.saved=saved;}
 @Override protected void init(){int x=width/2-70,y=height/2-20;addRenderableWidget(Checkbox.builder(Component.literal("确认刻录"),font).pos(x,y).selected(value).onValueChange((box,next)->{value=next;saved.accept(next);}).build());addRenderableWidget(Button.builder(Component.literal("返回"),button->onClose()).bounds(x,y+30,140,20).build());}
 @Override public void render(GuiGraphics graphics,int x,int y,float delta){renderBackground(graphics,x,y,delta);super.render(graphics,x,y,delta);graphics.drawCenteredString(font,title,width/2,height/2-42,0xffffff);}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
