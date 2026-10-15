package net.muxigame.musiccompat.burn;
import java.util.UUID;

/** Pure layout/error tests; injected width metric does not initialize MC Font. */
public final class BurnScreenUiTest {
    private static int checks;
    private static void check(boolean condition){if(!condition)throw new AssertionError("UI check "+checks);checks++;}
    private static BurnCoordinator.Progress failure(String code){return new BurnCoordinator.Progress(UUID.randomUUID(),BurnCoordinator.Stage.FAILED,0,0,code);}
    private static int width(String value){return value.codePointCount(0,value.length())*9;}
    private static void outside(BurnScreenRuntime.TipBox box,int x,int y,int w,int h){check(box!=null);check(box.x()+box.width()<=x||box.x()>=x+w||box.y()+box.height()<=y||box.y()>=y+h);}
    public static void main(String[] args){
        String[] codes={"music_daily_upload_limit","music_intent_rate_limit","music_oss_read_deadline","storage_operation_failed",
            "music_oss_transport_unavailable","music_sts_exchange_unavailable","music_workload_identity_unavailable","music_identity_too_short",
            "music_oss_response_limit","music_oss_payload_refused","music_oss_origin_refused","music_oss_write_guard","music_operation_limit",
            "music_payload_limit","music_read_limit","request_id_reused","job_not_pending","job_cancelled_or_expired","music_service_unavailable",
            "platform_download_permission_unavailable","platform_trial_only","platform_drm_unsupported","account_authorization_required"};
        for(String code:codes){
            var progress=failure(code);String full=BurnScreenRuntime.message(progress);
            String shortLine=BurnScreenRuntime.singleLine(full,135,BurnScreenUiTest::width);
            check(!shortLine.contains("\n")&&!shortLine.contains("\r"));check(width(shortLine)<=135);
            check(BurnScreenRuntime.detailText(progress).endsWith("["+code+"]"));
        }
        check(BurnScreenRuntime.message(failure("music_oss_read_deadline")).contains("读取上传音频超时"));
        check(BurnScreenRuntime.message(failure("storage_operation_failed")).contains("存储校验失败"));
        check(BurnScreenRuntime.message(failure("music_oss_transport_unavailable")).contains("音频存储连接失败"));
        for(String unknown:new String[]{null,"COOKIE=OFFLINE_NOT_REAL","https://example.invalid/?signature=OFFLINE_NOT_REAL","unknown_provider_failure"}){
            String detail=BurnScreenRuntime.detailText(failure(unknown));check(detail.endsWith("[burn_failed_retry_available]"));
            check(!detail.contains("COOKIE")&&!detail.contains("signature=")&&!detail.contains("unknown_provider_failure"));
        }
        check(BurnScreenRuntime.singleLine("第一行\n第二行\r第三行\t尾部",135,BurnScreenUiTest::width).indexOf('\n')<0);
        String emoji=BurnScreenRuntime.singleLine("🎵🎵🎵🎵🎵",27,BurnScreenUiTest::width);
        check(width(emoji)<=27);check(emoji.codePoints().noneMatch(c->c>=0xd800&&c<=0xdfff));
        check(BurnScreenRuntime.singleLine("状态",0,BurnScreenUiTest::width).isEmpty());
        check(BurnScreenRuntime.singleLine("状态",1,BurnScreenUiTest::width).isEmpty());
        outside(BurnScreenRuntime.tooltipBox(640,360,232,60,176,190,192,58),232,60,176,190);
        outside(BurnScreenRuntime.tooltipBox(640,360,460,60,176,190,192,58),460,60,176,190);
        outside(BurnScreenRuntime.tooltipBox(320,360,72,120,176,190,192,58),72,120,176,190);
        outside(BurnScreenRuntime.tooltipBox(320,360,72,20,176,190,192,58),72,20,176,190);
        check(BurnScreenRuntime.tooltipBox(320,180,72,0,176,166,192,58)==null);
        check(BurnScreenRuntime.tooltipBox(20,20,0,0,16,16,30,30)==null);
        check(BurnScreenRuntime.selectionCurrent(5,5,false,true));
        check(!BurnScreenRuntime.selectionCurrent(5,6,false,true));
        check(!BurnScreenRuntime.selectionCurrent(5,5,true,true));
        check(!BurnScreenRuntime.selectionCurrent(5,5,false,false));
        var upload=new BurnCoordinator.Progress(UUID.randomUUID(),BurnCoordinator.Stage.UPLOADING,5,10,"");
        check(BurnScreenRuntime.message(upload).endsWith("50%"));check(!BurnScreenRuntime.detailText(upload).contains("["));
        System.out.println("{\"uiChecks\":"+checks+",\"statusWidthPixels\":135,\"controlsCoveredByTooltip\":false,\"mcStarted\":false}");
    }
}
