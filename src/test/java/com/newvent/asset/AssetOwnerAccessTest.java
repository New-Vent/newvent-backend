package com.newvent.asset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import com.newvent.asset.controller.AdminAssetController;
import com.newvent.asset.service.AssetService;
import com.newvent.auth.dto.AuthUser;
import com.newvent.event.service.EventOwnerCheck;

/**
 * 남의 이벤트 이미지를 건드릴 수 없다.
 *
 * ★★ SecurityConfig 의 {@code /api/admin/** → ROLE_ADMIN} 은 "관리자인가" 만 본다.
 *   "이 이벤트의 관리자인가" 는 안 본다. 그것만 믿었더니 아무 관리자나 eventId 만 알면
 *   남의 이벤트 이미지를 올리고 · 보고 · 지울 수 있었다 (리뷰에서 잡혔다).
 *
 * ★ 거절이 **서비스를 건드리기 전에** 일어나는지까지 본다.
 *   나중에 막으면 파일이 이미 디스크에 써진 뒤일 수 있다.
 *   GenerationOwnerAccessTest 가 같은 모양으로 같은 것을 본다.
 */
class AssetOwnerAccessTest {

    final AssetService assets = mock(AssetService.class);
    final EventOwnerCheck ownerCheck = mock(EventOwnerCheck.class);
    final AdminAssetController controller = new AdminAssetController(assets, ownerCheck);

    final AuthUser other = AuthUser.admin(9L);

    void notOwner(AuthUser admin) {
        given(ownerCheck.requireOwned(2L, admin)).willThrow(new AccessDeniedException("denied"));
    }

    @Test
    @DisplayName("업로드 — 다른 관리자는 파일이 저장되기 전에 403")
    void uploadRejectsOtherAdmin() {
        notOwner(other);
        // ★ 실제 파일은 필요 없다. **서비스에 닿기 전에** 막히는지를 보는 테스트다
        MultipartFile file = mock(MultipartFile.class);

        assertThatThrownBy(() -> controller.upload(2L, file, null, other))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(assets);
    }

    @Test
    @DisplayName("목록 — 다른 관리자는 403")
    void listRejectsOtherAdmin() {
        notOwner(other);

        assertThatThrownBy(() -> controller.list(2L, other))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(assets);
    }

    @Test
    @DisplayName("삭제 — 다른 관리자는 403, 파일을 건드리지 않는다")
    void deleteRejectsOtherAdmin() {
        notOwner(other);

        assertThatThrownBy(() -> controller.delete(2L, "a".repeat(32), other))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(assets);
    }

    // ★ 인증 정보가 없는 경우도 EventOwnerCheck 가 막는다 — 컨트롤러가 따로 보지 않는다
    @Test
    @DisplayName("인증 정보가 없으면 403")
    void rejectsAnonymous() {
        given(ownerCheck.requireOwned(2L, null)).willThrow(new AccessDeniedException("denied"));

        assertThatThrownBy(() -> controller.list(2L, null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(assets);
    }
}
