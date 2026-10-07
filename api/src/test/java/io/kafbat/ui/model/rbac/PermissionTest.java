package io.kafbat.ui.model.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.kafbat.ui.model.rbac.permission.TopicAction;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PermissionTest {

  @ParameterizedTest
  @EnumSource(value = Resource.class, names = {"TOPIC", "CONSUMER", "SCHEMA", "CONNECT", "CONNECTOR"})
  void validateRejectsMissingValueForNamedResources(Resource resource) {
    var p = new Permission();
    p.setResource(resource.name());
    p.setActions(List.of("all"));

    assertThatIllegalArgumentException().isThrownBy(p::validate)
        .withMessageContaining(resource.name())
        .withMessageContaining("null or empty");
  }

  @ParameterizedTest
  @EnumSource(value = Resource.class, names = {"TOPIC", "CONSUMER", "SCHEMA", "CONNECT", "CONNECTOR"})
  void validateRejectsEmptyValueForNamedResources(Resource resource) {
    var p = new Permission();
    p.setResource(resource.name());
    p.setActions(List.of("view"));
    p.setValue("");

    assertThatIllegalArgumentException().isThrownBy(p::validate)
        .withMessageContaining(resource.name());
  }

  @ParameterizedTest
  @EnumSource(value = Resource.class, names = {"TOPIC", "CONSUMER", "SCHEMA", "CONNECT", "CONNECTOR"})
  void validateAcceptsWildcardForNamedResources(Resource resource) {
    var p = new Permission();
    p.setResource(resource.name());
    p.setActions(List.of("all"));
    p.setValue(".*");

    assertThatCode(p::validate).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @EnumSource(value = Resource.class, names = {"TOPIC", "CONSUMER", "SCHEMA", "CONNECT", "CONNECTOR"},
      mode = EnumSource.Mode.EXCLUDE)
  void validateAcceptsUnnamedResourcesWithoutValue(Resource resource) {
    var p = new Permission();
    p.setResource(resource.name());
    p.setActions(List.of("all"));

    assertThatCode(p::validate).doesNotThrowAnyException();
  }

  @Test
  void validatePreservesGlobalSchemaCompatibilityPermissionWithoutValue() {
    var p = new Permission();
    p.setResource("schema");
    p.setActions(List.of("modify_global_compatibility"));

    p.validate();
    p.transform();

    assertThat(AccessContext.builder().schemaGlobalCompatChange().build().isAccessible(List.of(p))).isTrue();
  }

  @Test
  void validateRequiresValueWhenGlobalSchemaActionIsCombinedWithNamedActions() {
    var p = new Permission();
    p.setResource("schema");
    p.setActions(List.of("MODIFY_GLOBAL_COMPATIBILITY", "VIEW"));

    assertThatIllegalArgumentException().isThrownBy(p::validate)
        .withMessageContaining("SCHEMA");
  }

  @Test
  void validateRejectsMissingValueInDefaultRole() {
    var p = new Permission();
    p.setResource("topic");
    p.setActions(List.of("all"));
    var role = new DefaultRole();
    role.setPermissions(List.of(p));

    assertThatIllegalArgumentException().isThrownBy(role::validate)
        .withMessageContaining("TOPIC");
  }

  @Test
  void transformSetsParseableFields() {
    var p = new Permission();
    p.setResource("toPic");
    p.setActions(List.of("vIEW", "EdiT"));
    p.setValue("patt|ern");

    p.transform();

    assertThat(p.getParsedActions())
        .containsExactlyInAnyOrder(TopicAction.VIEW, TopicAction.EDIT);

    assertThat(p.getCompiledValuePattern())
        .isNotNull()
        .matches(pattern -> pattern.pattern().equals("patt|ern"));
  }

  @Test
  void transformSetsFullActionsListIfAllActionPassed() {
    var p = new Permission();
    p.setResource("toPic");
    p.setActions(List.of("All"));

    p.transform();

    assertThat(p.getParsedActions())
        .containsExactlyInAnyOrder(TopicAction.values());
  }

  @Test
  void transformUnnestsDependantActions() {
    var p = new Permission();
    p.setResource("toPic");
    p.setActions(List.of("EDIT"));

    p.transform();

    assertThat(p.getParsedActions())
        .containsExactlyInAnyOrder(TopicAction.VIEW, TopicAction.EDIT);
  }

}
