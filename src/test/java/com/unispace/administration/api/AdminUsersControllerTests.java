package com.unispace.administration.api;

import com.unispace.administration.application.UserDirectory;
import com.unispace.administration.application.UserDirectory.Page;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminUsersControllerTests {
    private final UserDirectory directory = mock(UserDirectory.class);
    private final AdminUsersController controller = new AdminUsersController(directory);

    @Test
    void startsAtTheFirstIdWithTheDefaultPageSize() {
        var page = new Page(List.of(), null);
        when(directory.list(BigInteger.ZERO, 25)).thenReturn(page);
        var response = controller.list(new LinkedMultiValueMap<>());
        assertThat(response.getBody()).isEqualTo(page);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void acceptsAnUnsignedCursorAndTheMaximumPageSize() {
        String cursor = "18446744073709551615";
        var page = new Page(List.of(), null);
        when(directory.list(new BigInteger(cursor), 100)).thenReturn(page);
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("cursor", cursor);
        parameters.add("limit", "100");
        assertThat(controller.list(parameters).getBody()).isEqualTo(page);
    }

    @Test
    void rejectsDuplicateAndUnapprovedParametersWithoutQuerying() {
        var duplicate = new LinkedMultiValueMap<String, String>();
        duplicate.add("limit", "1");
        duplicate.add("limit", "2");
        var search = new LinkedMultiValueMap<String, String>();
        search.add("search", "example");
        assertThatThrownBy(() -> controller.list(duplicate)).isInstanceOf(AdminUsersController.InvalidRequest.class);
        assertThatThrownBy(() -> controller.list(search)).isInstanceOf(AdminUsersController.InvalidRequest.class);
        verifyNoInteractions(directory);
    }

    @Test
    void rejectsNonCanonicalAndOutOfRangeIdentifiersAndLimits() {
        for (String id : List.of("0", "01", "-1", "1e2", "18446744073709551616", "999999999999999999999")) {
            assertThatThrownBy(() -> controller.detail(id)).isInstanceOf(AdminUsersController.InvalidRequest.class);
        }
        for (String limit : List.of("", "0", "-1", "101", "01", "9999999999")) {
            var parameters = new LinkedMultiValueMap<String, String>();
            parameters.add("limit", limit);
            assertThatThrownBy(() -> controller.list(parameters)).isInstanceOf(AdminUsersController.InvalidRequest.class);
        }
        verifyNoInteractions(directory);
    }
}
