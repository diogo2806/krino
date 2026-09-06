package br.com.krino.transport;

import java.nio.file.Paths;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.krino.audit.SecurityAuditService;
import br.com.krino.security.AuthorizationService;
import br.com.krino.security.KrinoUserPrincipal;

@Service
public class UniversityTransportService {

    private static final long MAX_DOCUMENT_SIZE = 5L * 1024 * 1024;
    private static final Set<String> COURSE_TYPES = Set.of("PROFESSIONALIZING", "TECHNICAL", "UNIVERSITY");
    private static final Set<String> DAYS = Set.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> PROOF_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;
    private final SecurityAuditService auditService;

    public UniversityTransportService(JdbcTemplate jdbcTemplate, AuthorizationService authorizationService, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public List<RequestView> myRequests(Authentication authentication) {
        requireSelfRead(authentication);
        return jdbcTemplate.query(baseSelect() + " where r.applicant_user_id = ? order by r.created_at desc, r.id desc", this::mapRequest, userId(authentication));
    }

    public RequestView myRequest(long requestId, Authentication authentication) {
        requireSelfRead(authentication);
        return requireOwnedRequest(requestId, userId(authentication));
    }

    @Transactional
    public RequestView create(RequestInput input, Authentication authentication) {
        requireSelfWrite(authentication);
        validateInput(input);
        long actorId = userId(authentication);
        Long id = jdbcTemplate.queryForObject(
                "insert into university_transport_request (applicant_user_id, full_name, personal_document, birth_date, phone, course_type, course_name, institution_name, monday, tuesday, wednesday, thursday, friday, saturday, sunday) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
                Long.class,
                actorId,
                clean(input.fullName()),
                clean(input.personalDocument()),
                Date.valueOf(input.birthDate()),
                blankToNull(input.phone()),
                normalizeCourseType(input.courseType()),
                clean(input.courseName()),
                clean(input.institutionName()),
                hasDay(input.days(), "MONDAY"), hasDay(input.days(), "TUESDAY"), hasDay(input.days(), "WEDNESDAY"),
                hasDay(input.days(), "THURSDAY"), hasDay(input.days(), "FRIDAY"), hasDay(input.days(), "SATURDAY"), hasDay(input.days(), "SUNDAY"));
        if (id == null) throw new IllegalStateException("Não foi possível criar a solicitação de transporte.");
        addHistory(id, "DRAFT", null, actorId);
        auditService.record(authentication.getName(), "TRANSPORT_REQUEST_CREATED", "UNIVERSITY_TRANSPORT_REQUEST", Long.toString(id), "Solicitação criada em rascunho.");
        return myRequest(id, authentication);
    }

    @Transactional
    public RequestView update(long requestId, RequestInput input, Authentication authentication) {
        requireSelfWrite(authentication);
        validateInput(input);
        long actorId = userId(authentication);
        RequestView current = requireOwnedRequest(requestId, actorId);
        if (!(current.status().equals("DRAFT") || current.status().equals("ADJUSTMENT_REQUESTED"))) {
            throw new IllegalArgumentException("Esta solicitação não pode mais ser alterada pelo estudante.");
        }
        jdbcTemplate.update(
                "update university_transport_request set full_name = ?, personal_document = ?, birth_date = ?, phone = ?, course_type = ?, course_name = ?, institution_name = ?, monday = ?, tuesday = ?, wednesday = ?, thursday = ?, friday = ?, saturday = ?, sunday = ?, updated_at = current_timestamp where id = ?",
                clean(input.fullName()), clean(input.personalDocument()), Date.valueOf(input.birthDate()), blankToNull(input.phone()), normalizeCourseType(input.courseType()), clean(input.courseName()), clean(input.institutionName()),
                hasDay(input.days(), "MONDAY"), hasDay(input.days(), "TUESDAY"), hasDay(input.days(), "WEDNESDAY"), hasDay(input.days(), "THURSDAY"), hasDay(input.days(), "FRIDAY"), hasDay(input.days(), "SATURDAY"), hasDay(input.days(), "SUNDAY"), requestId);
        auditService.record(authentication.getName(), "TRANSPORT_REQUEST_UPDATED", "UNIVERSITY_TRANSPORT_REQUEST", Long.toString(requestId), "Cadastro da solicitação atualizado pelo estudante.");
        return myRequest(requestId, authentication);
    }

    @Transactional
    public DocumentInfo uploadDocument(long requestId, String type, MultipartFile file, Authentication authentication) {
        requireSelfWrite(authentication);
        long actorId = userId(authentication);
        RequestView current = requireOwnedRequest(requestId, actorId);
        if (!(current.status().equals("DRAFT") || current.status().equals("ADJUSTMENT_REQUESTED"))) {
            throw new IllegalArgumentException("Os documentos só podem ser alterados enquanto a solicitação estiver em rascunho ou em ajuste.");
        }
        String normalizedType = normalizeDocumentType(type);
        validateDocument(normalizedType, file);
        String filename = safeFilename(file.getOriginalFilename());
        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        try {
            jdbcTemplate.update(
                    "insert into university_transport_document (request_id, document_type, original_filename, content_type, size_bytes, content) values (?, ?, ?, ?, ?, ?) "
                            + "on conflict (request_id, document_type) do update set original_filename = excluded.original_filename, content_type = excluded.content_type, size_bytes = excluded.size_bytes, content = excluded.content, uploaded_at = current_timestamp",
                    requestId, normalizedType, filename, contentType, file.getSize(), file.getBytes());
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Não foi possível ler o arquivo enviado.");
        }
        auditService.record(authentication.getName(), "TRANSPORT_DOCUMENT_UPDATED", "UNIVERSITY_TRANSPORT_REQUEST", Long.toString(requestId), "Documento " + normalizedType + " atualizado.");
        return documentInfo(requestId, normalizedType);
    }

    public StoredDocument myDocument(long requestId, String type, Authentication authentication) {
        requireSelfRead(authentication);
        requireOwnedRequest(requestId, userId(authentication));
        return storedDocument(requestId, normalizeDocumentType(type));
    }

    @Transactional
    public RequestView submit(long requestId, Authentication authentication) {
        requireSelfWrite(authentication);
        long actorId = userId(authentication);
        RequestView current = requireOwnedRequest(requestId, actorId);
        if (!(current.status().equals("DRAFT") || current.status().equals("ADJUSTMENT_REQUESTED"))) {
            throw new IllegalArgumentException("A solicitação só pode ser enviada quando estiver em rascunho ou aguardando ajuste.");
        }
        requireDocuments(requestId);
        jdbcTemplate.update("update university_transport_request set status = 'SUBMITTED', review_reason = null, submitted_at = current_timestamp, updated_at = current_timestamp where id = ?", requestId);
        addHistory(requestId, "SUBMITTED", null, actorId);
        auditService.record(authentication.getName(), "TRANSPORT_REQUEST_SUBMITTED", "UNIVERSITY_TRANSPORT_REQUEST", Long.toString(requestId), "Solicitação enviada para análise.");
        return myRequest(requestId, authentication);
    }

    public List<RequestView> reviewQueue(String status, Authentication authentication) {
        requireReviewRead(authentication);
        if (status == null || status.isBlank()) {
            return jdbcTemplate.query(baseSelect() + " order by coalesce(r.submitted_at, r.created_at) desc, r.id desc", this::mapRequest);
        }
        String normalized = normalizeStatus(status);
        return jdbcTemplate.query(baseSelect() + " where r.status = ? order by coalesce(r.submitted_at, r.created_at) desc, r.id desc", this::mapRequest, normalized);
    }

    public RequestView reviewRequest(long requestId, Authentication authentication) {
        requireReviewRead(authentication);
        return requireRequest(requestId);
    }

    public StoredDocument reviewDocument(long requestId, String type, Authentication authentication) {
        requireReviewRead(authentication);
        requireRequest(requestId);
        return storedDocument(requestId, normalizeDocumentType(type));
    }

    @Transactional
    public RequestView startReview(long requestId, Authentication authentication) {
        requireReviewWrite(authentication);
        RequestView current = requireRequest(requestId);
        if (!current.status().equals("SUBMITTED")) throw new IllegalArgumentException("Somente solicitações enviadas podem entrar em análise.");
        transition(requestId, "UNDER_REVIEW", null, null, authentication);
        return requireRequest(requestId);
    }

    @Transactional
    public RequestView requestAdjustment(long requestId, ReasonInput input, Authentication authentication) {
        requireReviewWrite(authentication);
        RequestView current = requireRequest(requestId);
        requireReviewable(current);
        String reason = requireReason(input.reason());
        transition(requestId, "ADJUSTMENT_REQUESTED", reason, null, authentication);
        return requireRequest(requestId);
    }

    @Transactional
    public RequestView deny(long requestId, ReasonInput input, Authentication authentication) {
        requireReviewWrite(authentication);
        RequestView current = requireRequest(requestId);
        requireReviewable(current);
        String reason = requireReason(input.reason());
        transition(requestId, "DENIED", reason, null, authentication);
        return requireRequest(requestId);
    }

    @Transactional
    public RequestView approve(long requestId, ApprovalInput input, Authentication authentication) {
        requireReviewWrite(authentication);
        RequestView current = requireRequest(requestId);
        requireReviewable(current);
        requireDocuments(requestId);
        if (input.validUntil() == null || input.validUntil().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Informe uma validade igual ou posterior à data atual.");
        }
        transition(requestId, "APPROVED", null, input.validUntil(), authentication);
        return requireRequest(requestId);
    }

    public CardView myCard(Authentication authentication) {
        requireSelfRead(authentication);
        long actorId = userId(authentication);
        List<RequestView> approved = jdbcTemplate.query(
                baseSelect() + " where r.applicant_user_id = ? and r.status = 'APPROVED' and r.valid_until >= current_date order by r.reviewed_at desc nulls last, r.id desc limit 1",
                this::mapRequest, actorId);
        if (approved.isEmpty()) throw new IllegalArgumentException("Nenhuma carteirinha válida está disponível para sua conta.");
        RequestView request = approved.getFirst();
        documentInfo(request.id(), "PHOTO");
        CardArtView art = activeCardArt();
        if (!art.approved()) throw new IllegalArgumentException("A arte da carteirinha ainda não foi aprovada pela SEDUC.");
        return new CardView(request, art, "/transport/requests/" + request.id() + "/documents/PHOTO");
    }

    public CardView cardForReview(long requestId, Authentication authentication) {
        requireReviewRead(authentication);
        RequestView request = requireRequest(requestId);
        if (!request.status().equals("APPROVED") || request.validUntil() == null) throw new IllegalArgumentException("A carteirinha só está disponível para solicitação aprovada.");
        documentInfo(request.id(), "PHOTO");
        return new CardView(request, activeCardArt(), "/transport/admin/requests/" + request.id() + "/documents/PHOTO");
    }

    public CardArtView cardArt(Authentication authentication) {
        requireReviewRead(authentication);
        return activeCardArt();
    }

    @Transactional
    public CardArtView updateCardArt(CardArtInput input, Authentication authentication) {
        if (!authorizationService.hasPermission(authentication, "TRANSPORT_CARD_ART_WRITE")) {
            throw new AccessDeniedException("Sua conta não possui permissão para configurar a arte da carteirinha.");
        }
        String name = clean(input.name());
        String header = clean(input.headerText());
        String footer = blankToNull(input.footerText());
        String accent = input.accentColor() == null ? "" : input.accentColor().trim();
        if (!accent.matches("^#[0-9A-Fa-f]{6}$")) throw new IllegalArgumentException("Informe a cor de destaque no formato hexadecimal, por exemplo #173B57.");
        long actorId = userId(authentication);
        Long activeId = jdbcTemplate.queryForObject("select id from university_transport_card_art where active = true", Long.class);
        if (activeId == null) throw new IllegalStateException("Não existe arte ativa da carteirinha.");
        jdbcTemplate.update(
                "update university_transport_card_art set name = ?, header_text = ?, footer_text = ?, accent_color = ?, approved = ?, approved_by = ?, approved_at = case when ? then current_timestamp else null end, updated_at = current_timestamp where id = ?",
                name, header, footer, accent, input.approved(), input.approved() ? actorId : null, input.approved(), activeId);
        auditService.record(authentication.getName(), "TRANSPORT_CARD_ART_UPDATED", "UNIVERSITY_TRANSPORT_CARD_ART", Long.toString(activeId), input.approved() ? "Arte parametrizada e aprovada." : "Arte parametrizada e marcada como não aprovada.");
        return activeCardArt();
    }

    private void transition(long requestId, String status, String reason, LocalDate validUntil, Authentication authentication) {
        long actorId = userId(authentication);
        jdbcTemplate.update(
                "update university_transport_request set status = ?, review_reason = ?, valid_until = ?, reviewed_at = current_timestamp, updated_at = current_timestamp where id = ?",
                status, reason, validUntil == null ? null : Date.valueOf(validUntil), requestId);
        addHistory(requestId, status, reason, actorId);
        auditService.record(authentication.getName(), "TRANSPORT_REQUEST_" + status, "UNIVERSITY_TRANSPORT_REQUEST", Long.toString(requestId), "Estado atualizado para " + status + ".");
    }

    private void requireReviewable(RequestView current) {
        if (!(current.status().equals("SUBMITTED") || current.status().equals("UNDER_REVIEW"))) {
            throw new IllegalArgumentException("Esta solicitação não está disponível para decisão neste estado.");
        }
    }

    private void requireDocuments(long requestId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from university_transport_document where request_id = ? and document_type in ('PHOTO', 'ENROLLMENT_PROOF')", Integer.class, requestId);
        if (count == null || count < 2) throw new IllegalArgumentException("Envie a foto e o comprovante de matrícula antes de enviar ou aprovar a solicitação.");
    }

    private RequestView requireOwnedRequest(long requestId, long userId) {
        List<RequestView> rows = jdbcTemplate.query(baseSelect() + " where r.id = ? and r.applicant_user_id = ?", this::mapRequest, requestId, userId);
        if (rows.isEmpty()) throw new AccessDeniedException("A solicitação informada não pertence à sua conta.");
        return rows.getFirst();
    }

    private RequestView requireRequest(long requestId) {
        List<RequestView> rows = jdbcTemplate.query(baseSelect() + " where r.id = ?", this::mapRequest, requestId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Solicitação de transporte não encontrada.");
        return rows.getFirst();
    }

    private String baseSelect() {
        return "select r.*, u.display_name applicant_account_name, "
                + "exists(select 1 from university_transport_document d where d.request_id = r.id and d.document_type = 'PHOTO') has_photo, "
                + "exists(select 1 from university_transport_document d where d.request_id = r.id and d.document_type = 'ENROLLMENT_PROOF') has_enrollment_proof "
                + "from university_transport_request r join app_user u on u.id = r.applicant_user_id";
    }

    private RequestView mapRequest(ResultSet rs, int rowNum) throws SQLException {
        return new RequestView(
                rs.getLong("id"), rs.getLong("applicant_user_id"), rs.getString("applicant_account_name"), rs.getString("full_name"), rs.getString("personal_document"),
                rs.getDate("birth_date").toLocalDate(), rs.getString("phone"), rs.getString("course_type"), rs.getString("course_name"), rs.getString("institution_name"),
                selectedDays(rs), rs.getString("status"), rs.getString("review_reason"), toLocalDate(rs.getDate("valid_until")),
                rs.getBoolean("has_photo"), rs.getBoolean("has_enrollment_proof"), rs.getObject("submitted_at", OffsetDateTime.class), rs.getObject("reviewed_at", OffsetDateTime.class),
                history(rs.getLong("id")));
    }

    private List<HistoryView> history(long requestId) {
        return jdbcTemplate.query(
                "select h.status, h.reason, h.created_at, coalesce(u.display_name, 'Sistema') actor_name from university_transport_history h left join app_user u on u.id = h.actor_user_id where h.request_id = ? order by h.created_at, h.id",
                (rs, rowNum) -> new HistoryView(rs.getString("status"), rs.getString("reason"), rs.getString("actor_name"), rs.getObject("created_at", OffsetDateTime.class)), requestId);
    }

    private List<String> selectedDays(ResultSet rs) throws SQLException {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        if (rs.getBoolean("monday")) result.add("MONDAY");
        if (rs.getBoolean("tuesday")) result.add("TUESDAY");
        if (rs.getBoolean("wednesday")) result.add("WEDNESDAY");
        if (rs.getBoolean("thursday")) result.add("THURSDAY");
        if (rs.getBoolean("friday")) result.add("FRIDAY");
        if (rs.getBoolean("saturday")) result.add("SATURDAY");
        if (rs.getBoolean("sunday")) result.add("SUNDAY");
        return result;
    }

    private StoredDocument storedDocument(long requestId, String type) {
        List<StoredDocument> rows = jdbcTemplate.query(
                "select original_filename, content_type, content from university_transport_document where request_id = ? and document_type = ?",
                (rs, rowNum) -> new StoredDocument(rs.getString("original_filename"), rs.getString("content_type"), rs.getBytes("content")), requestId, type);
        if (rows.isEmpty()) throw new IllegalArgumentException("Documento não encontrado para esta solicitação.");
        return rows.getFirst();
    }

    private DocumentInfo documentInfo(long requestId, String type) {
        List<DocumentInfo> rows = jdbcTemplate.query(
                "select document_type, original_filename, content_type, size_bytes, uploaded_at from university_transport_document where request_id = ? and document_type = ?",
                (rs, rowNum) -> new DocumentInfo(rs.getString("document_type"), rs.getString("original_filename"), rs.getString("content_type"), rs.getLong("size_bytes"), rs.getObject("uploaded_at", OffsetDateTime.class)), requestId, type);
        if (rows.isEmpty()) throw new IllegalArgumentException("Documento não encontrado para esta solicitação.");
        return rows.getFirst();
    }

    private CardArtView activeCardArt() {
        List<CardArtView> rows = jdbcTemplate.query(
                "select id, name, header_text, footer_text, accent_color, approved, approved_at from university_transport_card_art where active = true",
                (rs, rowNum) -> new CardArtView(rs.getLong("id"), rs.getString("name"), rs.getString("header_text"), rs.getString("footer_text"), rs.getString("accent_color"), rs.getBoolean("approved"), rs.getObject("approved_at", OffsetDateTime.class)));
        if (rows.isEmpty()) throw new IllegalArgumentException("A arte da carteirinha ainda não foi configurada.");
        return rows.getFirst();
    }

    private void addHistory(long requestId, String status, String reason, long actorUserId) {
        jdbcTemplate.update("insert into university_transport_history (request_id, status, reason, actor_user_id) values (?, ?, ?, ?)", requestId, status, reason, actorUserId);
    }

    private void validateInput(RequestInput input) {
        if (input == null) throw new IllegalArgumentException("Informe os dados da solicitação.");
        clean(input.fullName()); clean(input.personalDocument()); clean(input.courseName()); clean(input.institutionName());
        if (input.birthDate() == null || input.birthDate().isAfter(LocalDate.now())) throw new IllegalArgumentException("Informe uma data de nascimento válida.");
        normalizeCourseType(input.courseType());
        if (input.days() == null || input.days().isEmpty()) throw new IllegalArgumentException("Selecione pelo menos um dia da semana para uso do transporte.");
        if (input.days().stream().map(this::normalizeDay).anyMatch(day -> !DAYS.contains(day))) throw new IllegalArgumentException("Um dos dias informados é inválido.");
    }

    private void validateDocument(String type, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Selecione um arquivo para enviar.");
        if (file.getSize() > MAX_DOCUMENT_SIZE) throw new IllegalArgumentException("O arquivo deve possuir no máximo 5 MB.");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        Set<String> allowed = type.equals("PHOTO") ? PHOTO_TYPES : PROOF_TYPES;
        if (!allowed.contains(contentType)) {
            throw new IllegalArgumentException(type.equals("PHOTO") ? "A foto deve ser JPEG, PNG ou WEBP." : "O comprovante deve ser PDF, JPEG ou PNG.");
        }
    }

    private String safeFilename(String original) {
        String fallback = "arquivo";
        if (original == null || original.isBlank()) return fallback;
        String name = Paths.get(original).getFileName().toString().replaceAll("[\\p{Cntrl}]", "").trim();
        return name.isBlank() ? fallback : name.substring(0, Math.min(name.length(), 255));
    }

    private String normalizeDocumentType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!(normalized.equals("PHOTO") || normalized.equals("ENROLLMENT_PROOF"))) throw new IllegalArgumentException("Tipo de documento inválido.");
        return normalized;
    }

