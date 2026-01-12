package com.loopone.loopinbe.global.s3;

import com.loopone.loopinbe.domain.chat.chatMessage.dto.ChatAttachment;
import com.loopone.loopinbe.domain.chat.chatMessage.enums.AttachmentType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private static final String REGION_ENDPOINT = "s3.ap-northeast-2.amazonaws.com";

    @Value("${spring.aws.credentials.s3.bucket}")
    private String BUCKET_NAME;

    // 채팅 이미지 업로드
    public ChatAttachment uploadChatImage(MultipartFile file, String dirName) throws IOException {
        String key = buildKey(dirName, file.getOriginalFilename());
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(key)
                .contentType(file.getContentType())
                .contentDisposition("inline")
                .build();
        s3Client.putObject(req, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        return new ChatAttachment(
                AttachmentType.IMAGE,
                key,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize()
        );
    }

    // 채팅 파일 업로드
    public ChatAttachment uploadChatFile(MultipartFile file, String dirName) throws IOException {
        String key = buildKey(dirName, file.getOriginalFilename());
        String disposition = buildContentDisposition(file.getOriginalFilename());

        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(key)
                .contentType(file.getContentType())
                .contentDisposition(disposition) // 객체 메타데이터로 저장(직접 URL 접근 시에도 도움)
                .build();
        s3Client.putObject(req, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        return new ChatAttachment(
                AttachmentType.FILE,
                key,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize()
        );
    }

    // key 기반 삭제
    public void deleteObjectByKey(String key) {
        DeleteObjectRequest req = DeleteObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(key)
                .build();
        s3Client.deleteObject(req);
    }

    public void deleteAllByKeys(List<String> keys) {
        if (keys == null) return;
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            deleteObjectByKey(key);
        }
    }

    // 파일 다운로드용 presigned url (파일명 지정)
    public String generateDownloadPresignedUrl(String key, String downloadFileName) {
        String encodedFileName = encodeRFC5987(downloadFileName);
        String contentDisposition = "attachment; filename=\"" + sanitizeAsciiFallback(downloadFileName)
                + "\"; filename*=UTF-8''" + encodedFileName;

        GetObjectRequest getReq = GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(key)
                .responseContentDisposition(contentDisposition)
                .build();

        return s3Presigner.presignGetObject(b -> b
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(getReq)
        ).url().toString();
    }

    // ----------------- 헬퍼 메서드 -----------------

    // public url 형태가 필요하면 사용
    public String toPublicUrl(String key) {
        return "https://" + BUCKET_NAME + "." + REGION_ENDPOINT + "/" + key;
    }

    private String buildKey(String dirName, String originalName) {
        String safeName = sanitizeForKey(originalName);
        return dirName + "/" + UUID.randomUUID() + "_" + safeName;
    }

    private String sanitizeForKey(String fileName) {
        if (fileName == null || fileName.isBlank()) return "file";
        // 슬래시/역슬래시 제거 + 제어문자 제거
        String s = fileName.replace("\\", "_").replace("/", "_");
        return s.replaceAll("[\\p{Cntrl}]", "_");
    }

    private String buildContentDisposition(String downloadFileName) {
        String encoded = encodeRFC5987(downloadFileName);
        return "attachment; filename=\"" + sanitizeAsciiFallback(downloadFileName) + "\"; filename*=UTF-8''" + encoded;
    }

    private String encodeRFC5987(String fileName) {
        try {
            return URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20")
                    .replaceAll("%28", "(")
                    .replaceAll("%29", ")")
                    .replaceAll("%27", "'");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Encoding failed", e);
        }
    }

    private String sanitizeAsciiFallback(String fileName) {
        // fallback용 ASCII-safe 이름
        return fileName == null ? "file" : fileName.replaceAll("[^a-zA-Z0-9 _.-]", "_");
    }
}
