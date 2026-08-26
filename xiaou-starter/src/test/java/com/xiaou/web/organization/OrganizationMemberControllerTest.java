package com.xiaou.web.organization;

import com.xiaou.aecp.identity.organization.OrganizationMember;
import com.xiaou.aecp.identity.organization.OrganizationMemberError;
import com.xiaou.aecp.identity.organization.OrganizationMemberService;
import com.xiaou.aecp.identity.organization.OrganizationRole;
import com.xiaou.aecp.identity.organization.OrganizationUserCandidate;
import com.xiaou.web.auth.BearerSessionAuthenticator;
import com.xiaou.web.auth.InvalidSessionException;
import org.junit.jupiter.api.Test;
import org.redisson.client.RedisException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.ALREADY_ACTIVE;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.FORBIDDEN;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.LAST_ADMINISTRATOR;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.MEMBER_NOT_FOUND;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.ORGANIZATION_NOT_FOUND;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.USER_NOT_FOUND;
import static com.xiaou.aecp.identity.organization.OrganizationRole.AUDITOR;
import static com.xiaou.aecp.identity.organization.OrganizationRole.ENGINEER;
import static com.xiaou.aecp.identity.organization.OrganizationRole.ORGANIZATION_ADMIN;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrganizationMemberController.class)
@Import(OrganizationMemberExceptionHandler.class)
class OrganizationMemberControllerTest {

    private static final String ORGANIZATION = "ORG-DEMO-COMAC";
    private static final String COLLECTION = "/api/v1/organizations/" + ORGANIZATION + "/members";
    private static final String MEMBER = COLLECTION + "/USR-DEMO-ENG-A";
    private static final String AUTHORIZATION = "Bearer admin-token";
    private static final String ACTOR = "demo-admin-a";
    private static final Instant JOINED_AT = Instant.parse("2026-08-25T02:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrganizationMemberService service;

    @MockBean
    private BearerSessionAuthenticator authenticator;

    @Test
    void searchReturnsCandidatesByEmployeeNo() throws Exception {
        authorize();
        when(service.searchMemberCandidates(ACTOR, ORGANIZATION, "A-1001"))
                .thenReturn(List.of(new OrganizationUserCandidate("USR-DEMO-EMP-1001", "A-1001", "张工", true)));

        mockMvc.perform(get(COLLECTION + "/candidates")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("employee_no", "A-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].user_id").value("USR-DEMO-EMP-1001"))
                .andExpect(jsonPath("$.data[0].employee_no").value("A-1001"))
                .andExpect(jsonPath("$.data[0].display_name").value("张工"))
                .andExpect(jsonPath("$.data[0].already_member").value(true));
    }

    @Test
    void addReturnsHttpAndBodyStatus201WithSnakeCaseMember() throws Exception {
        authorize();
        when(service.addMember(ACTOR, ORGANIZATION, "USR-DEMO-ENG-A", ENGINEER))
                .thenReturn(engineer());

        mockMvc.perform(post(COLLECTION)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"USR-DEMO-ENG-A\",\"role\":\"ENGINEER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.user_id").value("USR-DEMO-ENG-A"))
                .andExpect(jsonPath("$.data.display_name").value("演示工程师 A"))
                .andExpect(jsonPath("$.data.role").value("ENGINEER"))
                .andExpect(jsonPath("$.data.joined_at").value("2026-08-25T02:00:00Z"));
    }

