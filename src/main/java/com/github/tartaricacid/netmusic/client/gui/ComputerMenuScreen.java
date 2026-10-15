package com.github.tartaricacid.netmusic.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.FormattedText;
import net.muxigame.musiccompat.burn.BurnCoordinator;
import net.muxigame.musiccompat.burn.BurnScreenRuntime;
import com.github.tartaricacid.netmusic.NetMusic;
import com.github.tartaricacid.netmusic.inventory.ComputerMenu;
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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.regex.Pattern;

@IPNIgnore
public class ComputerMenuScreen extends AbstractContainerScreen<ComputerMenu> {
    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath(NetMusic.MOD_ID, "textures/gui/computer.png");
    private static final Pattern URL_HTTP_REG = Pattern.compile("(http|ftp|https)://[\\w\\-_]+(\\.[\\w\\-_]+)+([\\w\\-.,@?^=%&:/~+#]*[\\w\\-@?^=%&/~+#])?");
    private static final Pattern URL_FILE_REG = Pattern.compile("^[a-zA-Z]:\\\\(?:[^\\\\/:*?\"<>|\\r\\n]+\\\\)*[^\\\\/:*?\"<>|\\r\\n]*$");
    private static final Pattern TIME_REG = Pattern.compile("^\\d+$");
    private EditBox urlTextField;
    private EditBox nameTextField;
    private EditBox timeTextField;
    private Checkbox readOnlyButton;
    private Component tips = Component.empty();

    public ComputerMenuScreen(ComputerMenu screenContainer, Inventory inv, Component titleIn) {
        super(screenContainer, inv, titleIn);
        this.imageHeight = 216;
    }

