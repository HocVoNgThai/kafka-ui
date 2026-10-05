package io.kafbat.ui.config.auth;

import io.kafbat.ui.model.rbac.Role;
import io.kafbat.ui.model.rbac.Subject;
import io.kafbat.ui.model.rbac.provider.Provider;
import io.kafbat.ui.service.rbac.AccessControlService;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;
import org.springframework.security.ldap.authentication.NullLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;

class LdapSecurityConfigTest {

  private static final String USER_DN = "uid=alice,ou=users,dc=example,dc=org";
  private static final String GROUP_DN = "cn=directory-group,ou=groups,dc=example,dc=org";
  private static final String ROLE_NAME = "reader-role";

  @Test
  void mapsGroupsUsingConfiguredRoleAttribute() throws Exception {
    requireRole(authorities("description", "group", "readers", "directory-group"), true);
  }

  @Test
  void defaultsToCommonNameWhenRoleAttributeIsNotConfigured() throws Exception {
    requireRole(authorities(null, "group", "readers", "readers"), true);
  }

  @Test
  void doesNotMapNonMatchingGroups() throws Exception {
    requireRole(authorities("description", "group", "unrelated-group", "directory-group"), false);
  }

  @Test
  void preservesUsernameSubjectMapping() throws Exception {
    requireRole(authorities("description", "user", "alice", "directory-group"), true);
  }

  @Test
  void doesNotEnableGroupExtractionWhenRbacIsDisabled() {
    var properties = new LdapProperties();
    properties.setGroupRoleAttribute("description");
    var acs = new AccessControlService(null, new RoleBasedAccessControlProperties(), new StandardEnvironment());
    acs.init();

    try (var context = new StaticApplicationContext()) {
      var extractor = new LdapSecurityConfig(properties)
          .authoritiesExtractor(context, new LdapContextSource(), acs);
      if (!(extractor instanceof NullLdapAuthoritiesPopulator)) {
        throw new AssertionError("RBAC-disabled LDAP must not extract group authorities");
      }
    }
  }

  private Collection<? extends GrantedAuthority> authorities(String attribute, String subjectType,
                                                              String subjectValue, String commonName) throws Exception {
    var subject = new Subject();
    subject.setProvider(Provider.LDAP);
    subject.setType(subjectType);
    subject.setValue(subjectValue);

    var role = new Role();
    role.setName(ROLE_NAME);
    role.setClusters(List.of("test-cluster"));
    role.setSubjects(List.of(subject));

    var rbac = new RoleBasedAccessControlProperties();
    rbac.setRoles(List.of(role));
    var acs = new AccessControlService(null, rbac, new StandardEnvironment());
    acs.init();

    var properties = new LdapProperties();
    properties.setGroupFilterSearchBase("ou=groups");
    properties.setGroupRoleAttribute(attribute);
    var source = new LdapContextSource();

    try (var context = new StaticApplicationContext()) {
      context.getBeanFactory().registerSingleton("accessControlService", acs);
      var extractor = new LdapSecurityConfig(properties).authoritiesExtractor(context, source, acs);
      Field template = DefaultLdapAuthoritiesPopulator.class.getDeclaredField("ldapTemplate");
      template.setAccessible(true);
      template.set(extractor, new LocalLdapTemplate(source, commonName));
      return extractor.getGrantedAuthorities(new DirContextAdapter(USER_DN), "alice");
    }
  }

  private void requireRole(Collection<? extends GrantedAuthority> authorities, boolean expected) {
    boolean found = authorities.stream().anyMatch(authority -> ROLE_NAME.equals(authority.getAuthority()));
    if (found != expected) {
      throw new AssertionError("Expected mapped reader role: " + expected + ", actual: " + found);
    }
  }

  private static class LocalLdapTemplate extends SpringSecurityLdapTemplate {
    private final String commonName;

    LocalLdapTemplate(ContextSource source, String commonName) {
      super(source);
      this.commonName = commonName;
    }

    @Override
    public Set<Map<String, List<String>>> searchForMultipleAttributeValues(String base, String filter,
                                                                          Object[] params, String[] attributes) {
      if (!USER_DN.equals(params[0])) {
        return Set.of();
      }
      Map<String, List<String>> group = new HashMap<>();
      group.put(DN_KEY, List.of(GROUP_DN));
      for (String attribute : attributes) {
        if ("cn".equals(attribute)) {
          group.put(attribute, List.of(commonName));
        } else if ("description".equals(attribute)) {
          group.put(attribute, List.of("readers"));
        }
      }
      return Set.of(group);
    }
  }
}
