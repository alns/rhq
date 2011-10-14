/*
 * RHQ Management Platform
 * Copyright 2011, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.rhq.enterprise.gui.coregui.client.admin.templates;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.smartgwt.client.types.Alignment;
import com.smartgwt.client.widgets.grid.ListGrid;
import com.smartgwt.client.widgets.grid.ListGridField;
import com.smartgwt.client.widgets.layout.Layout;
import com.smartgwt.client.widgets.tree.TreeGrid;
import com.smartgwt.client.widgets.tree.TreeGridField;

import org.rhq.core.domain.authz.Permission;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.domain.resource.composite.ResourceTypeTemplateCountComposite;
import org.rhq.enterprise.gui.coregui.client.ImageManager;
import org.rhq.enterprise.gui.coregui.client.LinkManager;
import org.rhq.enterprise.gui.coregui.client.PermissionsLoadedListener;
import org.rhq.enterprise.gui.coregui.client.PermissionsLoader;
import org.rhq.enterprise.gui.coregui.client.ViewPath;
import org.rhq.enterprise.gui.coregui.client.alert.definitions.TemplateAlertDefinitionsView;
import org.rhq.enterprise.gui.coregui.client.components.TitleBar;
import org.rhq.enterprise.gui.coregui.client.components.view.ViewName;
import org.rhq.enterprise.gui.coregui.client.inventory.resource.type.ResourceTypeRepository;
import org.rhq.enterprise.gui.coregui.client.inventory.resource.type.ResourceTypeRepository.TypesLoadedCallback;
import org.rhq.enterprise.gui.coregui.client.util.selenium.LocatableVLayout;

/**
 * @author Jay Shaughnessy
 */
public class AlertDefinitionTemplateTypeView extends ResourceTypeTreeView {

    public static final ViewName VIEW_ID = new ViewName("AlertDefTemplates", MSG.view_adminConfig_alertDefTemplates());
    public static final String VIEW_PATH = ResourceTypeTreeView.VIEW_PATH + VIEW_ID;

    public static final String ATTR_ENABLED_TEMPLATES = "enabledTemplates";
    public static final String ATTR_DISABLED_TEMPLATES = "disabledTemplates";

    private Layout canvas;

    public AlertDefinitionTemplateTypeView(String locatorId) {
        super(locatorId);
    }

    @Override
    protected String getEditLink(String typeId) {

        if (null == typeId) {
            return LinkManager.getAdminTemplatesLink(VIEW_ID.getName());
        }

        return LinkManager.getAdminTemplatesEditLink(VIEW_ID.getName(), typeId);
    }

    @Override
    protected TitleBar getTitleBar() {

        return new TitleBar(this, MSG.view_adminConfig_alertDefTemplates(), ImageManager.getAlertIcon());
    }

    @Override
    protected Collection<ListGridField> getAdditionalListGridFields(boolean isTreeGrid) {
        List<ListGridField> fields = new ArrayList<ListGridField>(2);

        ListGridField enabledTemplatesField = (isTreeGrid) //
        ? new TreeGridField(ATTR_ENABLED_TEMPLATES, MSG.view_adminTemplates_enabledTemplates())
            : new ListGridField(ATTR_ENABLED_TEMPLATES, MSG.view_adminTemplates_enabledTemplates());
        enabledTemplatesField.setWidth("150");
        enabledTemplatesField.setAlign(Alignment.CENTER);
        enabledTemplatesField.setPrompt(MSG.view_adminTemplates_prompt_enabledAlertTemplates());
        fields.add(enabledTemplatesField);

        ListGridField disabledTemplatesField = (isTreeGrid) //
        ? new TreeGridField(ATTR_DISABLED_TEMPLATES, MSG.view_adminTemplates_disabledTemplates())
            : new ListGridField(ATTR_DISABLED_TEMPLATES, MSG.view_adminTemplates_disabledTemplates());
        disabledTemplatesField.setWidth("150");
        disabledTemplatesField.setAlign(Alignment.CENTER);
        disabledTemplatesField.setPrompt(MSG.view_adminTemplates_prompt_disabledAlertTemplates());
        fields.add(disabledTemplatesField);

        return fields;
    }

    @Override
    protected void editTemplates(int resourceTypeId, final ViewPath viewPath) {
        final Integer[] idArray = new Integer[] { resourceTypeId };
        ResourceTypeRepository.Cache.getInstance().getResourceTypes(idArray, new TypesLoadedCallback() {

            public void onTypesLoaded(final Map<Integer, ResourceType> types) {
                new PermissionsLoader().loadExplicitGlobalPermissions(new PermissionsLoadedListener() {

                    public void onPermissionsLoaded(Set<Permission> permissions) {
                        ResourceType rt = types.get(idArray[0]);
                        Layout alertCanvas = getCanvas();
                        String locatorId = extendLocatorId("alertTemplateDef");
                        TemplateAlertDefinitionsView def = new TemplateAlertDefinitionsView(locatorId, rt, permissions);
                        def.renderView(viewPath.next());
                        prepareSubCanvas(alertCanvas, def, viewPath.isEnd()); // don't show our back button if we are going to a template details pane which has its own back button
                        switchToCanvas(AlertDefinitionTemplateTypeView.this, alertCanvas);
                    }
                });
            }
        });
    }

    private Layout getCanvas() {
        if (null == this.canvas) {
            LocatableVLayout layout = new LocatableVLayout(extendLocatorId("alertTemplateLayout"));
            layout.setHeight100();
            layout.setWidth100();
            layout.setMargin(5);
            this.canvas = layout;
        }

        return this.canvas;
    }

    @Override
    protected ResourceTypeTreeNodeBuilder getNodeBuilderInstance(ListGrid platformsList, ListGrid platformServicesList,
        TreeGrid serversTreeGrid) {

        return new NodeBuilder(platformsList, platformServicesList, serversTreeGrid);
    }

    public static class NodeBuilder extends ResourceTypeTreeNodeBuilder {

        public NodeBuilder(ListGrid platformsList, ListGrid platformServicesList, TreeGrid serversTreeGrid) {

            super(platformsList, platformServicesList, serversTreeGrid);
        }

        @Override
        ResourceTypeListGridRecord getGridRecordInstance(ResourceTypeTemplateCountComposite composite) {

            return new GridRecord(composite);
        }

        public static class GridRecord extends ResourceTypeListGridRecord {

            public GridRecord(ResourceTypeTemplateCountComposite composite) {

                super(composite);

                setAttribute(ATTR_ENABLED_TEMPLATES, composite.getEnabledAlertCount());
                setAttribute(ATTR_DISABLED_TEMPLATES, composite.getDisabledAlertCount());
            }
        }

        @Override
        ResourceTypeTreeNode getTreeNodeInstance(ResourceTypeTemplateCountComposite composite, String plugin) {

            return new TreeNode(composite, plugin);
        }

        public static class TreeNode extends ResourceTypeTreeNode {

            public TreeNode(ResourceTypeTemplateCountComposite composite, String plugin) {

                super(composite, plugin);

                setAttribute(ATTR_ENABLED_TEMPLATES, composite.getEnabledAlertCount());
                setAttribute(ATTR_DISABLED_TEMPLATES, composite.getDisabledAlertCount());
            }
        }

    }

}