    @Override
    protected void init() {
        muxiBurn$preserveUrl();
        super.init();
        this.initUrlEditBox();
        this.initNameEditBox();
        this.initTimeEditBox();
        this.readOnlyButton = Checkbox.builder(Component.translatable("gui.netmusic.cd_burner.read_only"), font).pos(leftPos + 58, topPos + 55).maxWidth(80).selected(false).build();
        this.addRenderableWidget(this.readOnlyButton);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusic.cd_burner.craft"), (b) -> muxiBurn$link())
                .pos(leftPos + 7, topPos + 78).size(135, 18).build());
        muxiBurn$init();
    }

    private void initUrlEditBox() {
        String perText = "";
        boolean focus = false;
        if (urlTextField != null) {
            perText = urlTextField.getValue();
            focus = urlTextField.isFocused();
        }
        urlTextField = new EditBox(getMinecraft().font, leftPos + 10, topPos + 18, 120, 16, Component.literal("Music URL Box"));
        urlTextField.setValue(perText);
        urlTextField.setBordered(false);
        urlTextField.setMaxLength(32500);
        urlTextField.setTextColor(0xF3EFE0);
        urlTextField.setFocused(focus);
        urlTextField.moveCursorToEnd(false);
        this.addWidget(this.urlTextField);
    }

    private void initNameEditBox() {
        String perText = "";
        boolean focus = false;
        if (nameTextField != null) {
            perText = nameTextField.getValue();
            focus = nameTextField.isFocused();
        }
        nameTextField = new EditBox(getMinecraft().font, leftPos + 10, topPos + 39, 120, 16, Component.literal("Music Name Box"));
        nameTextField.setValue(perText);
        nameTextField.setBordered(false);
        nameTextField.setMaxLength(256);
        nameTextField.setTextColor(0xF3EFE0);
        nameTextField.setFocused(focus);
        nameTextField.moveCursorToEnd(false);
        this.addWidget(this.nameTextField);
    }

    private void initTimeEditBox() {
        String perText = "";
        boolean focus = false;
        if (timeTextField != null) {
            perText = timeTextField.getValue();
            focus = timeTextField.isFocused();
        }
        timeTextField = new EditBox(getMinecraft().font, leftPos + 10, topPos + 61, 40, 16, Component.literal("Music Time Box"));
        timeTextField.setValue(perText);
        timeTextField.setBordered(false);
        timeTextField.setMaxLength(5);
        timeTextField.setTextColor(0xF3EFE0);
        timeTextField.setFocused(focus);
        timeTextField.moveCursorToEnd(false);
        this.addWidget(this.timeTextField);
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
        urlTextField.render(graphics, x, y, partialTicks);
        nameTextField.render(graphics, x, y, partialTicks);
        timeTextField.render(graphics, x, y, partialTicks);
        if (StringUtils.isBlank(urlTextField.getValue()) && !urlTextField.isFocused()) {
            graphics.drawString(font, Component.translatable("gui.netmusic.computer.url.tips").withStyle(ChatFormatting.ITALIC), this.leftPos + 12, this.topPos + 18, ChatFormatting.GRAY.getColor(), false);
        }
        if (StringUtils.isBlank(nameTextField.getValue()) && !nameTextField.isFocused()) {
            graphics.drawString(font, Component.translatable("gui.netmusic.computer.name.tips").withStyle(ChatFormatting.ITALIC), this.leftPos + 12, this.topPos + 39, ChatFormatting.GRAY.getColor(), false);
        }
        if (StringUtils.isBlank(timeTextField.getValue()) && !timeTextField.isFocused()) {
            graphics.drawString(font, Component.translatable("gui.netmusic.computer.time.tips").withStyle(ChatFormatting.ITALIC), this.leftPos + 11, this.topPos + 61, ChatFormatting.GRAY.getColor(), false);
        }
        muxiBurn$status(graphics, font, tips, this.leftPos + 8, this.topPos + 100, 162, 0xCF0000);
        renderTooltip(graphics, x, y);
        muxiBurn$detail(graphics, x, y, partialTicks);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String urlValue = this.urlTextField.getValue();
        String nameValue = this.nameTextField.getValue();
        String timeValue = this.timeTextField.getValue();
        super.resize(minecraft, width, height);
        this.urlTextField.setValue(urlValue);
        this.nameTextField.setValue(nameValue);
        this.timeTextField.setValue(timeValue);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.urlTextField.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.urlTextField);
            return true;
        }
        if (this.nameTextField.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.nameTextField);
            return true;
        }
        if (this.timeTextField.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.timeTextField);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        InputConstants.Key mouseKey = InputConstants.getKey(keyCode, scanCode);
        // 防止 E 键关闭界面
        if (this.getMinecraft().options.keyInventory.isActiveAndMatches(mouseKey)) {
            if (urlTextField.isFocused() || nameTextField.isFocused() || timeTextField.isFocused()) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void insertText(String text, boolean overwrite) {
        if (overwrite) {
            this.urlTextField.setValue(text);
            this.nameTextField.setValue(text);
            this.timeTextField.setValue(text);
        } else {
            this.urlTextField.insertText(text);
            this.nameTextField.insertText(text);
            this.timeTextField.insertText(text);
        }
    }

    // Integrated verified burn UI: no injection into NetMusic classes.

    private BurnCoordinator muxiBurn$job;
    private Button muxiBurn$login;
    private Button muxiBurn$cancel;
    private CycleButton<String> muxiBurn$source;
    private final java.util.Map<Button,Boolean> muxiBurn$disabled=new java.util.IdentityHashMap<>();
    private String muxiBurn$restoreUrl;
    private Boolean muxiBurn$restoreReadOnly;
    private BurnScreenRuntime.Session muxiBurn$session;
    private boolean muxiBurn$closed;
    private boolean muxiBurn$choosing;
    private long muxiBurn$screenGeneration;
    private Button muxiBurn$localButton;
    private BurnScreenRuntime.TipBox muxiBurn$occupied;
    private Component muxiBurn$detail;
    private String muxiBurn$detailSummary="";
    private int muxiBurn$statusX,muxiBurn$statusY,muxiBurn$statusWidth;

    private void muxiBurn$preserveUrl() {
        muxiBurn$restoreUrl=urlTextField==null?null:urlTextField.getValue();
        muxiBurn$restoreReadOnly=readOnlyButton==null?null:readOnlyButton.selected();
    }

    private void muxiBurn$init() {
        // A resize/recovery reinitializes the same open menu. Keep its pending
        // native selection alive; removed() invalidates genuinely old menus.
        if(muxiBurn$closed||muxiBurn$screenGeneration==0){muxiBurn$screenGeneration++;muxiBurn$choosing=false;}
        muxiBurn$closed=false;
        muxiBurn$session=BurnScreenRuntime.session();
        if(muxiBurn$session==null)return;
        var controls=BurnScreenRuntime.computerControls(width,height,leftPos,topPos,imageWidth,imageHeight);
        muxiBurn$occupied=controls.occupied();
        // Upstream EditBox defaults to 32: preserve full platform URL and local URI.
        urlTextField.setMaxLength(2048);
        if(muxiBurn$restoreUrl!=null)urlTextField.setValue(muxiBurn$restoreUrl);
        muxiBurn$restoreUrl=null;
        if(muxiBurn$restoreReadOnly!=null&&readOnlyButton.selected()!=muxiBurn$restoreReadOnly)readOnlyButton.onPress();muxiBurn$restoreReadOnly=null;
        nameTextField.setMaxLength(120);nameTextField.setEditable(false);nameTextField.setHint(Component.literal("自动获取歌名"));
        String savedTitle=muxiBurn$session.title(this);
        if(!savedTitle.isBlank())nameTextField.setValue(savedTitle);
        timeTextField.setFilter(value->true);timeTextField.setValue("自动");timeTextField.setEditable(false);
        addRenderableWidget(Checkbox.builder(Component.literal("确认"),font).pos(controls.permission().x(),controls.permission().y()).maxWidth(controls.permission().width()).selected(muxiBurn$session.permissionConfirmed(this)).onValueChange((box,value)->muxiBurn$session.confirmation(this,value)).build());
        muxiBurn$login=addRenderableWidget(Button.builder(Component.literal("登录"),button->muxiBurn$session.login(this,urlTextField.getValue())).bounds(controls.login().x(),controls.login().y(),controls.login().width(),20).build());
        if(muxiBurn$job==null) muxiBurn$job=muxiBurn$session.open(this,
            progress -> {if(!muxiBurn$closed){tips=BurnScreenRuntime.text(progress);muxiBurn$detailSummary=tips.getString();muxiBurn$detail=BurnScreenRuntime.detail(progress);}});
        if(muxiBurn$job.running()){
            boolean frozen=muxiBurn$session.readOnlySelected(this);
            if(readOnlyButton.selected()!=frozen)readOnlyButton.onPress();
            String input=muxiBurn$session.activeInput(this);if(!input.isBlank())urlTextField.setValue(input);
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
        }).bounds(controls.local().x(),controls.local().y(),controls.local().width(),20).build());
        muxiBurn$localButton.active=!muxiBurn$choosing;
        muxiBurn$cancel=addRenderableWidget(Button.builder(Component.literal("取消刻录"),button -> muxiBurn$job.cancel())
            .bounds(controls.cancel().x(),controls.cancel().y(),controls.cancel().width(),20).build());
        muxiBurn$source=addRenderableWidget(CycleButton.<String>builder(value->Component.literal(switch(value){
            case "qq_music" -> "QQ音乐";case "bilibili" -> "B站";default -> "网易";}))
            .withValues(java.util.List.of("netease","qq_music","bilibili"))
            .withInitialValue(muxiBurn$session.sourceType(this)).displayOnlyValue()
            .create(controls.source().x(),controls.source().y(),controls.source().width(),20,Component.literal("音乐源"),
                (button,value)->muxiBurn$session.sourceType(this,value)));
    }
    private void muxiBurn$link() {
        if(muxiBurn$session==null){tips=Component.literal("音乐服务尚未就绪");return;}
        if(muxiBurn$choosing){tips=Component.literal("请先完成文件选择");return;}

        if(!muxiBurn$session.prepareLink(this,urlTextField.getValue()))return;
        if(!muxiBurn$session.permissionConfirmed(this)){tips=Component.literal("请确认刻录");return;}
        try {muxiBurn$start(muxiBurn$session.link(this,urlTextField.getValue()));}
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
        if(muxiBurn$source!=null)muxiBurn$source.active=!busy;
        readOnlyButton.active=!busy;urlTextField.setEditable(!busy);
        if(muxiBurn$localButton!=null)muxiBurn$localButton.active=!busy&&!muxiBurn$choosing;
        if(muxiBurn$login!=null)muxiBurn$login.active=!muxiBurn$choosing&&(muxiBurn$job==null||!muxiBurn$job.running());
        if(muxiBurn$session!=null){String automatic=muxiBurn$session.title(this);if(!automatic.isBlank()&&!automatic.equals(nameTextField.getValue()))nameTextField.setValue(automatic);}
        if(muxiBurn$closed||muxiBurn$detail==null||muxiBurn$detail.getString().isBlank()
                ||mouseX<muxiBurn$statusX||mouseX>=muxiBurn$statusX+muxiBurn$statusWidth
                ||mouseY<muxiBurn$statusY||mouseY>=muxiBurn$statusY+font.lineHeight+2)return;
        int textWidth=Math.max(1,Math.min(184,width-24));
        var lines=font.split(muxiBurn$detail,textWidth);
        int contentWidth=0;for(var line:lines)contentWidth=Math.max(contentWidth,font.width(line));
        var occupied=muxiBurn$occupied;
        if(occupied==null)return;
        var box=BurnScreenRuntime.tooltipBox(width,height,occupied.x(),occupied.y(),occupied.width(),occupied.height(),
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
