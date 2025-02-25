/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.as.server.deployment.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.as.server.deployment.SimpleAttachable;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.security.PermissionFactory;

/**
 * Information used to build a module.
 *
 * @author Stuart Douglas
 * @author Marius Bogoevici
 */
public class ModuleSpecification extends SimpleAttachable {

    /**
     * System dependencies are dependencies that are added automatically by the container.
     */
    private final List<ModuleDependency> systemDependencies = new ArrayList<ModuleDependency>();

    private final Set<ModuleIdentifier> systemDependenciesSet = new HashSet<ModuleIdentifier>();
    /**
     * Local dependencies are dependencies on other parts of the deployment, such as class-path entry
     */
    private final List<ModuleDependency> localDependencies = new ArrayList<ModuleDependency>();

    private final Set<ModuleIdentifier> localDependenciesSet = new HashSet<ModuleIdentifier>();
    /**
     * If set to true this indicates that a dependency on this module requires a dependency on all it's local
     * dependencies.
     */
    private boolean localDependenciesTransitive;

    /**
     * User dependencies are dependencies that the user has specifically added, either via jboss-deployment-structure.xml
     * or via the manifest.
     * <p/>
     * User dependencies are not affected by exclusions.
     */
    private final List<ModuleDependency> userDependencies = new ArrayList<ModuleDependency>();

    private final Set<ModuleIdentifier> userDependenciesSet = new HashSet<ModuleIdentifier>();

    private final List<ResourceLoaderSpec> resourceLoaders = new ArrayList<ResourceLoaderSpec>();

    /**
     * The class transformers
     */
    private final List<String> classTransformers = new ArrayList<String>();

    private volatile List<ModuleDependency> allDependencies = null;

    /**
     * Modules that cannot be added as dependencies to the deployment, as the user has excluded them
     */
    private final Set<ModuleIdentifier> exclusions = new HashSet<ModuleIdentifier>();

    /**
     * A subset of found dependencies that are excluded in process of deployment, as the user has excluded them
     */
    private final Set<ModuleIdentifier> excludedDependencies = new HashSet<ModuleIdentifier>();

    /**
     * Flag that is set to true if modules of non private sub deployments should be able to see each other
     */
    private boolean subDeploymentModulesIsolated;

    /**
     * Flag that is set to true if exclusions should be cascaded to sub deployments
     */
    private boolean exclusionsCascadedToSubDeployments;

    /**
     * Flag that indicates that this module should never be visible to other sub deployments
     */
    private boolean privateModule;

    /**
     * Flag that indicates that this module should always be visible to other sub deployments, even if sub deployments
     * are isolated and there is no specify dependency on this module.
     *
     * If sub deployments are not isolated then this flag has no effect.
     *
     */
    private boolean publicModule;

    /**
     * Flag that indicates that local resources should come last in the dependencies list
     */
    private boolean localLast;

    /**
     * Module aliases
     */
    private final List<ModuleIdentifier> aliases = new ArrayList<ModuleIdentifier>();

    /**
     * JBoss modules system dependencies, which allow you to specify dependencies on the app class loader
     * to get access to JDK classes.
     */
    private final List<DependencySpec> moduleSystemDependencies = new ArrayList<DependencySpec>();

    /**
     * The minimum permission set for this module, wrapped as {@code PermissionFactory} instances.
     */
    private final List<PermissionFactory> permissionFactories = new ArrayList<PermissionFactory>();

    public void addSystemDependency(final ModuleDependency dependency) {
        if (!exclusions.contains(dependency.getIdentifier()) && !systemDependenciesSet.contains(dependency.getIdentifier())) {
            this.systemDependencies.add(dependency);
            this.systemDependenciesSet.add(dependency.getIdentifier());
        } else {
            excludedDependencies.add(dependency.getIdentifier());
        }
    }

