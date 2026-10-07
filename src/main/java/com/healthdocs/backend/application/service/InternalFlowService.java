package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.CaseAssignmentRepository;
import com.healthdocs.backend.adapter.out.persistence.CaseResultRepository;
import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.CaseResultEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.StoredFileEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.ResultNotificationPublisher;
import com.healthdocs.backend.application.port.out.StoragePort;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.Role;
import com.healthdocs.backend.domain.model.ResultAvailableEvent;
import com.healthdocs.backend.infrastructure.config.InternalFlowProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalFlowService {
    private final MedicalCaseRepository cases; private final StoredFileRepository files;
    private final CaseAssignmentRepository assignments; private final CaseResultRepository results;
    private final StoragePort storage; private final InternalFlowProperties properties; private final Clock clock;
    private final ResultNotificationPublisher resultNotificationPublisher;
    @Autowired
    public InternalFlowService(MedicalCaseRepository cases, StoredFileRepository files, CaseAssignmentRepository assignments,
            CaseResultRepository results, StoragePort storage, InternalFlowProperties properties, Clock clock) {
        this(cases, files, assignments, results, storage, properties, clock, event -> { });
    }
    public InternalFlowService(MedicalCaseRepository cases, StoredFileRepository files, CaseAssignmentRepository assignments,
            CaseResultRepository results, StoragePort storage, InternalFlowProperties properties, Clock clock,
            ResultNotificationPublisher resultNotificationPublisher) {
        this.cases=cases; this.files=files; this.assignments=assignments; this.results=results; this.storage=storage; this.properties=properties; this.clock=clock;
        this.resultNotificationPublisher = resultNotificationPublisher;
    }
    @Transactional(readOnly = true)
    public List<CaseView> list(AuthenticatedUser user) {
        requireInternal(user);
        return cases.findAssignedInternalCases(user.id(),
                List.of(CaseStatus.ENABLED_FOR_PROCESSING, CaseStatus.RESULT_UPLOADED, CaseStatus.RESULT_AVAILABLE))
                .stream().map(this::view).toList();
    }
    public Download download(AuthenticatedUser user, UUID caseId, UUID fileId) {
        requireInternal(user);
        MedicalCaseEntity medicalCase = downloadVisibleCase(user, caseId);
        StoredFileEntity file = files.findById(fileId).orElseThrow(() -> new ResourceNotFoundException("File not found"));
        if (!file.getMedicalCase().getId().equals(medicalCase.getId()) || file.getFileKind()!=FileKind.INPUT) throw new ResourceNotFoundException("File not found");
        StoragePort.UploadUrlResult url=storage.generateDownloadUrl(file.getObjectKey(), file.getContentType(), properties.downloadTtlMinutes());
        return new Download(url.uploadUrl(), url.expiresAt());
    }
    @Transactional(readOnly = true)
    public PatientResult patientResult(AuthenticatedUser user, UUID caseId) {
        if (user == null || !user.enabled() || user.role() != Role.PATIENT) {
            throw new ForbiddenOperationException("Patient access required");
        }
        MedicalCaseEntity medicalCase = cases.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        if (!medicalCase.getPatient().getId().equals(user.id())) {
            throw new ForbiddenOperationException("Case does not belong to patient");
        }
        if (medicalCase.getStatus() != CaseStatus.RESULT_AVAILABLE) {
            throw new BusinessRuleViolationException("Case result is not available yet");
        }
        CaseResultEntity result = results.findByMedicalCaseId(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Result not available"));
        if (result.getStatus() != com.healthdocs.backend.domain.model.ResultStatus.AVAILABLE) {
            throw new BusinessRuleViolationException("Result is not available yet");
        }
        StoredFileEntity file = result.getFile();
        if (file == null
                || file.getFileKind() != FileKind.RESULT
                || file.getMedicalCase() == null
                || !caseId.equals(file.getMedicalCase().getId())
                || file.getObjectKey() == null
                || file.getObjectKey().isBlank()
                || !storage.objectExists(file.getObjectKey())) {
            throw new ResourceNotFoundException("Result file not found");
        }
        StoragePort.UploadUrlResult url = storage.generateDownloadUrl(
                file.getObjectKey(), file.getContentType(), properties.downloadTtlMinutes());
        return new PatientResult(result.getId(), caseId, result.getAvailableAt(),
                new Download(url.uploadUrl(), url.expiresAt()));
    }
    @Transactional(readOnly = true)
    public Download downloadResult(AuthenticatedUser user, UUID caseId, UUID resultId) {
        // Coordinadores e investigadores comparten la descarga temporal del resultado:
        // el acceso se limita al personal interno asignado al caso.
        requireInternal(user);
        visibleResultCase(user, caseId, resultId);
        CaseResultEntity result = results.findById(resultId).orElseThrow(() -> new ResourceNotFoundException("Result not found"));
        StoragePort.UploadUrlResult url = storage.generateDownloadUrl(result.getFile().getObjectKey(), result.getFile().getContentType(), properties.downloadTtlMinutes());
        return new Download(url.uploadUrl(), url.expiresAt());
    }
    @Transactional
    public void deleteResult(AuthenticatedUser user, UUID caseId, UUID resultId) {
        requireInternal(user);
        CaseResultEntity result = results.findById(resultId).orElseThrow(() -> new ResourceNotFoundException("Result not found"));
        visibleResultCase(user, caseId, resultId);
        if (result.getStatus() != com.healthdocs.backend.domain.model.ResultStatus.UPLOADED) throw new BusinessRuleViolationException("Published results cannot be deleted");
        storage.deleteObject(result.getFile().getObjectKey());
        results.delete(result);
        files.delete(result.getFile());
        MedicalCaseEntity medicalCase = result.getMedicalCase();
        medicalCase.setStatus(CaseStatus.ENABLED_FOR_PROCESSING);
        medicalCase.setUpdatedAt(clock.instant());
        cases.save(medicalCase);
    }
    private MedicalCaseEntity visibleResultCase(AuthenticatedUser user, UUID caseId, UUID resultId) {
        MedicalCaseEntity c = cases.findById(caseId).orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        if (!assignments.existsByCaseIdAndUserId(caseId, user.id())) throw new ForbiddenOperationException("Case is not available");
        CaseResultEntity result = results.findById(resultId).orElseThrow(() -> new ResourceNotFoundException("Result not found"));
        if (!result.getMedicalCase().getId().equals(caseId)) throw new ResourceNotFoundException("Result not found");
        return c;
    }
    @Transactional
    public UploadIntent createResult(AuthenticatedUser user, UUID caseId, String filename, String contentType, Long size) {
        requireInternal(user); visibleCase(user, caseId);
        // Un archivo RESULT creado durante una intención pendiente no es todavía
        // un resultado asociado. Solo un case_result confirmado debe bloquear
        // una nueva intención; de lo contrario un intento expirado o fallido
        // impediría reintentar la carga.
        if (results.existsByMedicalCaseId(caseId)) throw new BusinessRuleViolationException("Result already exists");
        validate(filename, contentType, size);
        UUID id=UUID.randomUUID(); String key="result/%s/%s".formatted(caseId,id); Instant now=clock.instant();
        StoredFileEntity entity=new StoredFileEntity(id, cases.findById(caseId).orElseThrow(), key, FileKind.RESULT, filename.trim(), contentType.trim(), size);
        StoredFileEntity saved=files.save(entity); StoragePort.UploadUrlResult url=storage.generateUploadUrl(key, contentType, properties.uploadTtlMinutes());
        return new UploadIntent(
                new FileView(saved.getId(), saved.getOriginalFilename(), saved.getContentType(), saved.getSizeBytes(), saved.getFileKind()),
                url.uploadUrl(), url.expiresAt());
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
        return new Confirmation(
                new FileView(file.getId(), file.getOriginalFilename(), file.getContentType(), file.getSizeBytes(), file.getFileKind()),
                new UploadedResult(result.getId(), medicalCase.getId(), result.getStatus(), user.id(), result.getUploadedAt()),
                medicalCase.getStatus());
    }
    @Transactional
    public void makeResultAvailable(AuthenticatedUser user, UUID resultId) {
        requireCoordinator(user);
        CaseResultEntity result = results.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Result not found"));
        MedicalCaseEntity medicalCase = result.getMedicalCase();
        if (!assignments.existsByCaseIdAndUserId(medicalCase.getId(), user.id())) {
            throw new ForbiddenOperationException("Coordinator is not assigned to this case");
        }
        if (result.getStatus() != com.healthdocs.backend.domain.model.ResultStatus.UPLOADED
                || medicalCase.getStatus() != CaseStatus.RESULT_UPLOADED) {
            throw new BusinessRuleViolationException("Result is not ready to be published");
        }
        Instant now = clock.instant();
        result.makeAvailable(now, user.id());
        medicalCase.makeResultAvailable(now);
        results.save(result);
        cases.save(medicalCase);
        resultNotificationPublisher.publish(new ResultAvailableEvent(
                ResultAvailableEvent.CURRENT_VERSION, ResultAvailableEvent.EVENT_TYPE,
                UUID.randomUUID(), result.getId(), medicalCase.getId(),
                medicalCase.getPatient().getId(), medicalCase.getPatient().getEmail(),
                now, now, user.id()));
    }
    private MedicalCaseEntity visibleCase(AuthenticatedUser user, UUID id) { MedicalCaseEntity c=cases.findById(id).orElseThrow(()->new ResourceNotFoundException("Case not found")); if(c.getStatus()!=CaseStatus.ENABLED_FOR_PROCESSING || !assignments.existsByCaseIdAndUserId(id,user.id())) throw new ForbiddenOperationException("Case is not available"); return c; }
    private MedicalCaseEntity downloadVisibleCase(AuthenticatedUser user, UUID id) {
        MedicalCaseEntity c = cases.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        // El insumo sigue siendo necesario para revisar, comparar o reprocesar el caso
        // incluso después de cargar o publicar el resultado. Por eso la autorización
        // de descarga cubre todo el ciclo de trabajo posterior a la asignación.
        boolean assignedCase = c.getStatus() == CaseStatus.ENABLED_FOR_PROCESSING
                || c.getStatus() == CaseStatus.RESULT_UPLOADED
                || c.getStatus() == CaseStatus.RESULT_AVAILABLE;
        boolean assignedInternalCase = assignedCase
                && assignments.existsByCaseIdAndUserId(id, user.id());
        boolean coordinatorReviewingPendingCase = user.role() == Role.COORDINATOR
                && c.getStatus() == CaseStatus.FILES_RECEIVED;
        if (!assignedInternalCase && !coordinatorReviewingPendingCase) {
            throw new ForbiddenOperationException("Case is not available");
        }
        return c;
    }
    private void requireInternal(AuthenticatedUser u) { if(u==null || !u.enabled() || (u.role()!=Role.RESEARCHER && u.role()!=Role.COORDINATOR)) throw new ForbiddenOperationException("Internal access required"); }
    private void requireCoordinator(AuthenticatedUser u) { if(u==null || !u.enabled() || u.role()!=Role.COORDINATOR) throw new ForbiddenOperationException("Coordinator access required"); }
    private void validate(String f,String m,Long s) { if(f==null||f.isBlank()||f.length()>255||f.contains("..")||f.contains("/")||f.contains("\\")) throw new IllegalArgumentException("Invalid originalFilename"); if(m==null||!properties.allowedMimeTypes().contains(m.split(";",2)[0].trim())) throw new UnsupportedMediaTypeException("MIME type is not allowed"); if(s==null||s<1) throw new IllegalArgumentException("sizeBytes must be positive"); if(s>properties.resultMaxSizeBytes()) throw new PayloadTooLargeException("File exceeds maximum size"); }
    private CaseView view(MedicalCaseEntity c) {
        CaseResultEntity result = results.findByMedicalCaseId(c.getId()).orElse(null);
        ResultView resultView = result == null ? null : new ResultView(
                result.getId(), result.getFile().getOriginalFilename(), result.getStatus(),
                result.getUploadedBy(), result.getUploadedAt(), result.getAvailableAt());
        return new CaseView(c.getId(), c.getReferenceCode(), c.getStudyName(), c.getStatus(), c.getCreatedAt(),
                files.findByMedicalCaseId(c.getId()).stream()
                        .map(f->new FileView(f.getId(),f.getOriginalFilename(),f.getContentType(),f.getSizeBytes(),f.getFileKind())).toList(),
                resultView);
    }
    private static String sanitize(String s){return s.replace("\\","_").replace("/","_");}
    public record FileView(UUID id,String originalFilename,String contentType,Long sizeBytes,FileKind fileKind){}
    public record CaseView(UUID id,String referenceCode,String studyName,CaseStatus status,Instant createdAt,List<FileView> files,ResultView result){}
    public record ResultView(UUID id,String originalFilename,com.healthdocs.backend.domain.model.ResultStatus status,UUID uploadedBy,Instant uploadedAt,Instant availableAt){}
    public record Download(String downloadUrl,Instant expiresAt){}
    public record PatientResult(UUID id, UUID caseId, Instant availableAt, Download download){}
    public record UploadIntent(FileView file,String uploadUrl,Instant expiresAt){}
    public record UploadedResult(UUID id,UUID caseId,com.healthdocs.backend.domain.model.ResultStatus status,UUID uploadedBy,Instant uploadedAt){}
    public record Confirmation(FileView file,UploadedResult result,CaseStatus caseStatus){}
}
