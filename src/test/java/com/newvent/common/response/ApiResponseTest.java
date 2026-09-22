package com.newvent.common.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void 데이터가_있는_성공_응답을_생성한다() {
        String data = "response data";

        ApiResponse<String> response = ApiResponse.success(data);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(data);
        assertThat(response.message()).isNull();
    }

    @Test
    void 메시지가_포함된_성공_응답을_생성한다() {
        String data = "response data";
        String message = "요청에 성공했습니다.";

        ApiResponse<String> response = ApiResponse.success(data, message);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(data);
        assertThat(response.message()).isEqualTo(message);
    }

    @Test
    void 데이터가_없는_성공_응답을_생성한다() {
        ApiResponse<Void> response = ApiResponse.successNoData();

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNull();
        assertThat(response.message()).isNull();
    }

    @Test
    void 데이터가_없고_메시지가_포함된_성공_응답을_생성한다() {
        String message = "삭제되었습니다.";

        ApiResponse<Void> response = ApiResponse.successNoData(message);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNull();
        assertThat(response.message()).isEqualTo(message);
    }
}
