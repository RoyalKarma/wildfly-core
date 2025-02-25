/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.test.integration.elytron.sasl.mgmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.test.security.common.TestRunnerConfigSetupTask;
import org.wildfly.test.security.common.elytron.AggregateRoleDecoder;
import org.wildfly.test.security.common.elytron.ConfigurableElement;
import org.wildfly.test.security.common.elytron.FileSystemRealm;
import org.wildfly.test.security.common.elytron.MechanismConfiguration;
import org.wildfly.test.security.common.elytron.MechanismRealmConfiguration;
import org.wildfly.test.security.common.elytron.PermissionMapper;
import org.wildfly.test.security.common.elytron.SaslFilter;
import org.wildfly.test.security.common.elytron.SimpleConfigurableSaslServerFactory;
import org.wildfly.test.security.common.elytron.SimplePermissionMapper;
import org.wildfly.test.security.common.elytron.SimpleSaslAuthenticationFactory;
import org.wildfly.test.security.common.elytron.SimpleSecurityDomain;
import org.wildfly.test.security.common.elytron.SourceAddressRoleDecoder;
import org.wildfly.test.security.common.other.SimpleMgmtNativeInterface;
import org.wildfly.test.security.common.other.SimpleSocketBinding;
import org.wildfly.test.security.common.other.TrustedDomainsConfigurator;

/**
 * Test authentication with the use of a source address role decoder where the IP address of the remote
 * client does not match the address configured on the decoder.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
@RunWith(WildFlyRunner.class)
@ServerSetup({ AuthenticationWithSourceAddressRoleDecoderMismatchTestCase.ServerSetup.class })
public class AuthenticationWithSourceAddressRoleDecoderMismatchTestCase extends AbstractMgmtSaslTestBase {

    private static final String MECHANISM = "DIGEST-MD5";
    private static final String NAME = AuthenticationWithSourceAddressRoleDecoderMatchTestCase.class.getSimpleName();
    private static final String AGGREGATE_ROLE_DECODER = "aggregateRoleDecoder";
    private static final String DECODER_1 = "decoder1";
    private static final String DECODER_2 = "decoder2";
    private static final String IP_PERMISSION_MAPPER = "ipPermissionMapper";

    @Override
    protected String getMechanism() {
        return MECHANISM;
    }

    /* The security domain being used in this test is configured with:
    1) a source-address-role-decoder that assigns the "Admin" role if the IP address of the remote client is 999.999.999.999
    2) a permission-mapper that assigns the "LoginPermission" if the identity has the "Admin" role unless the principal
    is "user3"
    */

    @Test
    public void testAuthenticationIPAddressMismatch() {
        createValidConfigForMechanism(MECHANISM, "alice").run(() -> assertAuthenticationFails());
        createValidConfigForMechanism(MECHANISM, "user3").run(() -> assertAuthenticationFails());
    }

    @Test
    public void testAuthenticationIPAddressMismatchWithSecurityRealmRole() {
        assertMechPassWhoAmI(MECHANISM, "bob");
    }

    public static class ServerSetup extends TestRunnerConfigSetupTask {

        @Override
        protected ConfigurableElement[] getConfigurableElements() {
            List<ConfigurableElement> elements = createConfigurableElementsForSaslMech();
            return elements.toArray(new ConfigurableElement[elements.size()]);
        }
    }

    protected void assertMechPassWhoAmI(String mechanismName, String expectedUsername) {
        createValidConfigForMechanism(mechanismName, expectedUsername).run(() -> assertWhoAmI(expectedUsername));
    }

    protected static List<ConfigurableElement> createConfigurableElementsForSaslMech() {
        List<ConfigurableElement> elements = new ArrayList<>();
        HashSet<String> roles = new HashSet<>();
        roles.add("Admin");
        HashSet<String> permissionSets = new HashSet<>();
        permissionSets.add("login-permission");
        permissionSets.add("default-permissions");
        HashSet<String> principals = new HashSet<>();
        principals.add("user3");
        elements.add(SimplePermissionMapper.builder()
                .withName(IP_PERMISSION_MAPPER)
                .withMapping(Collections.emptySet(), roles, permissionSets)
                .withMapping(principals, Collections.emptySet(), Collections.emptySet())
                .withMappingMode(PermissionMapper.MappingMode.AND)
                .build());
        elements.add(SourceAddressRoleDecoder.builder()
                .withName(DECODER_1)
                .withIPAddress("999.999.999.999")
                .withRole("Admin")
                .build());
        elements.add(SourceAddressRoleDecoder.builder()
                .withName(DECODER_2)
                .withIPAddress("99.99.99.99")
                .withRole("Employee")
                .build());
        elements.add(AggregateRoleDecoder.builder()
                .withName("aggregateRoleDecoder")
                .withRoleDecoder(DECODER_1)
                .withRoleDecoder(DECODER_2)
                .build());
        elements.add(FileSystemRealm.builder().withName("fsRealm")
                .withUser(USERNAME, USERNAME + PASSWORD_SFX, ROLE_SASL)
                .withUser("alice", "alice" + PASSWORD_SFX, "Employee")
                .withUser("bob", "bob" + PASSWORD_SFX, "Admin")
                .withUser("user3", "user3" + PASSWORD_SFX, "Employee")
                .build());
        elements.add(SimpleSecurityDomain.builder().withName(NAME).withDefaultRealm("fsRealm").withPermissionMapper(IP_PERMISSION_MAPPER)
                .withRealms(SimpleSecurityDomain.SecurityDomainRealm.builder().withRealm("fsRealm").withRoleDecoder("groups-to-roles").build())
                .withPermissionMapper(IP_PERMISSION_MAPPER)
                .withRoleDecoder(AGGREGATE_ROLE_DECODER)
                .build());
        elements.add(TrustedDomainsConfigurator.builder().withName("ManagementDomain").withTrustedSecurityDomains(NAME).build());

        elements.add(SimpleConfigurableSaslServerFactory.builder().withName(NAME).withSaslServerFactory("elytron")
                .addFilter(SaslFilter.builder().withPatternFilter(MECHANISM).build()).build());
        elements.add(
                SimpleSaslAuthenticationFactory.builder().withName(NAME).withSaslServerFactory(NAME).withSecurityDomain(NAME)
                        .addMechanismConfiguration(
                                MechanismConfiguration.builder().withMechanismName(MECHANISM)
                                        .addMechanismRealmConfiguration(
                                                MechanismRealmConfiguration.builder().withRealmName(NAME).build())
                                        .build())
                        .build());

        elements.add(SimpleSocketBinding.builder().withName(NAME).withPort(PORT_NATIVE).build());
        elements.add(SimpleMgmtNativeInterface.builder().withSocketBinding(NAME).withSaslAuthenticationFactory(NAME).build());
        return elements;
    }
}
