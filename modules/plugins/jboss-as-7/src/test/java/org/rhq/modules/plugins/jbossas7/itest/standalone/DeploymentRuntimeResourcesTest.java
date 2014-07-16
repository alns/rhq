/*
 * RHQ Management Platform
 * Copyright (C) 2005-2014 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 */

package org.rhq.modules.plugins.jbossas7.itest.standalone;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.rhq.modules.plugins.jbossas7.itest.standalone.DeploymentTest.getMeasurementScheduleRequests;
import static org.rhq.modules.plugins.jbossas7.itest.standalone.DeploymentTest.getMissingMeasurements;
import static org.rhq.modules.plugins.jbossas7.itest.standalone.DeploymentTest.getTestDeploymentPackageDetails;
import static org.rhq.modules.plugins.jbossas7.test.util.Constants.PLUGIN_NAME;
import static org.rhq.modules.plugins.jbossas7.test.util.Constants.STANDALONE_RESOURCE_KEY;
import static org.rhq.modules.plugins.jbossas7.test.util.Constants.STANDALONE_RESOURCE_TYPE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.rhq.core.clientapi.agent.PluginContainerException;
import org.rhq.core.clientapi.agent.inventory.CreateResourceRequest;
import org.rhq.core.clientapi.agent.inventory.CreateResourceResponse;
import org.rhq.core.clientapi.agent.inventory.DeleteResourceRequest;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.Property;
import org.rhq.core.domain.configuration.PropertyList;
import org.rhq.core.domain.configuration.PropertyMap;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.content.PackageDetailsKey;
import org.rhq.core.domain.content.transfer.ResourcePackageDetails;
import org.rhq.core.domain.measurement.MeasurementReport;
import org.rhq.core.domain.measurement.MeasurementScheduleRequest;
import org.rhq.core.domain.resource.CreateResourceStatus;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.ResourceCategory;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.pc.inventory.ResourceContainer;
import org.rhq.core.pc.util.FacetLockType;
import org.rhq.core.pluginapi.measurement.MeasurementFacet;
import org.rhq.core.pluginapi.operation.OperationFacet;
import org.rhq.core.pluginapi.operation.OperationResult;
import org.rhq.modules.plugins.jbossas7.itest.AbstractJBossAS7PluginTest;
import org.rhq.test.arquillian.MockingServerServices;
import org.rhq.test.arquillian.RunDiscovery;

/**
 * This test ensures that "* Runtime" (JPA, Messaging, EJB3) child resources of a deployment get discovered.
 *
 * @author Thomas Segismont
 */
@Test(groups = { "integration", "pc", "standalone" }, singleThreaded = true)
public class DeploymentRuntimeResourcesTest extends AbstractJBossAS7PluginTest {

    private enum RuntimeServiceType {
        DATASOURCES_RUNTIME("Datasources Runtime"), //
        DATASOURCE_RUNTIME("Datasource Runtime"), //
        MESSAGING_RUNTIME("Messaging Runtime"), //
        HORNETQ_SERVER_RUNTIME("HornetQ Server Runtime"), //
        JMS_QUEUE_RUNTIME("JMS Queue Runtime"), //
        JMS_TOPIC_RUNTIME("JMS Topic Runtime"), //
        EJB3_RUNTIME("EJB3 Runtime"), //
        MESSAGE_DRIVEN_BEAN_RUNTIME("Message Driven Bean Runtime"), //
        SINGLETON_BEAN_RUNTIME("Singleton Bean Runtime"), //
        STATELESS_SESSION_BEAN_RUNTIME("Stateless Session Bean Runtime"), //
        STATEFUL_SESSION_BEAN_RUNTIME("Stateful Session Bean Runtime"), //
        WEBSERVICES_RUNTIME("Webservices Runtime"), //
        ENDPOINT_RUNTIME("Endpoint Runtime"), //
        JPA_RUNTIME("JPA Runtime"), //
        HIBERNATE_PERSISTENCE_UNIT("Hibernate Persistence Unit"), //
        ENTITY("Entity");

        private String serviceTypeName;

        RuntimeServiceType(String serviceTypeName) {
            this.serviceTypeName = serviceTypeName;
        }

        public String getServiceTypeName() {
            return serviceTypeName;
        }
    }

    private Resource deployment;

