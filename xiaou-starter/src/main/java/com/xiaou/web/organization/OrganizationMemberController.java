package com.xiaou.web.organization;

import com.xiaou.aecp.identity.organization.OrganizationMember;
import com.xiaou.aecp.identity.organization.OrganizationUserCandidate;
import com.xiaou.aecp.identity.organization.OrganizationMemberService;
import com.xiaou.web.auth.BearerSessionAuthenticator;
import com.xiaou.web.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/members")
public class OrganizationMemberController {

    private final BearerSessionAuthenticator authenticator;
    private final OrganizationMemberService service;

    public OrganizationMemberController(
            BearerSessionAuthenticator authenticator,
            OrganizationMemberService service) {
        this.authenticator = authenticator;
        this.service = service;
    }

    @GetMapping("/candidates")
    public ApiResponse<List<OrganizationUserCandidateResponse>> searchCandidates(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable("organizationId") String organizationId,
            @RequestParam(value = "employee_no", defaultValue = "") String employeeNo) {
        String actor = authenticator.requireUsername(authorization);
        List<OrganizationUserCandidateResponse> items = service
                .searchMemberCandidates(actor, organizationId, employeeNo)
                .stream()
                .map(OrganizationUserCandidateResponse::from)
                .toList();
        return ApiResponse.success("查询成功", items);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrganizationMemberResponse>> addMember(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable("organizationId") String organizationId,
            @Valid @RequestBody AddOrganizationMemberRequest request) {
        String actor = authenticator.requireUsername(authorization);
        OrganizationMember member = service.addMember(
                actor, organizationId, request.userId(), request.role());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        201, "成员添加成功", OrganizationMemberResponse.from(member)));
    }

    @GetMapping
    public ApiResponse<OrganizationMemberListResponse> listMembers(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable("organizationId") String organizationId) {
        String actor = authenticator.requireUsername(authorization);
        List<OrganizationMemberResponse> items = service.listMembers(actor, organizationId).stream()
                .map(OrganizationMemberResponse::from)
                .toList();
        return ApiResponse.success("查询成功", new OrganizationMemberListResponse(items, items.size()));
    }

    @PatchMapping("/{userId}")
    public ApiResponse<OrganizationMemberResponse> changeRole(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable("organizationId") String organizationId,
            @PathVariable("userId") String userId,
            @Valid @RequestBody UpdateOrganizationMemberRoleRequest request) {
        String actor = authenticator.requireUsername(authorization);
        OrganizationMember member = service.changeRole(actor, organizationId, userId, request.role());
        return ApiResponse.success("成员角色更新成功", OrganizationMemberResponse.from(member));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<RemoveOrganizationMemberResponse> removeMember(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable("organizationId") String organizationId,
            @PathVariable("userId") String userId) {
        String actor = authenticator.requireUsername(authorization);
        service.removeMember(actor, organizationId, userId);
        return ApiResponse.success("成员移除成功", new RemoveOrganizationMemberResponse(true));
    }
}
