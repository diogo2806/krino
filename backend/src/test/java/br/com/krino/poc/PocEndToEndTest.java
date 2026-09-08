package br.com.krino.poc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PocEndToEndTest {

    private static final String ADMIN_USERNAME = "poc.admin";
    private static final String ADMIN_PASSWORD = "PocAdmin#2026!";
    private static final String USER_PASSWORD = "PocUsuario#2026!";
    private static final int YEAR = 2026;
    private static final String SCHOOL_A_CODE = "POC-EM-01";
    private static final String SCHOOL_B_CODE = "POC-EM-02";
    private static final LocalDate LESSON_DATE = LocalDate.of(2026, 3, 2); // segunda-feira

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("krino_poc_e2e")
            .withUsername("krino")
            .withPassword("krino");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("krino.security.jwt-secret", () -> "poc-e2e-secret-key-with-more-than-thirty-two-bytes-2026");
        registry.add("krino.security.bootstrap-admin-username", () -> ADMIN_USERNAME);
        registry.add("krino.security.bootstrap-admin-password", () -> ADMIN_PASSWORD);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveExecutarCenarioIntegradoDaPocComDadosFicticiosECalculoConhecido() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        // Autenticação não é contornada: sem JWT a API protegida continua bloqueada.
        mockMvc.perform(get("/api/secretaria/schools"))
                .andExpect(status().isUnauthorized());

        long schoolA = postJson("/api/secretaria/schools", adminToken, Map.of(
                "code", SCHOOL_A_CODE,
                "name", "Escola Municipal POC Horizonte",
                "address", "Rua Fictícia 100")).get("id").asLong();
        long schoolB = postJson("/api/secretaria/schools", adminToken, Map.of(
                "code", SCHOOL_B_CODE,
                "name", "Escola Municipal POC Caminhos",
                "address", "Avenida Fictícia 200")).get("id").asLong();

        long classA = postJson("/api/secretaria/classes", adminToken, Map.of(
                "schoolId", schoolA, "academicYear", YEAR, "name", "7º A", "stage", "7º ano", "shift", "MORNING")).get("id").asLong();
        long classB = postJson("/api/secretaria/classes", adminToken, Map.of(
                "schoolId", schoolB, "academicYear", YEAR, "name", "7º B", "stage", "7º ano", "shift", "AFTERNOON")).get("id").asLong();
        assertThat(classA).isPositive();
        assertThat(classB).isPositive();

        JsonNode components = getJson("/api/secretaria/components", adminToken);
        long mathComponent = findIdBy(components, "code", "MAT");

        long professor = postJson("/api/secretaria/professionals", adminToken, Map.of(
                "schoolId", schoolA, "registration", "PROF-POC-001", "name", "Professor POC Matemática", "professionalType", "TEACHER")).get("id").asLong();
        postJson("/api/secretaria/teacher-assignments", adminToken, Map.of(
                "professionalId", professor, "classId", classA, "componentId", mathComponent,
                "validFrom", "2026-01-01", "validUntil", "2026-12-31"));
        postJson("/api/secretaria/calendar", adminToken, Map.of(
                "schoolId", schoolA, "academicDate", LESSON_DATE.toString(), "schoolDay", true, "description", "Dia letivo POC"));
        postJson("/api/secretaria/schedules", adminToken, Map.of(
                "classId", classA, "componentId", mathComponent, "professionalId", professor, "dayOfWeek", 1,
                "startTime", "07:00:00", "endTime", "07:50:00", "validFrom", "2026-01-01", "validUntil", "2026-12-31"));

        List<Long> studentIds = new ArrayList<>();
        List<Long> enrollmentIds = new ArrayList<>();
        List<String> registrations = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            String registration = "ALUNO-POC-%03d".formatted(index);
            registrations.add(registration);
            long studentId = postJson("/api/secretaria/students", adminToken, Map.of(
                    "schoolId", schoolA,
                    "registration", registration,
                    "name", "Estudante POC %02d".formatted(index),
                    "birthDate", "2013-04-15",
                    "guardianName", "Responsável POC %02d".formatted(index),
                    "guardianProfession", "Profissão fictícia")).get("id").asLong();
            studentIds.add(studentId);
            long enrollmentId = postJson("/api/secretaria/enrollments", adminToken, Map.of(
                    "studentId", studentId, "classId", classA, "enrollmentType", "ENROLLMENT", "enrollmentDate", "2026-02-02"))
                    .get("id").asLong();
            enrollmentIds.add(enrollmentId);
        }

        // Mantém a segunda escola com dados reais no cenário e valida isolamento de escopo.
        long schoolBStudent = postJson("/api/secretaria/students", adminToken, Map.of(
                "schoolId", schoolB, "registration", "ALUNO-POC-B-001", "name", "Estudante POC Escola B", "birthDate", "2013-06-01",
                "guardianName", "Responsável Escola B", "guardianProfession", "Profissão fictícia")).get("id").asLong();
        postJson("/api/secretaria/enrollments", adminToken, Map.of(
                "studentId", schoolBStudent, "classId", classB, "enrollmentType", "ENROLLMENT", "enrollmentDate", "2026-02-02"));

        JsonNode roles = getJson("/api/admin/roles", adminToken);
        long professorRole = findIdBy(roles, "name", "Professor");
        long guardianRole = findIdBy(roles, "name", "Responsável legal");
        long transportRole = findIdBy(roles, "name", "Estudante do transporte");

        long professorUser = createUser(adminToken, "poc.professor", "Professor POC");
        assignRole(adminToken, professorUser, professorRole, "SCHOOL", SCHOOL_A_CODE);
        putJson("/api/secretaria/professionals/" + professor + "/user-link", adminToken, Map.of("username", "poc.professor"));

        long guardianUser = createUser(adminToken, "poc.responsavel", "Responsável POC");
        assignRole(adminToken, guardianUser, guardianRole, "USER", null);
        postNoBody("/api/admin/users/" + guardianUser + "/linked-students/" + studentIds.getFirst(), adminToken);

        long transportUser = createUser(adminToken, "poc.transporte", "Estudante Transporte POC");
        assignRole(adminToken, transportUser, transportRole, "USER", null);

        String professorToken = login("poc.professor", USER_PASSWORD);
        String guardianToken = login("poc.responsavel", USER_PASSWORD);
        String transportToken = login("poc.transporte", USER_PASSWORD);

        // O professor tem escopo somente na escola A e não consegue consultar a escola B.
        mockMvc.perform(get("/api/secretaria/students")
                        .param("schoolId", Long.toString(schoolB))
                        .param("year", Integer.toString(YEAR))
                        .param("search", "")
                        .header(HttpHeaders.AUTHORIZATION, bearer(professorToken)))
                .andExpect(status().isForbidden());

        long diaryId = postJson("/api/diaries", adminToken, Map.of(
                "classId", classA, "componentId", mathComponent, "mode", "FINAL_YEARS", "responsibleProfessionalId", professor,
                "validFrom", "2026-02-01", "validUntil", "2026-12-15")).get("id").asLong();

        List<Map<String, Object>> attendance = new ArrayList<>();
        for (Long enrollmentId : enrollmentIds) {
            attendance.add(Map.of("enrollmentId", enrollmentId, "status", "PRESENT"));
        }
        JsonNode lesson = putJson("/api/diaries/" + diaryId + "/lessons/" + LESSON_DATE + "/1", professorToken, Map.of(
                "period", 1,
                "content", "Números inteiros e resolução de problemas",
                "planningNotes", "Aula POC executada no calendário e horário válidos.",
                "attendance", attendance));
        assertThat(lesson.get("attendance").size()).isEqualTo(10);

        long diaryAssessment = postJson("/api/diaries/" + diaryId + "/assessments", professorToken, Map.of(
                "period", 1, "title", "Avaliação POC Bimestre 1", "assessmentDate", LESSON_DATE.toString(), "maxScore", 10)).get("id").asLong();
        putJson("/api/diaries/" + diaryId + "/assessments/" + diaryAssessment + "/grades", professorToken,
                List.of(Map.of("enrollmentId", enrollmentIds.getFirst(), "score", 8.5, "observation", "Resultado fictício POC")));

        JsonNode schoolDocument = getJson("/api/secretaria/documents/ENROLLMENT_DECLARATION?schoolId=" + schoolA
                + "&year=" + YEAR + "&studentId=" + studentIds.getFirst(), adminToken);
        assertThat(schoolDocument.get("type").asText()).isEqualTo("ENROLLMENT_DECLARATION");

        // Controle de acesso: cartão, identificação e sincronização offline idempotente.
        JsonNode card = putJson("/api/access-control/students/" + studentIds.getFirst() + "/card", adminToken, Map.of());
        String accessCode = card.get("code").asText();
        JsonNode identified = postJson("/api/access-control/identify", adminToken, Map.of("code", accessCode));
        assertThat(identified.get("studentId").asLong()).isEqualTo(studentIds.getFirst());

        String clientEventId = "4d9c8b6f-1f5a-4e15-98ff-160b64a3d101";
        Map<String, Object> accessEvent = Map.of(
                "clientEventId", clientEventId,
                "studentId", studentIds.getFirst(),
                "schoolId", schoolA,
                "classId", classA,
                "eventType", "ENTRY",
                "capturedAt", "2026-03-02T07:15:00-03:00",
                "capturedOffline", true,
                "sourceType", "QR",
                "deviceId", "POC-TABLET-01");
        JsonNode firstSync = postJson("/api/access-control/sync", adminToken, List.of(accessEvent));
        JsonNode secondSync = postJson("/api/access-control/sync", adminToken, List.of(accessEvent));
        assertThat(firstSync.get(0).get("duplicate").asBoolean()).isFalse();
        assertThat(secondSync.get(0).get("duplicate").asBoolean()).isTrue();

        // Portal do responsável usa o vínculo real e apresenta avaliação/frequência do estudante vinculado.
        JsonNode linkedStudents = getJson("/api/family-portal/students", guardianToken);
        assertThat(linkedStudents.size()).isEqualTo(1);
        assertThat(linkedStudents.get(0).get("id").asLong()).isEqualTo(studentIds.getFirst());
        JsonNode reportCard = getJson("/api/family-portal/students/" + studentIds.getFirst() + "/report-card?year=" + YEAR + "&period=1", guardianToken);
        assertThat(reportCard.get("assessments").size()).isEqualTo(1);
        JsonNode familyAttendance = getJson("/api/family-portal/students/" + studentIds.getFirst() + "/attendance?year=" + YEAR + "&period=1", guardianToken);
        assertThat(familyAttendance.get("totalLessons").asInt()).isEqualTo(1);
        JsonNode accessNotifications = getJson("/api/family-portal/students/" + studentIds.getFirst() + "/notifications", guardianToken);
        assertThat(accessNotifications).isNotEmpty();

        // Transporte universitário: solicitar, anexar documentos, submeter, analisar, aprovar e emitir carteirinha.
        JsonNode transportRequest = postJson("/api/transport/requests", transportToken, Map.of(
                "fullName", "Estudante Transporte POC",
                "personalDocument", "DOC-POC-0001",
                "birthDate", "2004-05-20",
                "phone", "21999990000",
                "courseType", "UNIVERSITY",
                "courseName", "Sistemas de Informação POC",
                "institutionName", "Universidade Fictícia POC",
                "days", List.of("MONDAY", "WEDNESDAY", "FRIDAY")));
        long transportRequestId = transportRequest.get("id").asLong();
        upload(transportRequestId, "PHOTO", "foto-poc.png", "image/png", "PNG-POC".getBytes(StandardCharsets.UTF_8), transportToken);
        upload(transportRequestId, "ENROLLMENT_PROOF", "matricula-poc.pdf", "application/pdf", "%PDF-POC".getBytes(StandardCharsets.UTF_8), transportToken);
        postNoBodyExpectJson("/api/transport/requests/" + transportRequestId + "/submit", transportToken);
        postNoBodyExpectJson("/api/transport/admin/requests/" + transportRequestId + "/start-review", adminToken);
        postJson("/api/transport/admin/requests/" + transportRequestId + "/approve", adminToken, Map.of("validUntil", "2099-12-31"));
        putJson("/api/transport/admin/card-art", adminToken, Map.of(
                "name", "Arte POC", "headerText", "Transporte Universitário POC", "footerText", "Dados exclusivamente fictícios", "accentColor", "#173B57", "approved", true));
        JsonNode transportCard = getJson("/api/transport/card", transportToken);
        assertThat(transportCard.get("request").get("status").asText()).isEqualTo("APPROVED");

        // Avaliação em rede com resultado conhecido: 10 gabaritos válidos, 7 acertos e 3 erros em Q1.
        long assessmentId = postJson("/api/assessments", adminToken, Map.of(
                "name", "Avaliação Diagnóstica POC 2026",
                "stage", "DIAGNOSTIC",
                "academicYear", YEAR,
                "gradeStage", "7º ano",
                "componentId", mathComponent,
                "applicationManual", "Aplicação fictícia para POC integrada.",
                "instructions", "Responder apenas A ou B no cenário automatizado.")).get("id").asLong();
        putJson("/api/assessments/" + assessmentId + "/questions", adminToken,
                List.of(Map.of("sequenceNumber", 1, "descriptor", "D-POC-01", "skill", "Habilidade POC 01", "correctOption", "A")));
        JsonNode organization = postJson("/api/assessments/" + assessmentId + "/organization", adminToken, Map.of("classIds", List.of(classA)));
        assertThat(organization.get("students").asInt()).isEqualTo(10);

        List<Map<String, Object>> answerSheets = new ArrayList<>();
        for (int index = 0; index < registrations.size(); index++) {
            Map<String, String> answers = Map.of("1", index < 7 ? "A" : "B");
            Map<String, Object> sheet = new LinkedHashMap<>();
            sheet.put("registration", registrations.get(index));
            sheet.put("answers", answers);
            answerSheets.add(sheet);
        }
        JsonNode imported = postJson("/api/assessments/" + assessmentId + "/answer-sheets", adminToken,
                Map.of("sourceType", "MANUAL", "sheets", answerSheets));
        assertThat(imported.get("valid").asInt()).isEqualTo(10);
        assertThat(imported.get("invalid").asInt()).isZero();

        JsonNode validation = getJson("/api/assessments/" + assessmentId + "/validation", adminToken);
        assertThat(validation.get("valid").asInt()).isEqualTo(10);
        JsonNode processing = postNoBodyExpectJson("/api/assessments/" + assessmentId + "/process", adminToken);
        assertThat(processing.get("status").asText()).isEqualTo("COMPLETED");

        JsonNode networkResults = getJson("/api/assessments/" + assessmentId + "/results?level=NETWORK", adminToken);
        assertThat(networkResults).hasSize(1);
        assertThat(networkResults.get(0).get("students").asInt()).isEqualTo(10);
        assertThat(networkResults.get(0).get("correctAnswers").asInt()).isEqualTo(7);
        assertThat(networkResults.get(0).get("totalQuestions").asInt()).isEqualTo(10);
        assertThat(networkResults.get(0).get("scorePercent").decimalValue()).isEqualByComparingTo("70.00");

        getJson("/api/assessments/" + assessmentId + "/results?level=SCHOOL&schoolId=" + schoolA, adminToken);
        getJson("/api/assessments/" + assessmentId + "/results?level=CLASS&classId=" + classA, adminToken);
        getJson("/api/assessments/" + assessmentId + "/results?level=STUDENT&studentId=" + studentIds.getFirst(), adminToken);

        JsonNode dashboard = getJson("/api/reports/assessments/" + assessmentId + "/dashboard", adminToken);
        assertThat(dashboard.get("scorePercent").decimalValue()).isEqualByComparingTo("70.00");

        MvcResult export = mockMvc.perform(get("/api/reports/assessments/{assessmentId}/export", assessmentId)
                        .param("report", "dashboard")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(export.getResponse().getContentAsByteArray()).isNotEmpty();

        JsonNode administrationExport = getJson("/api/admin/data-export", adminToken);
        assertThat(administrationExport).isNotNull();
        JsonNode audit = getJson("/api/admin/audit?limit=200", adminToken);
        assertThat(audit.toString()).contains("ASSESSMENT_PROCESS");
        assertThat(audit.toString()).contains("ADMINISTRATION_DATA_EXPORTED");
    }

    private long createUser(String adminToken, String username, String displayName) throws Exception {
        return postJson("/api/admin/users", adminToken, Map.of(
                "username", username, "displayName", displayName, "password", USER_PASSWORD)).get("id").asLong();
    }

    private void assignRole(String adminToken, long userId, long roleId, String scopeType, String scopeReference) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roleId", roleId);
        body.put("scopeType", scopeType);
        if (scopeReference != null) body.put("scopeReference", scopeReference);
        postJson("/api/admin/users/" + userId + "/roles", adminToken, body);
    }

    private long findIdBy(JsonNode array, String field, String expected) {
        for (JsonNode item : array) {
            if (expected.equals(item.path(field).asText())) return item.path("id").asLong();
        }
        throw new AssertionError("Item não encontrado: " + field + "=" + expected);
    }

    private String login(String username, String password) throws Exception {
        JsonNode response = json(HttpMethod.POST, "/api/auth/login", null, Map.of("username", username, "password", password));
        return response.get("token").asText();
    }

    private JsonNode getJson(String path, String token) throws Exception {
        return json(HttpMethod.GET, path, token, null);
    }

    private JsonNode postJson(String path, String token, Object body) throws Exception {
        return json(HttpMethod.POST, path, token, body);
    }

    private JsonNode putJson(String path, String token, Object body) throws Exception {
        return json(HttpMethod.PUT, path, token, body);
    }

    private JsonNode postNoBodyExpectJson(String path, String token) throws Exception {
        return json(HttpMethod.POST, path, token, null);
    }

    private void postNoBody(String path, String token) throws Exception {
        mockMvc.perform(request(HttpMethod.POST, path).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().is2xxSuccessful());
    }

    private JsonNode json(HttpMethod method, String path, String token, Object body) throws Exception {
        var builder = request(method, path).contentType(MediaType.APPLICATION_JSON);
        if (token != null) builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
        if (body != null) builder.content(objectMapper.writeValueAsBytes(body));
        MvcResult result = mockMvc.perform(builder)
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (content.isBlank()) return objectMapper.createObjectNode();
        return objectMapper.readTree(content);
    }

    private void upload(long requestId, String type, String filename, String contentType, byte[] content, String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, content);
        mockMvc.perform(multipart("/api/transport/requests/{requestId}/documents/{type}", requestId, type)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
