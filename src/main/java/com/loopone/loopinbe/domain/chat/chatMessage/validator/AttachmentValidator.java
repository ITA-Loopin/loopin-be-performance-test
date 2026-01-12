package com.loopone.loopinbe.domain.chat.chatMessage.validator;

import com.loopone.loopinbe.global.exception.ReturnCode;
import com.loopone.loopinbe.global.exception.ServiceException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

public final class AttachmentValidator {
    private AttachmentValidator() {}
    private static final int MAX_FILE_COUNT = 10;
    private static final long MAX_FILE_SIZE = 30L * 1024 * 1024;;

    public static void validateImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) return;
        if (images.size() > MAX_FILE_COUNT) {
            throw new ServiceException(ReturnCode.MAX_FILE_LIMIT_EXCEEDED);
        }
        for (MultipartFile f : images) {
            if (f == null || f.isEmpty()) continue;
            if (f.getSize() > MAX_FILE_SIZE) {
                throw new ServiceException(ReturnCode.MAX_FILE_SIZE_LIMIT_EXCEEDED);
            }
        }
    }

    public static void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return;
        if (files.size() > MAX_FILE_COUNT) {
            throw new ServiceException(ReturnCode.MAX_FILE_LIMIT_EXCEEDED);
        }
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) continue;
            if (f.getSize() > MAX_FILE_SIZE) {
                throw new ServiceException(ReturnCode.MAX_FILE_SIZE_LIMIT_EXCEEDED);
            }
        }
    }
}