    @Test
    void listReturnsOnlyItemsAndTotalWithStableServiceOrder() throws Exception {
        authorize();
        when(service.listMembers(ACTOR, ORGANIZATION)).thenReturn(List.of(admin(), engineer()));

        mockMvc.perform(get(COLLECTION).header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.items[0].user_id").value("USR-DEMO-ADMIN-A"))
                .andExpect(jsonPath("$.data.items[1].user_id").value("USR-DEMO-ENG-A"))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void patchReturnsUpdatedRole() throws Exception {
        authorize();
        OrganizationMember updated = new OrganizationMember(
                ORGANIZATION, "USR-DEMO-ENG-A", "demo-engineer-a", "演示工程师 A", AUDITOR, JOINED_AT);
        when(service.changeRole(ACTOR, ORGANIZATION, "USR-DEMO-ENG-A", AUDITOR)).thenReturn(updated);

        mockMvc.perform(patch(MEMBER)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"AUDITOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.role").value("AUDITOR"));
    }

    @Test
    void deleteReturnsRemovedTrue() throws Exception {
        authorize();

        mockMvc.perform(delete(MEMBER).header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.removed").value(true));
    }

    @Test
    void blankUserIdAndUnknownRoleReturn400() throws Exception {
        authorize();

        mockMvc.perform(post(COLLECTION)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"\",\"role\":\"ENGINEER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(patch(MEMBER)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void missingOrExpiredSessionReturns401() throws Exception {
        when(authenticator.requireUsername(AUTHORIZATION)).thenThrow(new InvalidSessionException());

        mockMvc.perform(get(COLLECTION).header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(content().string(not(containsString("admin-token"))));
    }

    @Test
    void nonAdminAndCrossOrganizationRequestsReturn403() throws Exception {
        authorize();
        when(service.listMembers(ACTOR, ORGANIZATION))
                .thenThrow(new OrganizationMemberError(FORBIDDEN));

        mockMvc.perform(get(COLLECTION).header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void missingOrganizationUserOrMemberReturn404() throws Exception {
        authorize();
        when(service.listMembers(ACTOR, ORGANIZATION))
                .thenThrow(new OrganizationMemberError(ORGANIZATION_NOT_FOUND));
        mockMvc.perform(get(COLLECTION).header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));

        when(service.addMember(ACTOR, ORGANIZATION, "USR-DEMO-ENG-A", ENGINEER))
                .thenThrow(new OrganizationMemberError(USER_NOT_FOUND));
        mockMvc.perform(post(COLLECTION)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"USR-DEMO-ENG-A\",\"role\":\"ENGINEER\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));

        when(service.changeRole(ACTOR, ORGANIZATION, "USR-DEMO-ENG-A", AUDITOR))
                .thenThrow(new OrganizationMemberError(MEMBER_NOT_FOUND));
        mockMvc.perform(patch(MEMBER)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"AUDITOR\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void duplicateMemberAndLastAdministratorReturn409() throws Exception {
        authorize();
        when(service.addMember(ACTOR, ORGANIZATION, "USR-DEMO-ENG-A", ENGINEER))
                .thenThrow(new OrganizationMemberError(ALREADY_ACTIVE));
        mockMvc.perform(post(COLLECTION)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"USR-DEMO-ENG-A\",\"role\":\"ENGINEER\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));

        doThrow(new OrganizationMemberError(LAST_ADMINISTRATOR))
                .when(service).removeMember(ACTOR, ORGANIZATION, "USR-DEMO-ENG-A");
        mockMvc.perform(delete(MEMBER).header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void redisOrDatabaseUnavailableReturns503WithoutInternalDetails() throws Exception {
        when(authenticator.requireUsername(AUTHORIZATION))
                .thenThrow(new RedisException("redis://secret-host:6379/session:key"));
        mockMvc.perform(get(COLLECTION).header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(content().string(not(containsString("secret-host"))))
                .andExpect(content().string(not(containsString("session:key"))));

        org.mockito.Mockito.reset(authenticator);
        authorize();
        when(service.listMembers(ACTOR, ORGANIZATION))
                .thenThrow(new DataAccessResourceFailureException("jdbc:mysql://secret/select SQL"));
        mockMvc.perform(get(COLLECTION).header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(content().string(not(containsString("jdbc:mysql"))))
                .andExpect(content().string(not(containsString("select SQL"))));
    }

    @Test
    void unexpectedFailureReturns500WithoutInternalDetails() throws Exception {
        authorize();
        when(service.listMembers(ACTOR, ORGANIZATION))
                .thenThrow(new IllegalStateException("internal-detail"));

        mockMvc.perform(get(COLLECTION).header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(content().string(not(containsString("internal-detail"))));
    }

    private void authorize() {
        when(authenticator.requireUsername(AUTHORIZATION)).thenReturn(ACTOR);
    }

    private static OrganizationMember admin() {
        return new OrganizationMember(
                ORGANIZATION, "USR-DEMO-ADMIN-A", "demo-admin-a", "演示管理员 A",
                ORGANIZATION_ADMIN, JOINED_AT.minusSeconds(60));
    }

    private static OrganizationMember engineer() {
        return new OrganizationMember(
                ORGANIZATION, "USR-DEMO-ENG-A", "demo-engineer-a", "演示工程师 A", ENGINEER, JOINED_AT);
    }
}
