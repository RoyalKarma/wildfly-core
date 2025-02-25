/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.patching;

import static org.jboss.as.patching.IoUtils.mkdir;
import static org.jboss.as.patching.IoUtils.newFile;
import static org.jboss.as.test.patching.PatchingTestUtil.AS_DISTRIBUTION;
import static org.jboss.as.test.patching.PatchingTestUtil.AS_VERSION;
import static org.jboss.as.test.patching.PatchingTestUtil.FILE_SEPARATOR;
import static org.jboss.as.test.patching.PatchingTestUtil.MODULES_PATH;
import static org.jboss.as.test.patching.PatchingTestUtil.PRODUCT;
import static org.jboss.as.test.patching.PatchingTestUtil.assertPatchElements;
import static org.jboss.as.test.patching.PatchingTestUtil.createPatchXMLFile;
import static org.jboss.as.test.patching.PatchingTestUtil.createZippedPatchFile;
import static org.jboss.as.test.patching.PatchingTestUtil.randomString;
import static org.jboss.as.test.patching.PatchingTestUtil.readFile;

import java.io.File;

import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.HashUtils;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.jboss.as.test.patching.util.module.Module;
import org.jboss.as.version.ProductConfig;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author Martin Simka
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class CumulativePatchingScenariosTestCase extends AbstractPatchingTestCase {

    private File createOneOffPatchAddingMiscFile(String patchID, String asVersion) throws Exception {
        File oneOffPatchDir = mkdir(tempDir, patchID);
        ContentModification miscFileAdded = ContentModificationUtils.addMisc(oneOffPatchDir, patchID,
                "test content", "awesomeDirectory", "awesomeFile");
        ProductConfig productConfig = new ProductConfig(PRODUCT, asVersion, "main");
        Patch oneOffPatch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription("A one-off patch adding a misc file.")
                .oneOffPatchIdentity(productConfig.getProductName(), productConfig.getProductVersion())
                .getParent()
                .addContentModification(miscFileAdded)
                .build();
        createPatchXMLFile(oneOffPatchDir, oneOffPatch);
        return createZippedPatchFile(oneOffPatchDir, patchID);
    }

    private File createOneOffPatchAddingAModule(String patchID, String asVersion) throws Exception {
        String layerPatchID = randomString();
        File oneOffPatchDir = mkdir(tempDir, patchID);

        final String moduleName = "org.wildfly.test." + randomString();

        final ResourceItem resourceItem1 = new ResourceItem("testFile1", "content1".getBytes(StandardCharsets.UTF_8));
        final ResourceItem resourceItem2 = new ResourceItem("testFile2", "content2".getBytes(StandardCharsets.UTF_8));

        Module newModule = new Module.Builder(moduleName)
                .miscFile(resourceItem1)
                .miscFile(resourceItem2)
                .build();

        ContentModification moduleAdded = ContentModificationUtils.addModule(oneOffPatchDir, layerPatchID, newModule);
        Patch oneOffPatch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription("A one-off patch adding a new module.")
                .oneOffPatchIdentity(PRODUCT, asVersion)
                .getParent()
                .oneOffPatchElement(layerPatchID, "base", false)
                .setDescription("New module for the base layer")
                .addContentModification(moduleAdded)
                .getParent()
                .build();
        createPatchXMLFile(oneOffPatchDir, oneOffPatch);
        return createZippedPatchFile(oneOffPatchDir, patchID);
    }

    private File createCumulativePatch(String patchID, String asVersion, final String targetAsVersion) throws Exception {
        String layerPatchID = "layer" + patchID;
        File cpPatchDir = mkdir(tempDir, patchID);

        final String moduleName = "org.wildfly.test." + randomString();

        final ResourceItem resourceItem1 = new ResourceItem("testFile1", "content1".getBytes(StandardCharsets.UTF_8));
        final ResourceItem resourceItem2 = new ResourceItem("testFile2", "content2".getBytes(StandardCharsets.UTF_8));

        // Also see if we can update jboss-modules
        final File installation = new File(AS_DISTRIBUTION);
        final File patchDir = new File(cpPatchDir, patchID);
        final ContentModification jbossModulesModification = PatchingTestUtil.updateModulesJar(installation, patchDir);

        Module newModule = new Module.Builder(moduleName)
                .miscFile(resourceItem1)
                .miscFile(resourceItem2)
                .build();

        // Create the version module
        final String versionModuleName = ProductInfo.getVersionModule();
        final String slot = ProductInfo.getVersionModuleSlot();
        final String originalVersionModulePath = MODULES_PATH + FILE_SEPARATOR + versionModuleName.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + slot;
        final Module modifiedModule = PatchingTestUtil.createVersionModule(targetAsVersion);

        ContentModification moduleAdded = ContentModificationUtils.addModule(cpPatchDir, layerPatchID, newModule);
        ContentModification versionModuleModified = ContentModificationUtils.modifyModule(cpPatchDir, layerPatchID, HashUtils.hashFile(new File(originalVersionModulePath)), modifiedModule);

        Patch cpPatch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription("A cp patch.")
                .upgradeIdentity(PRODUCT, asVersion, targetAsVersion)
                .getParent()
                .upgradeElement(layerPatchID, "base", false)
                .addContentModification(moduleAdded)
                .addContentModification(versionModuleModified)
                .getParent()
                .addContentModification(jbossModulesModification)
                .build();
        createPatchXMLFile(cpPatchDir, cpPatch);
        return createZippedPatchFile(cpPatchDir, patchID);
    }

    private File createSecondCumulativePatch(String patchID, String asVersion, final String currentPatch, final String targetAsVersion) throws Exception {
        String layerPatchID = "layer" + patchID;
        File cpPatchDir = mkdir(tempDir, patchID);

        // Create the version module
        final String versionModuleName = ProductInfo.getVersionModule();
        final Module modifiedModule = PatchingTestUtil.createVersionModule(targetAsVersion);

        // Calculate the target hash of the currently active module
        final String currentLayerPatchID = "layer" + currentPatch;
        final String originalVersionModulePath = MODULES_PATH + FILE_SEPARATOR + Constants.OVERLAYS + FILE_SEPARATOR +
                currentLayerPatchID + FILE_SEPARATOR + versionModuleName.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + ProductInfo.getVersionModuleSlot();
        byte[] patchedAsVersionHash = HashUtils.hashFile(new File(originalVersionModulePath));
        assert patchedAsVersionHash != null;

        ContentModification versionModuleModified = ContentModificationUtils.modifyModule(cpPatchDir, layerPatchID, patchedAsVersionHash, modifiedModule);

        ProductConfig productConfig = new ProductConfig(PRODUCT, asVersion, "main");
        Patch cpPatch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription("A cp patch.")
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(), targetAsVersion)
                .getParent()
                .upgradeElement(layerPatchID, "base", false)
                .addContentModification(versionModuleModified)
                .getParent()
                .build();
        createPatchXMLFile(cpPatchDir, cpPatch);
        return createZippedPatchFile(cpPatchDir, patchID);
    }

    private File createInvalidCumulativePatch(String patchID, String asVersion, final String targetAsVersion) throws Exception {
        String layerPatchID = randomString();
        File cpPatchDir = mkdir(tempDir, patchID);

        // Create the version module
        final String versionModuleName = ProductInfo.getVersionModule();
        final String slot = ProductInfo.getVersionModuleSlot();
        final String originalVersionModulePath = MODULES_PATH + FILE_SEPARATOR + versionModuleName.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + slot;

        final Module modifiedModule = PatchingTestUtil.createVersionModule(targetAsVersion);

        // create broken patch - replaced layerPatchID with patchID
        ContentModification versionModuleModified = ContentModificationUtils.modifyModule(cpPatchDir, patchID, HashUtils.hashFile(new File(originalVersionModulePath)), modifiedModule);
        Patch cpPatch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription("A cp patch.")
                .upgradeIdentity(PRODUCT, asVersion, targetAsVersion)
                .getParent()
                .upgradeElement(layerPatchID, "base", false)
                .addContentModification(versionModuleModified)
                .getParent()
                .build();
        createPatchXMLFile(cpPatchDir, cpPatch);
        return createZippedPatchFile(cpPatchDir, patchID);
    }

    /**
     * Applies one-off that adds a misc file
     * Applies CP that adds a module, should invalidate all previously installed one-off
     * Applies one-off that adds a module
     * Applies second CP that modifies version module
     * does rollback of second CP
     * does rollback of second one-off
     * does rollback of first CP
     * does rollback of first one-off
     *
     * @throws Exception
     */
    @Test
    public void testOneOffCPOneOffCP() throws Exception {
        final String oneOffPatchID1 = randomString();
        final String oneOffPatchID2 = randomString();
        final String cpPatchID = randomString();
        final String cpPatchID2 = randomString();
        final String eapWithCP = "EAP with cp patch";
        File oneOffZip1 = createOneOffPatchAddingMiscFile(oneOffPatchID1, AS_VERSION);
        File cpZip = createCumulativePatch(cpPatchID, AS_VERSION, eapWithCP);
        File oneOffZip2 = createOneOffPatchAddingAModule(oneOffPatchID2, eapWithCP);

        // apply oneoff
        controller.start();
        Assert.assertTrue("Patch should be accepted", CliUtilsForPatching.applyPatch(oneOffZip1.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        Assert.assertTrue("The patch " + oneOffPatchID1 + " should be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffPatchID1));

        // apply cumulative patch
        Assert.assertTrue("Patch should be accepted", CliUtilsForPatching.applyPatch(cpZip.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        Assert.assertTrue("The patch " + cpPatchID + " should be listed as installed",
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(cpPatchID));
        Assert.assertFalse("The patch " + oneOffPatchID1 + " should NOT be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffPatchID1));

        // apply one-off
        Assert.assertTrue("Patch should be accepted", CliUtilsForPatching.applyPatch(oneOffZip2.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        Assert.assertTrue("The patch " + oneOffPatchID2 + " should be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffPatchID2));

        // apply second cumulative patch
        File cpZip2 = createSecondCumulativePatch(cpPatchID2, eapWithCP, cpPatchID, "EAP with second CP");
        Assert.assertTrue("Patch should be accepted", CliUtilsForPatching.applyPatch(cpZip2.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        Assert.assertTrue("The patch " + cpPatchID2 + " should be listed as installed",
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(cpPatchID2));
        Assert.assertFalse("The patch " + oneOffPatchID2 + " should NOT be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffPatchID2));

        // rollback second cumulative patch
        Assert.assertTrue("Rollback should be accepted", CliUtilsForPatching.rollbackCumulativePatch(true));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        Assert.assertFalse("The patch " + cpPatchID2 + " should NOT be listed as installed",
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(cpPatchID2));
        Assert.assertTrue("The cumulative patch id should be " + cpPatchID,
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(cpPatchID));
        Assert.assertTrue("The patch " + oneOffPatchID2 + " should be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffPatchID2));

        //rollback oneoff
        Assert.assertTrue("Rollback should be accepted", CliUtilsForPatching.rollbackPatch(oneOffPatchID2));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        Assert.assertFalse("The patch " + oneOffPatchID2 + " should NOT be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffPatchID2));

        // rollback first cumulative patch
        Assert.assertTrue("Rollback should be accepted", CliUtilsForPatching.rollbackCumulativePatch(true));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        Assert.assertFalse("The patch " + cpPatchID + " should NOT be listed as installed",
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(cpPatchID));
        Assert.assertTrue("The cumulative patch id should be " + Constants.BASE,
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(Constants.BASE));
        Assert.assertTrue("The patch " + oneOffPatchID1 + " should be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffPatchID1));

        // rollback one-off
        Assert.assertTrue("Rollback should be accepted", CliUtilsForPatching.rollbackPatch(oneOffPatchID1));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        try {
            Assert.assertFalse("The patch " + oneOffPatchID1 + " should NOT be listed as installed",
                    CliUtilsForPatching.getInstalledPatches().contains(oneOffPatchID1));

            // no patches present
            assertPatchElements(PatchingTestUtil.BASE_MODULE_DIRECTORY, null, false);
        } finally {
            controller.stop();
        }

    }

    /**
     * Applies one-off that adds a misc file
     * Applies one-off that adds a module
     * tries to apply a broken CP
     * patch shouldn't be accepted and one-offs should stay consistent
     *
     * @throws Exception
     */
    @Test
    public void testOneOffInvalidCumulativePatch() throws Exception {
        String oneOffPatchID1 = randomString();
        String oneOffPatchID2 = randomString();
        String cpPatchID = randomString();
        File oneOffZip1 = createOneOffPatchAddingMiscFile(oneOffPatchID1, AS_VERSION);
        File oneOffZip2 = createOneOffPatchAddingAModule(oneOffPatchID2, AS_VERSION);
        File cpZip = createInvalidCumulativePatch(cpPatchID, AS_VERSION, "EAP with cp patch");

        // apply oneoffs
        controller.start();
        Assert.assertTrue("Patch should be accepted", CliUtilsForPatching.applyPatch(oneOffZip1.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        Assert.assertTrue("The patch " + oneOffPatchID1 + " should be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffPatchID1));
        Assert.assertTrue("Patch should be accepted", CliUtilsForPatching.applyPatch(oneOffZip2.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        Assert.assertTrue("The patch " + oneOffPatchID2 + " should be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffPatchID2));

        // apply cumulative patch
        Assert.assertFalse("Patch shouldn't be accepted", CliUtilsForPatching.applyPatch(cpZip.getAbsolutePath()));
        controller.stop();

        controller.start();
        Assert.assertTrue("The patch " + oneOffPatchID1 + " should be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffPatchID1));
        Assert.assertTrue("The patch " + oneOffPatchID2 + " should be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffPatchID2));

        File miscFile = newFile(new File(AS_DISTRIBUTION), "awesomeDirectory", "awesomeFile");
        Assert.assertTrue("File " + miscFile.getAbsolutePath() + " should exist.", miscFile.exists());
        Assert.assertEquals("Unexpected content of file: " + miscFile.getAbsolutePath(), "test content", readFile(miscFile.getAbsolutePath()));
        controller.stop();
    }

    /**
     * Applies cumulative patch
     * Tries to apply one-off targeted to version without cp
     * Applies one-off targeted to new version with cp
     * Does rollback of one-off
     * Does rollback of CP
     * Applies one-off targeted to version without cp
     *
     * @throws Exception
     */
    @Test
    public void testApplyOneOffToWrongTargetVersion() throws Exception {
        final String cpAsVersion = "EAP with cp patch";
        String oneOffPatchForOldVersionWithoutCPID = randomString();
        String oneOffPatchForNewVersionWithCPID = randomString();
        String cpPatchID = randomString();
        File oneOffPatchForOldVersionWithoutCPZip = createOneOffPatchAddingMiscFile(oneOffPatchForOldVersionWithoutCPID, AS_VERSION);
        File oneOffPatchForNewVersionWithCPZip = createOneOffPatchAddingAModule(oneOffPatchForNewVersionWithCPID, cpAsVersion);
        File cpZip = createCumulativePatch(cpPatchID, AS_VERSION, cpAsVersion);

        // apply cumulative patch
        controller.start();
        Assert.assertTrue("Patch should be accepted ", CliUtilsForPatching.applyPatch(cpZip.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        Assert.assertTrue("The patch " + cpPatchID + " should be listed as installed",
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(cpPatchID));

        // try to apply one-off targeted to old version
        Assert.assertFalse("Patch shouldn't be accepted", CliUtilsForPatching.applyPatch(oneOffPatchForOldVersionWithoutCPZip.getAbsolutePath()));

        // apply one-off targeted to version with CP
        Assert.assertTrue("Patch should be accepted", CliUtilsForPatching.applyPatch(oneOffPatchForNewVersionWithCPZip.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        Assert.assertTrue("The patch " + cpPatchID + " should be listed as installed",
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(cpPatchID));
        Assert.assertTrue("The patch " + oneOffPatchForNewVersionWithCPID + " should be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffPatchForNewVersionWithCPID));

        // rollback one-off for new version
        Assert.assertTrue("Rollback should be accepted", CliUtilsForPatching.rollbackPatch(oneOffPatchForNewVersionWithCPID));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        Assert.assertFalse("The patch " + oneOffPatchForNewVersionWithCPID + " shouldn't be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffPatchForNewVersionWithCPID));
        Assert.assertTrue("The patch " + cpPatchID + " should be listed as installed",
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(cpPatchID));

        // rollback cp
        Assert.assertTrue("Rollback should be accepted", CliUtilsForPatching.rollbackCumulativePatch(true));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        Assert.assertFalse("The patch " + cpPatchID + " shouldn't be listed as installed",
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(cpPatchID));

        // apply one-off patch for old version without cp
        Assert.assertTrue("Patch should be accepted", CliUtilsForPatching.applyPatch(oneOffPatchForOldVersionWithoutCPZip.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        Assert.assertTrue("The patch " + oneOffPatchForOldVersionWithoutCPZip + " should be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffPatchForOldVersionWithoutCPID));
        controller.stop();
    }

    /**
     * Applies CP
     * changes standalone/configuration/standalone.xml
     * changes domain/configuration/domain.xml
     * does rollback of CP with --reset-configuration=true
     * Applies CP
     * changes standalone/configuration/standalone.xml
     * changes domain/configuration/domain.xml
     * does rollback of CP with --reset-configuration=false
     *
     * @throws Exception
     */
    @Test
    public void testCumulativePatchRollbackRestoreConfiguration() throws Exception {
        final String cpAsVersion = "EAP with cp patch";
        String cpPatchID = randomString();
        File cpZip = createCumulativePatch(cpPatchID, AS_VERSION, cpAsVersion);

        // apply cumulative patch
        controller.start();
        Assert.assertTrue("Patch should be accepted", CliUtilsForPatching.applyPatch(cpZip.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        Assert.assertTrue("The patch " + cpPatchID + " should be listed as installed",
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(cpPatchID));
        controller.stop();

        // save original content of files in standalone/configuration, domain/configuration
        final String standaloneXmlPath = AS_DISTRIBUTION + FILE_SEPARATOR + "standalone" + FILE_SEPARATOR + "configuration" + FILE_SEPARATOR + "standalone.xml";
        final String standaloneConfOrigContent = readFile(standaloneXmlPath);

        final String domainXmlPath = AS_DISTRIBUTION + FILE_SEPARATOR + "domain" + FILE_SEPARATOR + "configuration" + FILE_SEPARATOR + "domain.xml";
        final String domainConfOrigContent = readFile(domainXmlPath);

        addSystemProperties(standaloneXmlPath);
        addSystemProperties(domainXmlPath);

        controller.start();
        // rollback with reset-configuration=true
        Assert.assertTrue("Rollback should be accepted", CliUtilsForPatching.rollbackCumulativePatch(true));
        controller.stop();

        controller.start();
        Assert.assertEquals("File should be restored", standaloneConfOrigContent, readFile(standaloneXmlPath));
        Assert.assertEquals("File should be restored", domainConfOrigContent, readFile(domainXmlPath));

        // apply cumulative patch
        Assert.assertTrue("Patch should be accepted", CliUtilsForPatching.applyPatch(cpZip.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        controller.start();
        Assert.assertTrue("The patch " + cpPatchID + " should be listed as installed",
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(cpPatchID));
        controller.stop();

        addSystemProperties(standaloneXmlPath);
        addSystemProperties(domainXmlPath);

        controller.start();
        // rollback with reset-configuration=false
        Assert.assertTrue("Rollback should be accepted", CliUtilsForPatching.rollbackCumulativePatch(false));
        controller.stop();

        controller.start();
        try {
            Assert.assertNotEquals("File shouldn't be restored", standaloneConfOrigContent, readFile(standaloneXmlPath));
            Assert.assertNotEquals("File shouldn't be restored", domainConfOrigContent, readFile(domainXmlPath));

            // no patches present
            assertPatchElements(PatchingTestUtil.BASE_MODULE_DIRECTORY, null, false);
        } finally {
            controller.stop();
        }
    }
    //todo this should be done via CLI commands not by manipulating xml DOM
    private void addSystemProperties(String filePath) throws Exception {
        // modify xml
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(filePath);

        NodeList nodeList = document.getElementsByTagName("extensions");
        if (nodeList.getLength() != 1) {
            throw new IllegalStateException("unexpected count of extensions elements");
        }
        Node extensions = nodeList.item(0);
        Node systemProperties = document.createElement("system-properties");
        extensions.getParentNode().insertBefore(systemProperties, extensions.getNextSibling());

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource domSource = new DOMSource(document);
        StreamResult streamResult = new StreamResult(new File(filePath));
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(domSource, streamResult);
    }
}
