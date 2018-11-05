package fi.vm.yti.terminology.api.security;

import fi.vm.yti.security.AuthenticatedUserProvider;
import fi.vm.yti.security.YtiUser;
import fi.vm.yti.terminology.api.model.termed.GenericNode;
import fi.vm.yti.terminology.api.model.termed.Identifier;
import fi.vm.yti.terminology.api.model.termed.Node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

import static fi.vm.yti.security.Role.ADMIN;
import static fi.vm.yti.security.Role.TERMINOLOGY_EDITOR;
import static fi.vm.yti.terminology.api.util.CollectionUtils.mapToSet;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;

@Service
public class AuthorizationManager {

    private final AuthenticatedUserProvider userProvider;
    private final AuthorizationTermedService termedService;

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationManager.class);

    @Autowired
    AuthorizationManager(AuthenticatedUserProvider userProvider,
                         AuthorizationTermedService termedService) {
        this.userProvider = userProvider;
        this.termedService = termedService;
    }

    public boolean canModifyNodes(List<? extends Node> nodes) {
        return nodes.isEmpty() || canModifyAllGraphs(mapToSet(nodes, node -> node.getType().getGraphId()));
    }

    public boolean canRemoveNodes(List<Identifier> identifiers) {
        return identifiers.isEmpty() || canModifyAllGraphs(mapToSet(identifiers, id -> id.getType().getGraphId()));
    }

    public boolean canDeleteVocabulary(UUID graphId) {
        return canModifyAllGraphs(singleton(graphId));
    }

    public boolean canCreateVocabulary(GenericNode vocabularyNode) {

        Set<UUID> organizationIds =
                mapToSet(vocabularyNode.getReferences().getOrDefault("contributor", emptyList()), Identifier::getId);

        return canModifyAllOrganizations(organizationIds);
    }

    private boolean canModifyAllGraphs(Collection<UUID> graphIds) {

        Set<UUID> organizationIds = graphIds.stream()
                .flatMap(graphId -> termedService.getOrganizationIds(graphId).stream())
                .collect(toSet());

        return canModifyAllOrganizations(organizationIds);
    }

    private boolean canModifyAllOrganizations(Collection<UUID> organizationIds) {
        YtiUser user = userProvider.getUser();
        return user.isSuperuser() || user.isInAnyRole(EnumSet.of(ADMIN, TERMINOLOGY_EDITOR), organizationIds);
    }

    public boolean userHasRightsForCodelistConceptSuggestion() {
        return !this.userProvider.getUser().isAnonymous();
    }
}
