package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.CaseAssignmentRepository;
import com.healthdocs.backend.adapter.out.persistence.CaseResultRepository;
import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.CaseResultEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.StoredFileEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.StoragePort;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.Role;
import com.healthdocs.backend.infrastructure.config.InternalFlowProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalFlowService {
    private final MedicalCaseRepository cases; private final StoredFileRepository files;
    private final CaseAssignmentRepository assignments; private final CaseResultRepository results;
    private final StoragePort storage; private final InternalFlowProperties properties; private final Clock clock;
    public InternalFlowService(MedicalCaseRepository cases, StoredFileRepository files, CaseAssignmentRepository assignments,
            CaseResultRepository results, StoragePort storage, InternalFlowProperties properties, Clock clock) {
        this.cases=cases; this.files=files; this.assignments=assignments; this.results=results; this.storage=storage; this.properties=properties; this.clock=clock;
    }
    public List<CaseView> list(AuthenticatedUser user) {
        requireInternal(user);
        return cases.findEnabledForUser(user.id(), CaseStatus.ENABLED_FOR_PROCESSING).stream().map(this::view).toList();
    }
    public Download download(AuthenticatedUser user, UUID caseId, UUID fileId) {
        requireInternal(user); MedicalCaseEntity medicalCase = visibleCase(user, caseId);
        StoredFileEntity file = files.findById(fileId).orElseThrow(() -> new ResourceNotFoundException("File not found"));
        if (!file.getMedicalCase().getId().equals(medicalCase.getId()) || file.getFileKind()!=FileKind.INPUT) throw new ResourceNotFoundException("File not found");
        StoragePort.UploadUrlResult url=storage.generateDownloadUrl(file.getObjectKey(), file.getContentType(), properties.downloadTtlMinutes());
        return new Download(url.uploadUrl(), url.expiresAt());
    }
    @Transactional
    public UploadIntent createResult(AuthenticatedUser user, UUID caseId, String filename, String contentType, Long size) {
        requireInternal(user); visibleCase(user, caseId);
        if (results.existsByMedicalCaseId(caseId) || files.countByMedicalCaseIdAndFileKind(caseId, FileKind.RESULT)>0) throw new BusinessRuleViolationException("Result already exists");
        validate(filename, contentType, size);
        UUID id=UUID.randomUUID(); String key="result/%s/%s/%s".formatted(caseId,id,sanitize(filename)); Instant now=clock.instant();
        StoredFileEntity entity=new StoredFileEntity(id, cases.findById(caseId).orElseThrow(), key, FileKind.RESULT, filename.trim(), contentType.trim(), size);
        StoredFileEntity saved=files.save(entity); StoragePort.UploadUrlResult url=storage.generateUploadUrl(key, contentType, properties.uploadTtlMinutes());
        return new UploadIntent(saved.getId(), saved.getOriginalFilename(), saved.getContentType(), saved.getSizeBytes(), saved.getFileKind(), url.uploadUrl(), url.expiresAt());
    }
    @Transactional
    public Confirmation confirm(AuthenticatedUser user, UUID fileId) {
        requireInternal(user); StoredFileEntity file=files.findById(fileId).orElseThrow(()->new ResourceNotFoundException("File not found"));
        if (file.getFileKind()!=FileKind.RESULT) throw new BusinessRuleViolationException("Only result files can be confirmed internally");
        MedicalCaseEntity medicalCase=visibleCase(user,file.getMedicalCase().getId());
        if (!storage.objectExists(file.getObjectKey())) throw new BusinessRuleViolationException("Uploaded object does not exist");
        if (results.existsByMedicalCaseId(medicalCase.getId())) throw new BusinessRuleViolationException("Result already exists");
        CaseResultEntity result=results.save(new CaseResultEntity(medicalCase,file,com.healthdocs.backend.domain.model.ResultStatus.UPLOADED));
        result.recordUpload(user.id(),clock.instant()); medicalCase.setStatus(CaseStatus.RESULT_UPLOADED); medicalCase.setUpdatedAt(clock.instant()); cases.save(medicalCase);
        return new Confirmation(result.getId(),medicalCase.getId(),result.getStatus(),user.id(),result.getUploadedAt(),medicalCase.getStatus());
    }
    private MedicalCaseEntity visibleCase(AuthenticatedUser user, UUID id) { MedicalCaseEntity c=cases.findById(id).orElseThrow(()->new ResourceNotFoundException("Case not found")); if(c.getStatus()!=CaseStatus.ENABLED_FOR_PROCESSING || !assignments.existsByCaseIdAndUserId(id,user.id())) throw new ForbiddenOperationException("Case is not available"); return c; }
    private void requireInternal(AuthenticatedUser u) { if(u==null || !u.enabled() || (u.role()!=Role.ESTUDIANTE && u.role()!=Role.PROFESOR)) throw new ForbiddenOperationException("Internal access required"); }
    private void validate(String f,String m,Long s) { if(f==null||f.isBlank()||f.length()>255||f.contains("..")||f.contains("/")||f.contains("\\")) throw new IllegalArgumentException("Invalid originalFilename"); if(m==null||!properties.allowedMimeTypes().contains(m.split(";",2)[0].trim())) throw new UnsupportedMediaTypeException("MIME type is not allowed"); if(s==null||s<1) throw new IllegalArgumentException("sizeBytes must be positive"); if(s>properties.resultMaxSizeBytes()) throw new PayloadTooLargeException("File exceeds maximum size"); }
    private CaseView view(MedicalCaseEntity c) { return new CaseView(c.getId(),c.getReferenceCode(),c.getStudyType(),c.getStatus(),c.getCreatedAt(),files.findByMedicalCaseId(c.getId()).stream().filter(f->f.getFileKind()==FileKind.INPUT).map(f->new FileView(f.getId(),f.getOriginalFilename(),f.getContentType(),f.getSizeBytes(),f.getFileKind())).toList()); }
    private static String sanitize(String s){return s.replace("\\","_").replace("/","_");}
    public record FileView(UUID id,String originalFilename,String contentType,Long sizeBytes,FileKind fileKind){}
    public record CaseView(UUID id,String referenceCode,String studyType,CaseStatus status,Instant createdAt,List<FileView> files){}
    public record Download(String downloadUrl,Instant expiresAt){}
    public record UploadIntent(UUID id,String originalFilename,String contentType,Long sizeBytes,FileKind fileKind,String uploadUrl,Instant expiresAt){}
    public record Confirmation(UUID resultId,UUID caseId,com.healthdocs.backend.domain.model.ResultStatus status,UUID uploadedBy,Instant uploadedAt,CaseStatus caseStatus){}
}
