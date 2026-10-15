package net.muxigame.musiccompat.burn;
import java.util.Set;
/** Only stable service codes cross the client boundary; provider messages never do. */
public final class MusicServiceErrors {
    private MusicServiceErrors(){}
    private static final Set<String> KNOWN=Set.of(
        "music_upload_quota","music_daily_upload_limit","music_intent_rate_limit",
        "audio_digest_mismatch","unsupported_or_truncated_audio","music_upload_unconfigured",
        "launcher_authorization_required","live_account_connection_required","burn_context_required",
        "audio_validation_busy","audio_validation_timeout","invalid_metadata_size",
        "music_oss_read_deadline","music_oss_transport_unavailable","music_oss_response_limit",
        "music_oss_payload_refused","music_oss_origin_refused","music_oss_write_guard",
        "storage_operation_failed","music_sts_exchange_unavailable","music_workload_identity_unavailable",
        "music_identity_too_short","music_operation_limit","music_payload_limit","music_read_limit",
        "request_id_reused","job_not_pending","job_cancelled_or_expired","invalid_audio_size");
    public static String safe(String code){
        if(!KNOWN.contains(code==null?"":code))return "music_service_unavailable";
        return code.equals("launcher_authorization_required")?"account_authorization_required":code;
    }
}