    public void addSystemDependencies(final Collection<ModuleDependency> dependencies) {
        allDependencies = null;
        for (final ModuleDependency dependency : dependencies) {
            addSystemDependency(dependency);
        }
    }

    public void addUserDependency(final ModuleDependency dependency) {
        allDependencies = null;
        if (this.userDependenciesSet.add(dependency.getIdentifier())) {
            this.userDependencies.add(dependency);
        }
    }

    public void addUserDependencies(final Collection<ModuleDependency> dependencies) {
        allDependencies = null;
        for (final ModuleDependency dependency : dependencies) {
            addUserDependency(dependency);
        }
    }

    public void addLocalDependency(final ModuleDependency dependency) {
        allDependencies = null;
        if (!exclusions.contains(dependency.getIdentifier())) {
            if (this.localDependenciesSet.add(dependency.getIdentifier())) {
                this.localDependencies.add(dependency);
            }
        } else {
            excludedDependencies.add(dependency.getIdentifier());
        }
    }

    public void addLocalDependencies(final Collection<ModuleDependency> dependencies) {
        allDependencies = null;
        for (final ModuleDependency dependency : dependencies) {
            addLocalDependency(dependency);
        }
    }

    public List<ModuleDependency> getSystemDependencies() {
        return Collections.unmodifiableList(systemDependencies);
    }

    public void addExclusion(final ModuleIdentifier exclusion) {
        allDependencies = null;
        exclusions.add(exclusion);
        Iterator<ModuleDependency> it = systemDependencies.iterator();
        while (it.hasNext()) {
            final ModuleDependency dep = it.next();
            if (dep.getIdentifier().equals(exclusion)) {
                it.remove();
            }
        }
        it = localDependencies.iterator();
        while (it.hasNext()) {
            final ModuleDependency dep = it.next();
            if (dep.getIdentifier().equals(exclusion)) {
                it.remove();
            }
        }
    }

    public void addExclusions(final Iterable<ModuleIdentifier> exclusions) {
        for (final ModuleIdentifier exclusion : exclusions) {
            addExclusion(exclusion);
        }
    }


    public List<ModuleDependency> getLocalDependencies() {
        return Collections.unmodifiableList(localDependencies);
    }

    public List<ModuleDependency> getUserDependencies() {
        return Collections.unmodifiableList(userDependencies);
    }

    /**
     * Gets a modifiable view of the user dependencies list.
     *
     * @return The user dependencies
     */
    public List<ModuleDependency> getMutableUserDependencies() {
        allDependencies = null;
        return userDependencies;
    }

    public void addResourceLoader(final ResourceLoaderSpec resourceLoader) {
        this.resourceLoaders.add(resourceLoader);
    }

    public List<ResourceLoaderSpec> getResourceLoaders() {
        return Collections.unmodifiableList(resourceLoaders);
    }

    public void addClassTransformer(final String classTransformer) {
        this.classTransformers.add(classTransformer);
    }

    public List<String> getClassTransformers() {
        return Collections.unmodifiableList(classTransformers);
    }

    public boolean isSubDeploymentModulesIsolated() {
        return subDeploymentModulesIsolated;
    }

    public void setSubDeploymentModulesIsolated(final boolean subDeploymentModulesIsolated) {
        this.subDeploymentModulesIsolated = subDeploymentModulesIsolated;
    }

    public boolean isExclusionsCascadedToSubDeployments() {
        return exclusionsCascadedToSubDeployments;
    }

    public void setExclusionsCascadedToSubDeployments(boolean exclusionsCascadedToSubDeployments) {
        this.exclusionsCascadedToSubDeployments = exclusionsCascadedToSubDeployments;
    }

    public boolean isPrivateModule() {
        return privateModule;
    }

    public void setPrivateModule(final boolean privateModule) {
        this.privateModule = privateModule;
    }

    public boolean isPublicModule() {
        return publicModule;
    }

    public void setPublicModule(boolean publicModule) {
        this.publicModule = publicModule;
    }

