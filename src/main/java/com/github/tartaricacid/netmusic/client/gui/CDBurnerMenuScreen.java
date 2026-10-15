package com.github.tartaricacid.netmusic.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.muxigame.musiccompat.burn.BurnCoordinator;
import net.muxigame.musiccompat.burn.BurnScreenRuntime;
import com.github.tartaricacid.netmusic.NetMusic;
import com.github.tartaricacid.netmusic.config.MusicListManage;
import com.github.tartaricacid.netmusic.inventory.CDBurnerMenu;
import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import com.github.tartaricacid.netmusic.network.NetworkHandler;
import com.github.tartaricacid.netmusic.network.message.SetMusicIDMessage;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.anti_ad.mc.ipn.api.IPNIgnore;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@IPNIgnore
public class CDBurnerMenuScreen extends AbstractContainerScreen<CDBurnerMenu> {
    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath(NetMusic.MOD_ID, "textures/gui/cd_burner.png");
    private static final Pattern ID_REG = Pattern.compile("^\\d{4,}$");
    private static final Pattern DJ_ID_REG = Pattern.compile("^dj/(\\d+)$");
    private static final Pattern URL_1_REG = Pattern.compile("^https://music\\.163\\.com/song\\?id=(\\d+).*$");
    private static final Pattern URL_2_REG = Pattern.compile("^https://music\\.163\\.com/#/song\\?id=(\\d+).*$");
    private static final Pattern DJ_URL_1_REG = Pattern.compile("^https://music\\.163\\.com/dj\\?id=(\\d+).*$");
    private static final Pattern DJ_URL_2_REG = Pattern.compile("^https://music\\.163\\.com/#/dj\\?id=(\\d+).*$");
    private EditBox textField;
    private Checkbox readOnlyButton;
    private Component tips = Component.empty();

    public CDBurnerMenuScreen(CDBurnerMenu screenContainer, Inventory inv, Component titleIn) {
        super(screenContainer, inv, titleIn);
        this.imageHeight = 176;
    }

