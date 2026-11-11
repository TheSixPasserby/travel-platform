package com.example.travelplatform.guide.service;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/** Public-only keyset. The fingerprint prevents accidentally mixing filters between requests. */
public record FeedCursor(LocalDateTime snapshotAt, long upperId, LocalDateTime publishedAt, long id, long rank) {
    public String encode(String queryKey) {
        String value=String.join("|",snapshotAt.toString(),Long.toString(upperId),publishedAt.toString(),Long.toString(id),Long.toString(rank),queryKey);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static FeedCursor decode(String raw,String queryKey,LocalDateTime now) {
        try {
            if(raw.length()>1024) throw new IllegalArgumentException();
            String[] parts=new String(Base64.getUrlDecoder().decode(raw),StandardCharsets.UTF_8).split("\\|",-1);
            if(parts.length!=6 || !parts[5].equals(queryKey)) throw new IllegalArgumentException();
            var cursor=new FeedCursor(LocalDateTime.parse(parts[0]),Long.parseLong(parts[1]),LocalDateTime.parse(parts[2]),Long.parseLong(parts[3]),Long.parseLong(parts[4]));
            if(cursor.snapshotAt.isAfter(now.plusSeconds(1))
                    || cursor.upperId<1 || cursor.id<1 || cursor.id>cursor.upperId || cursor.rank<0
                    || cursor.publishedAt.isAfter(cursor.snapshotAt)) throw new IllegalArgumentException();
            return cursor;
        } catch(IllegalArgumentException | java.time.DateTimeException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,"列表游标无效，请刷新列表");
        }
    }

    public static String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch(NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