    private String normalizeCourseType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!COURSE_TYPES.contains(normalized)) throw new IllegalArgumentException("Selecione o tipo de curso: profissionalizante, técnico ou universitário.");
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "SUBMITTED", "UNDER_REVIEW", "ADJUSTMENT_REQUESTED", "APPROVED", "DENIED").contains(normalized)) throw new IllegalArgumentException("Status de solicitação inválido.");
        return normalized;
    }

    private String normalizeDay(String day) { return day == null ? "" : day.trim().toUpperCase(Locale.ROOT); }
    private boolean hasDay(List<String> days, String day) { return days.stream().map(this::normalizeDay).anyMatch(day::equals); }

    private String clean(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Preencha todos os campos obrigatórios da solicitação.");
        return value.trim();
    }

    private String requireReason(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Informe o motivo da decisão.");
        String reason = value.trim();
        if (reason.length() > 1000) throw new IllegalArgumentException("O motivo deve possuir no máximo 1000 caracteres.");
        return reason;
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private LocalDate toLocalDate(Date value) { return value == null ? null : value.toLocalDate(); }

    private long userId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof KrinoUserPrincipal principal)) throw new AccessDeniedException("Usuário autenticado não identificado.");
        return principal.id();
    }

    private void requireSelfRead(Authentication authentication) {
        if (!authorizationService.hasPermission(authentication, "TRANSPORT_REQUEST_READ")) throw new AccessDeniedException("Sua conta não possui permissão para consultar o transporte universitário.");
    }

    private void requireSelfWrite(Authentication authentication) {
        if (!authorizationService.hasPermission(authentication, "TRANSPORT_REQUEST_WRITE")) throw new AccessDeniedException("Sua conta não possui permissão para solicitar transporte universitário.");
    }

    private void requireReviewRead(Authentication authentication) {
        if (!(authorizationService.hasPermission(authentication, "TRANSPORT_REVIEW_READ") || authorizationService.hasPermission(authentication, "TRANSPORT_REVIEW_WRITE"))) throw new AccessDeniedException("Sua conta não possui permissão para analisar solicitações de transporte.");
    }

    private void requireReviewWrite(Authentication authentication) {
        if (!authorizationService.hasPermission(authentication, "TRANSPORT_REVIEW_WRITE")) throw new AccessDeniedException("Sua conta não possui permissão para decidir solicitações de transporte.");
    }

    public record RequestInput(String fullName, String personalDocument, LocalDate birthDate, String phone, String courseType, String courseName, String institutionName, List<String> days) {}
    public record ReasonInput(String reason) {}
    public record ApprovalInput(LocalDate validUntil) {}
    public record DocumentInfo(String type, String filename, String contentType, long sizeBytes, OffsetDateTime uploadedAt) {}
    public record StoredDocument(String filename, String contentType, byte[] content) {}
    public record HistoryView(String status, String reason, String actorName, OffsetDateTime createdAt) {}
    public record RequestView(long id, long applicantUserId, String applicantAccountName, String fullName, String personalDocument, LocalDate birthDate, String phone, String courseType, String courseName, String institutionName, List<String> days, String status, String reviewReason, LocalDate validUntil, boolean hasPhoto, boolean hasEnrollmentProof, OffsetDateTime submittedAt, OffsetDateTime reviewedAt, List<HistoryView> history) {}
    public record CardArtInput(String name, String headerText, String footerText, String accentColor, boolean approved) {}
    public record CardArtView(long id, String name, String headerText, String footerText, String accentColor, boolean approved, OffsetDateTime approvedAt) {}
    public record CardView(RequestView request, CardArtView art, String photoPath) {}
}
