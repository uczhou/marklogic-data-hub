/*
 * Copyright 2012-2019 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.marklogic.hub.deploy.commands;

import com.marklogic.appdeployer.command.AbstractResourceCommand;
import com.marklogic.appdeployer.command.CommandContext;
import com.marklogic.appdeployer.command.SortOrderConstants;
import com.marklogic.mgmt.resource.ResourceManager;
import com.marklogic.mgmt.resource.databases.DatabaseManager;
import com.marklogic.rest.util.Fragment;
import org.apache.commons.lang3.StringUtils;
import org.jdom2.Element;
import org.jdom2.Namespace;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Per DHFPROD-2554, this command ensures that when it adds DHF-specific fields and range field indexes to the
 * staging, final, and jobs databases, it will not drop any existing fields or range field indexes.
 * <p>
 * The reason that these fields are applied via an XML file is to avoid a bug specific to JSON files and the Manage
 * API that is not yet fixed in ML 9.0-9.
 */
public class DeployDatabaseFieldCommand extends AbstractResourceCommand {

    private final static Namespace MANAGE_NS = Namespace.getNamespace("http://marklogic.com/manage");

    public DeployDatabaseFieldCommand() {
        setExecuteSortOrder(SortOrderConstants.DEPLOY_OTHER_DATABASES + 1);
    }

    @Override
    protected File[] getResourceDirs(CommandContext context) {
        return findResourceDirs(context, configDir -> new File(configDir.getBaseDir(), "database-fields"));
    }

    @Override
    protected ResourceManager getResourceManager(CommandContext commandContext) {
        return new DatabaseManager(commandContext.getManageClient());
    }

    @Override
    protected String adjustPayloadBeforeSavingResource(CommandContext context, File f, String payload) {
        payload = super.adjustPayloadBeforeSavingResource(context, f, payload);
        return addExistingFieldsAndRangeFieldIndexes(payload, new DatabaseManager(context.getManageClient()));
    }

    @Override
    public void undo(CommandContext context) {
        //no-op as deleting database will delete all the fields and indexes associated with it.
        logger.info("No action required on undeploy, as the command for deleting databases on undeploy will also delete " +
            "the fields and indexes created by this command.");
    }

    protected String addExistingFieldsAndRangeFieldIndexes(String payload, ResourceManager dbManager) {
        Fragment newProps = new Fragment(payload);
        Fragment existingProps = dbManager.getPropertiesAsXml(newProps.getElementValue("/node()/m:database-name"));

        addExistingFields(newProps, existingProps);
        addExistingRangeFieldIndexes(newProps, existingProps);
        addExistingRangePathIndexes(newProps, existingProps);
        return newProps.getPrettyXml();
    }

    protected void addExistingFields(Fragment newProps, Fragment existingProps) {
        Element newFields = newProps.getInternalDoc().getRootElement().getChild("fields", MANAGE_NS);
        if (newFields != null) {
            List<String> newFieldNames = new ArrayList<>();
            newProps.getElements("/m:database-properties/m:fields/m:field").forEach(field -> {
                String name = field.getChildText("field-name", MANAGE_NS);
                if (StringUtils.isNotBlank(name)) {
                    newFieldNames.add(name);
                }
            });

            for (Element field : existingProps.getElements("/m:database-properties/m:fields/m:field")) {
                String name = field.getChildText("field-name", MANAGE_NS);
                if (StringUtils.isNotBlank(name) && !newFieldNames.contains(name)) {
                    newFields.addContent(field.detach());
                }
            }
        }
    }

    protected void addExistingRangeFieldIndexes(Fragment newProps, Fragment existingProps) {
        Element newRangeFieldIndexes = newProps.getInternalDoc().getRootElement().getChild("range-field-indexes", MANAGE_NS);
        if (newRangeFieldIndexes != null) {
            List<String> newIndexFieldNames = new ArrayList<>();
            newProps.getElements("/m:database-properties/m:range-field-indexes/m:range-field-index").forEach(index -> {
                String name = index.getChildText("field-name", MANAGE_NS);
                if (StringUtils.isNotBlank(name)) {
                    newIndexFieldNames.add(name);
                }
            });

            for (Element index : existingProps.getElements("/m:database-properties/m:range-field-indexes/m:range-field-index")) {
                String name = index.getChildText("field-name", MANAGE_NS);
                if (StringUtils.isNotBlank(name) && !newIndexFieldNames.contains(name)) {
                    newRangeFieldIndexes.addContent(index.detach());
                }
            }
        }
    }

    protected void addExistingRangePathIndexes(Fragment newProps, Fragment existingProps) {
        Element newRangePathIndexes = newProps.getInternalDoc().getRootElement().getChild("range-path-indexes", MANAGE_NS);
        if (newRangePathIndexes != null) {
            List<String> newIndexPathExpressions = new ArrayList<>();
            newProps.getElements("/m:database-properties/m:range-path-indexes/m:range-path-index").forEach(index -> {
                String pathExpression = index.getChildText("path-expression", MANAGE_NS);
                if (StringUtils.isNotBlank(pathExpression)) {
                    newIndexPathExpressions.add(pathExpression);
                }
            });

            for (Element index : existingProps.getElements("/m:database-properties/m:range-path-indexes/m:range-path-index")) {
                String pathExpression = index.getChildText("path-expression", MANAGE_NS);
                if (StringUtils.isNotBlank(pathExpression) && !newIndexPathExpressions.contains(pathExpression)) {
                    newRangePathIndexes.addContent(index.detach());
                }
            }
        }
    }
}
