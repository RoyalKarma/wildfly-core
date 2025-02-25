/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.integration.domain.rbac;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.suites.FullRbacProviderRunAsTestSuite;
import org.jboss.as.test.integration.domain.suites.FullRbacProviderTestSuite;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.as.test.integration.management.rbac.UserRolesMappingServerSetupTask;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
//import org.junit.Ignore;

/**
 * Tests of server group scoped roles using the "rbac" access control provider but with
 * the client using operation-headers to control the roles.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
//@Ignore("[WFCORE-1958] Clean up testsuite Elytron registration.")
public class RBACProviderRunAsServerGroupScopedRolesTestCase extends AbstractServerGroupScopedRolesTestCase {

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = FullRbacProviderRunAsTestSuite.createSupport(RBACProviderRunAsServerGroupScopedRolesTestCase.class.getSimpleName());
        primaryClientConfig = testSupport.getDomainPrimaryConfiguration();
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        setupRoles(domainClient);
        ServerGroupRolesMappingSetup.INSTANCE.setup(domainClient);
        deployDeployment1(domainClient);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();

        try {
            ServerGroupRolesMappingSetup.INSTANCE.tearDown(domainClient);
        } finally {
            try {
                tearDownRoles(domainClient);
            } finally {
                try {
                    removeDeployment1(domainClient);
                } finally {
                    FullRbacProviderTestSuite.stopSupport();
                    testSupport = null;
                }
            }
        }
    }

    @Override
    protected boolean isAllowLocalAuth() {
        return true;
    }

    @Override
    protected void configureRoles(ModelNode op, String[] roles) {
        RbacUtil.addRoleHeader(op, roles);
    }

    private static class ServerGroupRolesMappingSetup extends UserRolesMappingServerSetupTask {

        private static final Map<String, Set<String>> STANDARD_USERS;

        static {
            Map<String, Set<String>> rolesToUsers = new HashMap<String, Set<String>>();
            rolesToUsers.put(MONITOR_USER, Collections.singleton(MONITOR_USER));
            rolesToUsers.put(OPERATOR_USER, Collections.singleton(OPERATOR_USER));
            rolesToUsers.put(MAINTAINER_USER, Collections.singleton(MAINTAINER_USER));
            rolesToUsers.put(DEPLOYER_USER, Collections.singleton(DEPLOYER_USER));
            rolesToUsers.put(ADMINISTRATOR_USER, Collections.singleton(ADMINISTRATOR_USER));
            rolesToUsers.put(AUDITOR_USER, Collections.singleton(AUDITOR_USER));
            rolesToUsers.put(SUPERUSER_USER, Collections.singleton(SUPERUSER_USER));
            STANDARD_USERS = rolesToUsers;
        }

        private static final ServerGroupRolesMappingSetup INSTANCE = new ServerGroupRolesMappingSetup();

        protected ServerGroupRolesMappingSetup() {
            super(STANDARD_USERS);
        }
    }
}