    @Override
    protected void injectMocks(MockingServerServices serverServices) {
        Mockito.when(
            serverServices.getContentServerService().downloadPackageBitsGivenResource(Mockito.anyInt(),
                Mockito.any(PackageDetailsKey.class), Mockito.any(OutputStream.class))).then(new Answer<Long>() {
            @Override
            public Long answer(InvocationOnMock invocation) throws Throwable {
                OutputStream out = (OutputStream) invocation.getArguments()[invocation.getArguments().length - 1];
                return copyStreamAndReturnCount(out);
            }
        });

        Mockito.when(
            serverServices.getContentServerService().downloadPackageBitsForChildResource(Mockito.anyInt(),
                Mockito.anyString(), Mockito.any(PackageDetailsKey.class), Mockito.any(OutputStream.class))).then(
            new Answer<Long>() {
                @Override
                public Long answer(InvocationOnMock invocation) throws Throwable {
                    OutputStream out = (OutputStream) invocation.getArguments()[invocation.getArguments().length - 1];
                    return copyStreamAndReturnCount(out);
                }
            });
    }

    private long copyStreamAndReturnCount(OutputStream out) throws IOException {
        if (null == out) {
            System.out.println("**** Unexepected null output stream in mock code!!");
            return 0L;
        }

        String path = TestDeployments.JAVAEE6_TEST_APP.getResourcePath();
        InputStream in = getClass().getClassLoader().getResourceAsStream(path);

        long cnt = 0;

        try {
            int data;
            while ((data = in.read()) != -1) {
                out.write(data);
                cnt++;
            }
        } finally {
            in.close();
            out.flush();
        }
        return cnt;
    }

    @Test(priority = 10)
    @RunDiscovery
    public void testDeploy() throws Exception {
        Resource platform = validatePlatform();
        Resource standaloneServer = waitForResourceByTypeAndKey(platform, platform, STANDALONE_RESOURCE_TYPE,
            STANDALONE_RESOURCE_KEY);

        //assertFalse(standaloneResources == null || standaloneResources.size() != 1,
        //    "Exactly 1 AS7 standalone server resource should be present.");

        int serverResourceId = standaloneServer.getId();

        ResourcePackageDetails packageDetails = getTestDeploymentPackageDetails(TestDeployments.JAVAEE6_TEST_APP);

        Configuration deploymentConfig = new Configuration();
        deploymentConfig.put(new PropertySimple("runtimeName", packageDetails.getName()));

        CreateResourceRequest request = new CreateResourceRequest();
        request.setPackageDetails(packageDetails);
        request.setParentResourceId(serverResourceId);
        request.setPluginConfiguration(null);
        request.setPluginName(PLUGIN_NAME);
        request.setResourceConfiguration(deploymentConfig);
        request.setResourceName(packageDetails.getName());
        request.setResourceTypeName("Deployment");

        CreateResourceResponse response = pluginContainer.getResourceFactoryManager().executeCreateResourceImmediately(
            request);

        assertEquals(response.getStatus(), CreateResourceStatus.SUCCESS,
            "The deployment failed with an error mesasge: " + response.getErrorMessage());

        deployment = waitForResourceByTypeAndKey(platform, standaloneServer, new ResourceType("Deployment",
            PLUGIN_NAME, ResourceCategory.SERVICE, null), "deployment=" + packageDetails.getName());
        // these tests depend on the deployment children to be in inventory, make sure they are
        waitForAsyncDiscoveryToStabilize(deployment, 5000L, 10);
    }

    @Test(priority = 12)
    public void testRuntimeResources() throws Exception {
        ensureAllRuntimeServicesAreFound();
        for (RuntimeServiceType runtimeServiceType : RuntimeServiceType.values()) {
            testRuntimeResourceMeasurements(runtimeServiceType);
        }
    }

    private void ensureAllRuntimeServicesAreFound() throws InterruptedException {
        long start = System.currentTimeMillis();
        boolean foundAllRuntimeServices;
        do {
            boolean runtimeServiceMissing = false;
            for (RuntimeServiceType runtimeServiceType : RuntimeServiceType.values()) {
                Set<Resource> childResources = getChildResourcesOfType(getDeploymentResource(),
                    runtimeServiceType.getServiceTypeName());
                runtimeServiceMissing = childResources.isEmpty() && canGetMeasurementFacet(childResources);
                if (runtimeServiceMissing) {
                    break;
                }
            }
            foundAllRuntimeServices = !runtimeServiceMissing;

        } while (!foundAllRuntimeServices
            && (System.currentTimeMillis() - start) < java.util.concurrent.TimeUnit.MINUTES.toMillis(10));
        Assert.assertTrue(foundAllRuntimeServices, "Could not find all runtime services");
    }

    private boolean canGetMeasurementFacet(Set<Resource> resources) {
        for (Resource resource : resources) {
            if (getMeasurementFacet(resource) == null) {
                return false;
            }
        }
        return true;
    }