    /**
     * Returns true if the {@link #localDependencies} added for this {@link ModuleSpecification} should be made
     * transitive (i.e. if any other module 'B' depends on the module 'A' represented by this {@link ModuleSpecification}, then
     * module 'B' will be added with all "local dependencies" that are applicable for module "A"). Else returns false.
     *
     * @return
     * @see {@link #localDependencies}
     */
    public boolean isLocalDependenciesTransitive() {
        return localDependenciesTransitive;
    }

    /**
     * Sets whether the {@link #localDependencies} applicable for this {@link ModuleSpecification} are to be treated as transitive dependencies
     * for modules which depend on the module represented by this {@link ModuleSpecification}
     *
     * @param localDependenciesTransitive True if the {@link #localDependencies} added for this {@link ModuleSpecification} should be made
     *                                    transitive (i.e. if any other module 'B' depends on the module 'A' represented by
     *                                    this {@link ModuleSpecification}, then module 'B' will be added with
     *                                    all "local dependencies" that are applicable for module "A"). False otherwise
     * @return
     * @see {@link #localDependencies}
     */
    public void setLocalDependenciesTransitive(final boolean localDependenciesTransitive) {
        this.localDependenciesTransitive = localDependenciesTransitive;
    }

    /**
     * @return
     * @deprecated since AS 8.x. Use {@link #isLocalDependenciesTransitive()} instead
     */
    @Deprecated
    public boolean isRequiresTransitiveDependencies() {
        return localDependenciesTransitive;
    }

    /**
     * @param requiresTransitiveDependencies
     * @deprecated since AS 8.x. Use {@link #setLocalDependenciesTransitive(boolean)} instead
     */
    @Deprecated
    public void setRequiresTransitiveDependencies(final boolean requiresTransitiveDependencies) {
        this.localDependenciesTransitive = requiresTransitiveDependencies;
    }

    public boolean isLocalLast() {
        return localLast;
    }

    public void setLocalLast(final boolean localLast) {
        this.localLast = localLast;
    }

    public void addAlias(final ModuleIdentifier moduleIdentifier) {
        aliases.add(moduleIdentifier);
    }

    public void addAliases(final Collection<ModuleIdentifier> moduleIdentifiers) {
        aliases.addAll(moduleIdentifiers);
    }

    public List<ModuleIdentifier> getAliases() {
        return aliases;
    }

    public List<ModuleDependency> getAllDependencies() {
        if (allDependencies == null) {
            allDependencies = new ArrayList<ModuleDependency>();
            allDependencies.addAll(systemDependencies);
            allDependencies.addAll(userDependencies);
            allDependencies.addAll(localDependencies);
        }
        return allDependencies;
    }

    public void addModuleSystemDependencies(final List<DependencySpec> systemDependencies) {
        moduleSystemDependencies.addAll(systemDependencies);
    }

    public List<DependencySpec> getModuleSystemDependencies() {
        return Collections.unmodifiableList(moduleSystemDependencies);
    }

    /**
     * Add a permission factory to this deployment.  This may include permissions not explicitly specified
     * in the domain configuration; such permissions must be validated before being added.
     *
     * @param permissionFactory the permission factory to add
     */
    public void addPermissionFactory(final PermissionFactory permissionFactory) {
        permissionFactories.add(permissionFactory);
    }

    /**
     * Get the permission factory set for this deployment.  This may include permissions not explicitly specified
     * in the domain configuration; such permissions must be validated before being added.
     *
     * @return the permission factory set for this deployment
     */
    public List<PermissionFactory> getPermissionFactories() {
        return permissionFactories;
    }

    public Set<ModuleIdentifier> getNonexistentExcludedDependencies() {
        // WFCORE-4234 check all excluded dependencies via jboss-deployment-structure.xml are also valid.
        return exclusions.stream().filter(e -> !excludedDependencies.contains(e)).collect(Collectors.toSet());
    }
}