    @Override
    protected void init() {
        muxiBurn$preserve();
        super.init();

        String perText = "";
        boolean focus = false;
        if (textField != null) {
            perText = textField.getValue();
            focus = textField.isFocused();
        }
        textField = new EditBox(getMinecraft().font, leftPos + 12, topPos + 18, 132, 16, Component.literal("Music ID Box")) {
            @Override
            public void insertText(String text) {
                Matcher matcher1 = URL_1_REG.matcher(text);
                if (matcher1.find()) {
                    String group = matcher1.group(1);
                    super.insertText(group);
                    return;
                }

                Matcher matcher2 = URL_2_REG.matcher(text);
                if (matcher2.find()) {
                    String group = matcher2.group(1);
                    super.insertText(group);
                    return;
                }

                Matcher matcher3 = DJ_URL_1_REG.matcher(text);
                if (matcher3.find()) {
                    String group = matcher3.group(1);
                    super.insertText("dj/" + group);
                    return;
                }

                Matcher matcher4 = DJ_URL_2_REG.matcher(text);
                if (matcher4.find()) {
                    String group = matcher4.group(1);
                    super.insertText("dj/" + group);
                    return;
                }

                super.insertText(text);
            }
        };
        textField.setValue(perText);
        textField.setBordered(false);
        textField.setMaxLength(19);
        textField.setTextColor(0xF3EFE0);
        textField.setFocused(focus);
        textField.moveCursorToEnd(false);
        this.addWidget(this.textField);

        this.readOnlyButton = Checkbox.builder(Component.translatable("gui.netmusic.cd_burner.read_only"), font).pos(leftPos + 66, topPos + 34).maxWidth(80).selected(false).build();
        this.addRenderableWidget(this.readOnlyButton);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusic.cd_burner.craft"), (b) -> muxiBurn$link())
                .pos(leftPos + 7, topPos + 35).size(55, 18).build());
        muxiBurn$init();
    }

    /** Kept as an addon/QA ABI; the actual widget calls the verified path directly. */
    private void handleCraftButton() {
        muxiBurn$link();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int x, int y) {
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int x, int y) {
        int posX = this.leftPos;
        int posY = (this.height - this.imageHeight) / 2;
        graphics.blit(BG, posX, posY, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float partialTicks) {
        super.render(graphics, x, y, partialTicks);
        textField.render(graphics, x, y, partialTicks);
        if (StringUtils.isBlank(textField.getValue()) && !textField.isFocused()) {
            graphics.drawString(font, Component.translatable("gui.netmusic.cd_burner.id.tips").withStyle(ChatFormatting.ITALIC), this.leftPos + 12, this.topPos + 18, ChatFormatting.GRAY.getColor(), false);
        }
        muxiBurn$status(graphics, font, tips, this.leftPos + 8, this.topPos + 57, 135, 0xCF0000);
        renderTooltip(graphics, x, y);
        muxiBurn$detail(graphics, x, y, partialTicks);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String value = this.textField.getValue();
        super.resize(minecraft, width, height);
        this.textField.setValue(value);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.textField.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.textField);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        InputConstants.Key mouseKey = InputConstants.getKey(keyCode, scanCode);
        // 防止 E 键关闭界面
        if (this.getMinecraft().options.keyInventory.isActiveAndMatches(mouseKey) && textField.isFocused()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void insertText(String text, boolean overwrite) {
        if (overwrite) {
            this.textField.setValue(text);
        } else {
            this.textField.insertText(text);
        }
    }

    // Integrated verified burn UI: no injection into NetMusic classes.

    private BurnCoordinator muxiBurn$job;
    private Button muxiBurn$login;
    private Button muxiBurn$cancel;
    private final java.util.Map<Button,Boolean> muxiBurn$disabled=new java.util.IdentityHashMap<>();
    private String muxiBurn$restoreInput;
    private Boolean muxiBurn$restoreReadOnly;
    private BurnScreenRuntime.Session muxiBurn$session;
    private boolean muxiBurn$closed;
    private boolean muxiBurn$choosing;
    private long muxiBurn$screenGeneration;
    private Button muxiBurn$localButton;
    private Component muxiBurn$detail;
    private String muxiBurn$detailSummary="";
    private int muxiBurn$statusX,muxiBurn$statusY,muxiBurn$statusWidth;

    private void muxiBurn$preserve(){muxiBurn$restoreInput=textField==null?null:textField.getValue();muxiBurn$restoreReadOnly=readOnlyButton==null?null:readOnlyButton.selected();}
    private void muxiBurn$init() {
        // A resize/recovery reinitializes the same open menu. Keep its pending
        // native selection alive; removed() invalidates genuinely old menus.
        if(muxiBurn$closed||muxiBurn$screenGeneration==0){muxiBurn$screenGeneration++;muxiBurn$choosing=false;}
        muxiBurn$closed=false;
        muxiBurn$session=BurnScreenRuntime.session();
        if(muxiBurn$session==null)return;
        textField.setMaxLength(2048);if(muxiBurn$restoreInput!=null)textField.setValue(muxiBurn$restoreInput);muxiBurn$restoreInput=null;
        if(muxiBurn$restoreReadOnly!=null&&readOnlyButton.selected()!=muxiBurn$restoreReadOnly)readOnlyButton.onPress();muxiBurn$restoreReadOnly=null;
        addRenderableWidget(Checkbox.builder(Component.literal("确认"),font).pos(leftPos+8,topPos-24).maxWidth(52).selected(muxiBurn$session.permissionConfirmed(this)).onValueChange((box,value)->muxiBurn$session.confirmation(this,value)).build());
        var login=BurnScreenRuntime.burnerLogin(width,leftPos,topPos,imageWidth);
        muxiBurn$login=addRenderableWidget(Button.builder(Component.literal("登录"),button->muxiBurn$session.login(this,textField.getValue())).bounds(login.x(),login.y(),login.width(),20).build());
        if(muxiBurn$job==null) muxiBurn$job=muxiBurn$session.open(this,
            progress -> {if(!muxiBurn$closed){tips=BurnScreenRuntime.text(progress);muxiBurn$detailSummary=tips.getString();muxiBurn$detail=BurnScreenRuntime.detail(progress);}});
        if(muxiBurn$job.running()){
            boolean frozen=muxiBurn$session.readOnlySelected(this);
            if(readOnlyButton.selected()!=frozen)readOnlyButton.onPress();
            String input=muxiBurn$session.activeInput(this);if(!input.isBlank())textField.setValue(input);
        }
        muxiBurn$localButton=addRenderableWidget(Button.builder(Component.literal("本地刻录"),button -> {
            if(muxiBurn$choosing)return;
            if(!muxiBurn$session.permissionConfirmed(this)){tips=Component.literal("请确认刻录");return;}
            var original=this;
            long selectionGeneration=muxiBurn$screenGeneration;
            muxiBurn$choosing=true;button.active=false;
            try {
                boolean started=muxiBurn$session.chooseLocalOnce(selected -> Minecraft.getInstance().execute(() -> {
                    if(!BurnScreenRuntime.selectionCurrent(selectionGeneration,muxiBurn$screenGeneration,
                            muxiBurn$closed,Minecraft.getInstance().screen==original))return;
                    muxiBurn$choosing=false;if(muxiBurn$localButton!=null)muxiBurn$localButton.active=true;
                    if(selected==null){tips=Component.empty();return;}
                    muxiBurn$start(muxiBurn$session.local(this,selected));
                }));
                if(!started){muxiBurn$choosing=false;button.active=true;tips=Component.literal("文件选择器已打开");}
            } catch(RuntimeException unavailable) {
                muxiBurn$choosing=false;button.active=true;tips=Component.literal("文件选择器暂不可用");
            }
        }).bounds(leftPos+64,topPos-24,52,20).build());
        muxiBurn$localButton.active=!muxiBurn$choosing;
        muxiBurn$cancel=addRenderableWidget(Button.builder(Component.literal("取消刻录"),button -> muxiBurn$job.cancel())
            .bounds(leftPos+120,topPos-24,52,20).build());
    }
    private void muxiBurn$link() {
        // The original widget enters here directly, before third-party craft injectors.
        if(muxiBurn$session==null){tips=Component.literal("音乐服务尚未就绪");return;}
        if(muxiBurn$choosing){tips=Component.literal("请先完成文件选择");return;}
        if(!muxiBurn$session.prepareLink(this,textField.getValue()))return;
        if(!muxiBurn$session.permissionConfirmed(this)){tips=Component.literal("请确认刻录");return;}
        try {muxiBurn$start(muxiBurn$session.link(this,textField.getValue()));}
        catch(RuntimeException invalid){tips=Component.literal("音频链接无效");}
    }
    private void muxiBurn$start(BurnCoordinator.Source source) {
        muxiBurn$session.readOnly(this,readOnlyButton.selected());
        try {muxiBurn$job.start(source,muxiBurn$session.commit(this));}
        catch(IllegalStateException busy){tips=Component.literal("正在刻录，请等待或取消");}
    }
    @Override public void removed() {
        muxiBurn$closed=true;muxiBurn$screenGeneration++;muxiBurn$choosing=false;
        muxiBurn$job=null;if(muxiBurn$session!=null)muxiBurn$session.closed(this);super.removed();
    }
    /** Pinned upstream renders tips at left+8/top+57, width135, immediately
     * above the platform buttons at top+70. Replacing its wrap keeps one row.
     */
    private void muxiBurn$status(GuiGraphics graphics,Font currentFont,FormattedText text,int x,int y,int rowWidth,int color) {
        String full=text.getString();
        if(!full.equals(muxiBurn$detailSummary)){muxiBurn$detailSummary=full;muxiBurn$detail=Component.literal(full);}
        muxiBurn$statusX=x;muxiBurn$statusY=y;muxiBurn$statusWidth=rowWidth;
        graphics.drawString(currentFont,BurnScreenRuntime.singleLine(full,rowWidth,currentFont::width),x,y,color,false);
    }
    private void muxiBurn$detail(GuiGraphics graphics,int mouseX,int mouseY,float delta) {
        boolean busy=muxiBurn$job!=null&&muxiBurn$job.running();
        for(var child:children())if(child instanceof Button button&&button!=muxiBurn$cancel&&!BurnScreenRuntime.closeControl(button.getMessage().getString())){
            if(busy){muxiBurn$disabled.putIfAbsent(button,button.active);button.active=false;}
            else{Boolean prior=muxiBurn$disabled.remove(button);if(prior!=null)button.active=prior;}
        }
        readOnlyButton.active=!busy;textField.setEditable(!busy);
        if(muxiBurn$localButton!=null)muxiBurn$localButton.active=!busy&&!muxiBurn$choosing;
        if(muxiBurn$login!=null)muxiBurn$login.active=!muxiBurn$choosing&&(muxiBurn$job==null||!muxiBurn$job.running());
        if(muxiBurn$closed||muxiBurn$detail==null||muxiBurn$detail.getString().isBlank()
                ||mouseX<muxiBurn$statusX||mouseX>=muxiBurn$statusX+muxiBurn$statusWidth
                ||mouseY<muxiBurn$statusY||mouseY>=muxiBurn$statusY+font.lineHeight+2)return;
        int textWidth=Math.max(1,Math.min(184,width-24));
        var lines=font.split(muxiBurn$detail,textWidth);
        int contentWidth=0;for(var line:lines)contentWidth=Math.max(contentWidth,font.width(line));
        var box=BurnScreenRuntime.tooltipBox(width,height,leftPos,topPos-24,imageWidth,imageHeight+24,
                contentWidth+8,lines.size()*(font.lineHeight+1)+8);
        if(box==null)return;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0,0,400);
            graphics.fill(box.x(),box.y(),box.x()+box.width(),box.y()+box.height(),0xf0100010);
            graphics.fill(box.x(),box.y(),box.x()+box.width(),box.y()+1,0xffbacbdc);
            graphics.fill(box.x(),box.y()+box.height()-1,box.x()+box.width(),box.y()+box.height(),0xffbacbdc);
            int lineY=box.y()+4;
            for(var line:lines){graphics.drawString(font,line,box.x()+4,lineY,0xffeeeeee,false);lineY+=font.lineHeight+1;}
        } finally {graphics.pose().popPose();}
    }
}