    private void testRuntimeResourceMeasurements(RuntimeServiceType runtimeServiceType) throws Exception {
        Set<Resource> resources = getChildResourcesOfType(getDeploymentResource(),
            runtimeServiceType.getServiceTypeName());
        for (Resource resource : resources) {
            MeasurementFacet measurementFacet = getMeasurementFacet(resource);
            // TODO (jshaughn) see why we get into this occasionally
            System.out.println("--->>> " + resource + " MeasurementFacet=" + measurementFacet);
            if (null == measurementFacet)
                continue;
            MeasurementReport report = new MeasurementReport();
            Set<MeasurementScheduleRequest> measurementScheduleRequests = getMeasurementScheduleRequests(resource);
            measurementFacet.getValues(report, measurementScheduleRequests);
            assertEquals(report.getCallTimeData().size(), 0, runtimeServiceType.getServiceTypeName()
                + ": no calltime data was requested");
            assertEquals(
                report.getNumericData().size() + report.getTraitData().size(),
                measurementScheduleRequests.size(),
                runtimeServiceType.getServiceTypeName()
                    + ": some requested measurements are missing: "
                    + getMissingMeasurements(measurementScheduleRequests, report.getNumericData(),
                        report.getTraitData()));
        }
    }

    private MeasurementFacet getMeasurementFacet(Resource resource) {
        ResourceContainer resourceContainer = pluginContainer.getInventoryManager().getResourceContainer(resource);
        try {
            return resourceContainer.createResourceComponentProxy(MeasurementFacet.class, FacetLockType.READ,
                SECONDS.toMillis(5), false, false, false);
        } catch (PluginContainerException e) {
            return null;
        }
    }

    private Set<Resource> getChildResourcesOfType(final Resource parentResource, final String runtimeServiceType) {
        Set<Resource> foundResources = new HashSet<Resource>();
        Set<Resource> childResources = parentResource.getChildResources();
        if (childResources != null && !childResources.isEmpty()) {
            for (Resource childResource : childResources) {
                if (runtimeServiceType.equals(childResource.getResourceType().getName())) {
                    foundResources.add(childResource);
                }
                foundResources.addAll(getChildResourcesOfType(childResource, runtimeServiceType));
            }
        }
        return foundResources;
    }

    @Test(priority = 13)
    public void testHibernatePersistenceUnitViewQueriesOperation() throws Exception {
        Set<Resource> resources = getChildResourcesOfType(getDeploymentResource(),
            RuntimeServiceType.HIBERNATE_PERSISTENCE_UNIT.getServiceTypeName());
        for (Resource resource : resources) {
            OperationFacet operationFacet = getOperationFacet(resource);
            Configuration parameters = new Configuration();
            parameters.setSimpleValue("batchSize", "50");
            parameters.setSimpleValue("managementQueryTimeout", "180");
            OperationResult operationResult = operationFacet.invokeOperation("viewQueries", parameters);
            String errorMessage = operationResult.getErrorMessage();
            assertNull(errorMessage, errorMessage);
            Configuration complexResults = operationResult.getComplexResults();
            assertNotNull(complexResults, "ComplexResults is null");
            PropertyList queriesPropList = complexResults.getList("queries");
            assertNotNull(queriesPropList, "queriesPropList is null");
            List<Property> queriesList = queriesPropList.getList();
            assertFalse(queriesList.isEmpty(), "queriesPropList is empty");
            for (Property queryProperty : queriesList) {
                assertTrue(queryProperty instanceof PropertyMap, "Not a PropertyMap: " + queryProperty);
                PropertyMap queryPropertyMap = (PropertyMap) queryProperty;
                assertTrue(queryPropertyMap.getMap().containsKey("query-name"),
                    "Map does not contain query-name attribute: " + queryPropertyMap.getMap());
            }
        }
    }

    @Test(priority = 99)
    public void testUndeploy() throws Exception {
        Resource deploymentResource = getDeploymentResource();
        DeleteResourceRequest request = new DeleteResourceRequest(0, deploymentResource.getId());
        getServerInventory().removeResource(deploymentResource);
        pluginContainer.getResourceFactoryManager().deleteResource(request);
    }

    private Resource getDeploymentResource() {
        return deployment;
    }

    private OperationFacet getOperationFacet(Resource resource) {
        ResourceContainer resourceContainer = pluginContainer.getInventoryManager().getResourceContainer(resource);
        try {
            return resourceContainer.createResourceComponentProxy(OperationFacet.class, FacetLockType.WRITE,
                SECONDS.toMillis(5), false, false, false);
        } catch (PluginContainerException e) {
            return null;
        }
    }

}
