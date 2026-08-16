package com.careerops.backend.job.dto;

import com.careerops.backend.job.Attachment;

public record AttachmentResponse(
        Integer sortNo,
        String fileName,
        String fileType,
        String url
) {
    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getSortNo(),
                attachment.getFileName(),
                attachment.getFileType(),
                attachment.getUrl()
        );
    }
}
