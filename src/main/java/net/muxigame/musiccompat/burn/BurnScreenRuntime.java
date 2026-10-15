package net.muxigame.musiccompat.burn;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.Set;
import net.minecraft.network.chat.Component;

/** One explicitly installed integration owner supplies live account and server commit. */
public final class BurnScreenRuntime {
    public interface Session {
        boolean enabled();
        BurnCoordinator open(AbstractContainerScreen<?> screen, Consumer<BurnCoordinator.Progress> progress);
        BurnCoordinator.Commit commit(AbstractContainerScreen<?> screen);
        BurnCoordinator.Source link(AbstractContainerScreen<?> screen,String value);
        BurnCoordinator.Source local(AbstractContainerScreen<?> screen,Path selected);
        void chooseLocal(Consumer<Path> selected);
        /** Production owner overrides this to expose the single-dialog admission. */
        default boolean chooseLocalOnce(Consumer<Path> selected) { chooseLocal(selected); return true; }
        /** The local user confirmation; initially false. */
        boolean permissionConfirmed(AbstractContainerScreen<?> screen);
        void permissionScreen(AbstractContainerScreen<?> screen);
        default void confirmation(AbstractContainerScreen<?> screen,boolean value){}
        default void login(AbstractContainerScreen<?> screen){}
        default void login(AbstractContainerScreen<?> screen,String value){login(screen);}
        default boolean prepareLink(AbstractContainerScreen<?> screen,String value){return true;}
        /** Detaches a view; background jobs are owned by their original connection. */
        void closed(AbstractContainerScreen<?> screen);
        void readOnly(AbstractContainerScreen<?> screen,boolean selected);
        default boolean readOnlySelected(AbstractContainerScreen<?> screen){return true;}
        default String activeInput(AbstractContainerScreen<?> screen){return "";}
        default String sourceType(AbstractContainerScreen<?> screen){return "netease";}
        default void sourceType(AbstractContainerScreen<?> screen,String type){}
        default String title(AbstractContainerScreen<?> screen){return "";}
        default void title(AbstractContainerScreen<?> screen,String title){}
    }
    private static volatile Session owner;
    private BurnScreenRuntime() {}
    public static synchronized void install(Session session) {
        if (owner != null && owner != session) throw new IllegalStateException("burn_integration_owner_exists");
        owner=session;
    }
    public static Session session() { Session current=owner;return current!=null && current.enabled()?current:null; }
    /** Keep explicit close widgets usable while the connection-owned task runs. */
    public static boolean closeControl(String label) {
        if(label==null)return false;
        return switch(label.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "×","✕","✖","x","关闭","close" -> true;
            default -> false;
        };
    }
    public static Component text(BurnCoordinator.Progress progress) { return Component.literal(message(progress)); }
    public static String message(BurnCoordinator.Progress progress) {
        String stage=switch(progress.stage()) {
            case PREPARING -> "准备刻录";case ACQUIRING -> "获取音频";case CHECKING -> "校验音频";case AUTHORIZING -> "申请上传";
            case UPLOADING -> "上传音频";case VERIFYING -> "服务器校验";case COMMITTING -> "刻录光盘";
            case DONE -> "刻录完成，唱片已放入背包";case FAILED -> "刻录失败，请查看原因";case CANCELLED -> progress.discReturned()?"已取消，唱片已返还背包":"已取消，等待唱片返还";
        };
        if(progress.stage()==BurnCoordinator.Stage.FAILED)stage=switch(progress.code()==null?"":progress.code()){
            case "platform_login_required","platform_login_expired" -> "平台登录已失效，请在原登录界面重新登录";
            case "platform_account_changed" -> "平台账号或游戏连接已切换，请重新开始";
            case "platform_client_session_unavailable" -> "当前客户端登录接口不可用";
            case "platform_trial_only" -> "平台仅提供试听，不能刻录上传";
            case "platform_drm_unsupported" -> "平台音频受 DRM 或加密保护，不能刻录上传";
            case "platform_permission_denied","platform_download_denied" -> "当前平台账号没有获取或下载权限";
            case "platform_download_capability_unavailable" -> "平台返回了播放信息，但未提供可确认的下载能力";
            case "platform_format_unavailable","platform_capability_unavailable" -> "平台未提供可确认的音频格式或能力";
            case "platform_redirect_refused" -> "平台请求发生重定向，已停止并保留本地凭证";
            case "platform_media_expired","platform_media_unavailable","platform_api_unavailable" -> "平台地址过期或暂不可用，请重试";
            case "platform_source_invalid","invalid_audio_link","not_local_audio","platform_response_invalid","platform_media_url_refused","platform_song_id_required" -> "平台歌曲或媒体响应无效，请重新选择";
            case "core_account_api_update_required" -> "请更新包含当前账号接口的游戏核心模组";
            case "unsupported_audio","unsupported_or_truncated_audio" -> "音频损坏或格式不受支持，请重新选择";
            case "account_authorization_required","live_account_connection_required" -> "账号连接失效，请重新连接";
            case "music_upload_quota" -> "达到上传配额，稍后重试";
            case "music_daily_upload_limit" -> "今日已使用 100 次刻录额度，请明日再试";
            case "music_intent_rate_limit" -> "请求过于频繁，请稍后重试";
            case "upload_ticket_expired" -> "上传授权过期，可重试";
            case "server_commit_timeout" -> "服务器未确认刻录，可重试";
            case "audio_digest_mismatch","audio_changed","truncated_audio" -> "音频校验失败，可重试";
            case "music_upload_unconfigured" -> "音乐上传服务尚未配置";
            case "platform_download_permission_unavailable" -> "平台未提供可验证的下载授权，请导入合法本地音频";
            case "burn_context_required" -> "请放入一张可刻录唱片后重试";
            case "audio_validation_busy","audio_validation_timeout" -> "音频校验暂不可用，请稍后重试";
            case "redistribution_permission_required" -> "请确认刻录";
            case "music_oss_read_deadline" -> "读取上传音频超时，请重试";
            case "storage_operation_failed" -> "存储校验失败，请重试";
            case "music_oss_transport_unavailable" -> "音频存储连接失败，请重试";
            case "music_sts_exchange_unavailable","music_workload_identity_unavailable","music_identity_too_short" -> "临时云身份暂不可用，请重试";
            case "music_oss_response_limit","music_oss_payload_refused","music_oss_origin_refused","music_oss_write_guard" -> "音频存储校验失败，请重试";
            case "music_operation_limit","music_payload_limit","music_read_limit" -> "音频存储额度不足，请稍后重试";
            case "request_id_reused" -> "本次任务信息已变更，请重新开始";
            case "job_not_pending","job_cancelled_or_expired" -> "本次上传任务已结束，请重新开始";
            case "music_service_unavailable" -> "音乐服务暂不可用，请稍后重试";
            default -> "刻录失败，可重试";
        };
        if(progress.stage()==BurnCoordinator.Stage.FAILED&&progress.discReturned())stage+="，唱片已返还背包";
        if(progress.total()>0)stage+=" "+Math.min(100,progress.bytes()*100/progress.total())+"%";
        return stage;
    }
    private static final Set<String> DETAIL_CODES = Set.of(
        "platform_login_required","platform_login_expired","platform_account_changed",
        "platform_client_session_unavailable","platform_trial_only","platform_drm_unsupported",
        "platform_permission_denied","platform_download_denied","platform_download_capability_unavailable",
        "platform_format_unavailable","platform_capability_unavailable","platform_redirect_refused",
        "platform_media_expired","platform_media_unavailable","platform_api_unavailable",
        "platform_response_invalid","platform_media_url_refused","platform_song_id_required",
        "core_account_api_update_required","unsupported_audio","unsupported_or_truncated_audio",
        "account_authorization_required","live_account_connection_required","music_upload_quota",
        "music_daily_upload_limit","music_intent_rate_limit","upload_ticket_expired","server_commit_timeout",
        "audio_digest_mismatch","audio_changed","truncated_audio","music_upload_unconfigured",
        "platform_download_permission_unavailable","burn_context_required","audio_validation_busy",
        "audio_validation_timeout","redistribution_permission_required","music_oss_read_deadline",
        "storage_operation_failed","music_oss_transport_unavailable","music_sts_exchange_unavailable",
        "music_workload_identity_unavailable","music_identity_too_short","music_oss_response_limit",
        "music_oss_payload_refused","music_oss_origin_refused","music_oss_write_guard","music_operation_limit",
        "music_payload_limit","music_read_limit","request_id_reused","job_not_pending","job_cancelled_or_expired",
        "music_service_unavailable","oss_upload_failed","download_unavailable","invalid_audio_size",
        "invalid_receipt","server_commit_failed","burn_failed_retry_available");
    public static String detailText(BurnCoordinator.Progress progress) {
        String summary = message(progress);
        if (progress.stage() != BurnCoordinator.Stage.FAILED) return summary;
        String code = DETAIL_CODES.contains(progress.code() == null ? "" : progress.code())
                ? progress.code() : "burn_failed_retry_available";
        return summary + "\n[" + code + "]";
    }
    public static Component detail(BurnCoordinator.Progress progress) { return Component.literal(detailText(progress)); }
    public static boolean selectionCurrent(long requestGeneration,long screenGeneration,boolean closed,boolean visible) {
        return requestGeneration == screenGeneration && !closed && visible;
    }
    /** The upstream status row is 135 pixels wide and only one font line tall. */
    public static String singleLine(String value,int width,ToIntFunction<String> measure) {
        String clean = value.replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]", " ").replaceAll(" +", " ").strip();
        if (width < 1) return "";
        if (measure.applyAsInt(clean) <= width) return clean;
        String suffix = "…";
        if (measure.applyAsInt(suffix) > width) return "";
        int end = clean.length();
        while (end > 0) {
            end = clean.offsetByCodePoints(end, -1);
            String clipped = clean.substring(0, end) + suffix;
            if (measure.applyAsInt(clipped) <= width) return clipped;
        }
        return suffix;
    }
    public record TipBox(int x,int y,int width,int height) {}
    public record ButtonBox(int x,int y,int width) {}
    public record ComputerControls(ButtonBox permission,ButtonBox local,ButtonBox cancel,ButtonBox source,ButtonBox login,TipBox occupied) {}
    /** Header rows when there is room; one side column at the minimum GUI height. */
    public static ComputerControls computerControls(int width,int height,int left,int top,int panelWidth,int panelHeight){
        if(top>=56){
            return new ComputerControls(new ButtonBox(left+8,top-24,52),new ButtonBox(left+64,top-24,52),
                new ButtonBox(left+120,top-24,52),new ButtonBox(left+8,top-48,panelWidth-72),new ButtonBox(left+panelWidth-60,top-48,52),
                new TipBox(left,top-48,panelWidth,panelHeight+48));
        }
        int x=left+panelWidth+8;
        if(x+52>width-8)x=left-60;
        if(x<8||x+52>width-8)throw new IllegalArgumentException("computer_controls_do_not_fit");
        int y=Math.max(8,Math.min(top,height-116));
        int occupiedLeft=Math.min(left,x),occupiedTop=Math.min(top,y);
        return new ComputerControls(new ButtonBox(x,y,52),new ButtonBox(x,y+22,52),new ButtonBox(x,y+44,52),
            new ButtonBox(x,y+66,52),new ButtonBox(x,y+88,52),new TipBox(occupiedLeft,occupiedTop,
                Math.max(left+panelWidth,x+52)-occupiedLeft,Math.max(top+panelHeight,y+108)-occupiedTop));
    }

    public static ButtonBox burnerLogin(int width,int left,int top,int panelWidth){
        if(top>=48)return new ButtonBox(left+120,top-48,52);
        int x=left+panelWidth+8;if(x+52>width-8)x=left-60;return new ButtonBox(x,Math.max(8,top-24),52);
    }
    /** Keep detail outside the occupied burner panel, including its top buttons. */
    public static TipBox tooltipBox(int screenWidth,int screenHeight,int panelX,int panelY,
                                    int panelWidth,int panelHeight,int boxWidth,int boxHeight) {
        if (boxWidth < 1 || boxHeight < 1 || boxWidth > screenWidth - 16 || boxHeight > screenHeight - 16) return null;
        int y = Math.max(8, Math.min(panelY, screenHeight - boxHeight - 8));
        if (panelX + panelWidth + 8 + boxWidth <= screenWidth - 8)
            return new TipBox(panelX + panelWidth + 8, y, boxWidth, boxHeight);
        if (panelX - boxWidth - 8 >= 8) return new TipBox(panelX - boxWidth - 8, y, boxWidth, boxHeight);
        int x = Math.max(8, Math.min(panelX, screenWidth - boxWidth - 8));
        if (panelY - boxHeight - 8 >= 8) return new TipBox(x, panelY - boxHeight - 8, boxWidth, boxHeight);
        if (panelY + panelHeight + 8 + boxHeight <= screenHeight - 8)
            return new TipBox(x, panelY + panelHeight + 8, boxWidth, boxHeight);
        return null;
    }
}
