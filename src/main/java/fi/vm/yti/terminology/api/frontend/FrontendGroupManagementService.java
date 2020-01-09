package fi.vm.yti.terminology.api.frontend;

import fi.vm.yti.security.AuthenticatedUserProvider;
import fi.vm.yti.security.AuthorizationException;
import fi.vm.yti.security.Role;
import fi.vm.yti.security.YtiUser;
import fi.vm.yti.terminology.api.util.Parameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

@Service
public class FrontendGroupManagementService {

    private final String groupManagementUrl;
    private final RestTemplate restTemplate;
    private final AuthenticatedUserProvider userProvider;

    public FrontendGroupManagementService(@Value("${groupmanagement.url}") String groupManagementUrl,
                                          RestTemplate restTemplate,
                                          AuthenticatedUserProvider userProvider) {
        this.groupManagementUrl = groupManagementUrl;
        this.restTemplate = restTemplate;
        this.userProvider = userProvider;
    }

    public @Nullable GroupManagementUser findUser(@NotNull String userId) {
        try {
            return restTemplate.exchange(groupManagementUrl + "/public-api/user" + Parameters.single("id", userId), GET, null, GroupManagementUser.class).getBody();
        } catch (HttpClientErrorException ex)   {
            if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw ex;
            } else {
                return null;
            }
        }
    }

    @NotNull List<GroupManagementUserRequest> getUserRequests() {

        YtiUser user = userProvider.getUser();

        if (user.isAnonymous()) {
            throw new AuthorizationException("User not authenticated");
        }

        String url = groupManagementUrl + "/private-api/requests" + Parameters.single("userId", user.getId().toString());
        return restTemplate.exchange(url, GET, null, new ParameterizedTypeReference<List<GroupManagementUserRequest>>() {}).getBody();
    }

    void sendRequest(final UUID organizationId) {

        YtiUser user = userProvider.getUser();

        if (user.isAnonymous()) {
            throw new AuthorizationException("User not authenticated");
        }

        Parameters parameters = new Parameters();
        parameters.add("userId", user.getId().toString());
        parameters.add("role", Role.TERMINOLOGY_EDITOR.toString());
        parameters.add("organizationId", organizationId.toString());

        String url = groupManagementUrl + "/private-api/request" + parameters;
        restTemplate.exchange(url, POST, null, String.class);
    }

    @NotNull List<GroupManagementUser> getUsers() {

        String url = groupManagementUrl + "/public-api/users";
        return restTemplate.exchange(url, GET, null, new ParameterizedTypeReference<List<GroupManagementUser>>() {}).getBody();
    }
}
