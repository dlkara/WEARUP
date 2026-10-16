package com.lec.spring.service;

import com.lec.spring.domain.ItemAttachment;
import com.lec.spring.repository.ItemAttachmentRepository;
import com.lec.spring.domain.Item;
import com.lec.spring.security.ResourceAccess;
import com.lec.spring.security.MutationLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
public class ItemAttachmentServiceImpl implements ItemAttachmentService {

    private final ItemAttachmentRepository itemAttachmentRepository;
    private final ResourceAccess resourceAccess;
    @Value("${app.upload.path.item}")
    private String uploadDirItem;

    public ItemAttachmentServiceImpl(ItemAttachmentRepository itemAttachmentRepository, ResourceAccess resourceAccess) {
        this.itemAttachmentRepository = itemAttachmentRepository;
        this.resourceAccess = resourceAccess;
    }

    @Override
    public int save(ItemAttachment file) {
        return itemAttachmentRepository.save(file);
    }

    @Override
    public List<ItemAttachment> findByItemId(Long itemId) {
        return itemAttachmentRepository.findByItemId(itemId);
    }

    @Override
    public ItemAttachment findById(Long id) {
        return itemAttachmentRepository.findById(id);
    }

    @Override
    public int deleteById(Long id) {
        ItemAttachment file = itemAttachmentRepository.findById(id);
        if (file != null) {
            // - 파일을 지우기 전에 DB 상품의 소유 브랜드 확인
            Item item = resourceAccess.requireItemOwner(file.getItemId());
            // - 삭제 대상이 업로드 폴더 밖을 가리키지 않는지 확인
            Path root = Path.of(uploadDirItem).toAbsolutePath().normalize();
            Path target = root.resolve(file.getFilename()).normalize();
            if (!target.startsWith(root) || target.equals(root)) {
                log.error("event=ITEM_FILE_DELETE_FAILED resourceId={} reasonCode=STORAGE_PATH_INVALID", id);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_PATH_INVALID");
            }
            try {
                Files.deleteIfExists(target);
            } catch (IOException ex) {
                log.error("event=ITEM_FILE_DELETE_FAILED resourceId={} reasonCode=CLEANUP_FAILED exceptionType={}",
                        id, ex.getClass().getSimpleName());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "CLEANUP_FAILED");
            }
            // - 파일 처리 후 DB 첨부 정보 삭제
            // - DB 실패 시 파일 복구는 별도 보완 필요
            int result = itemAttachmentRepository.deleteById(file);
            if (result > 0) MutationLog.success("DELETE", "ITEM_ATTACHMENT", id, item.getBrand().getId());
            return result;
        }
        return 0;
    }
}
