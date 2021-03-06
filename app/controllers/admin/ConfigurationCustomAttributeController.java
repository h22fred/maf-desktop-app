/*! LICENSE
 *
 * Copyright (c) 2015, The Agile Factory SA and/or its affiliates. All rights
 * reserved.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package controllers.admin;

import be.objectify.deadbolt.java.actions.Group;
import be.objectify.deadbolt.java.actions.Restrict;
import constants.IMafConstants;
import controllers.ControllersUtils;
import framework.commons.DataType;
import framework.services.configuration.II18nMessagesPlugin;
import framework.utils.*;
import models.framework_models.common.*;
import models.framework_models.common.ICustomAttributeValue.AttributeType;
import play.Configuration;
import play.Logger;
import play.data.Form;
import play.data.validation.Constraints.Required;
import play.mvc.Controller;
import play.mvc.Result;
import services.tableprovider.ITableProvider;
import utils.form.CustomAttributeDefinitionFormData;
import utils.form.CustomAttributeGroupFormData;
import utils.form.CustomAttributeItemFormData;
import utils.table.CustomAttributeGroupListView;
import utils.table.CustomAttributeItemListView;
import utils.table.CustomAttributeListView;

import javax.inject.Inject;
import java.util.*;

/**
 * Manage the custom attributes.
 * 
 * @author Johann Kohler
 * 
 */
@Restrict({ @Group(IMafConstants.ADMIN_CUSTOM_ATTRIBUTE_PERMISSION) })
public class ConfigurationCustomAttributeController extends Controller {

    public static Set<String> unauthorizedAttributeTypes = new HashSet<String>(Arrays.asList("DYNAMIC_SINGLE_ITEM", "DYNAMIC_MULTI_ITEM", "IMAGE"));
    public static Set<String> itemizableAttributeTypes = new HashSet<String>(Arrays.asList("SINGLE_ITEM", "MULTI_ITEM"));

    private static Logger.ALogger log = Logger.of(ConfigurationCustomAttributeController.class);

    private static Form<DataTypeForm> dataTypeFormTemplate = Form.form(DataTypeForm.class);
    private static Form<CustomAttributeDefinitionFormData> customAttributeFormTemplate = Form.form(CustomAttributeDefinitionFormData.class);
    private static Form<CustomAttributeItemFormData> itemFormTemplate = Form.form(CustomAttributeItemFormData.class);
    private static Form<CustomAttributeGroupFormData> groupFormTemplate = Form.form(CustomAttributeGroupFormData.class);

    @Inject
    private II18nMessagesPlugin i18nMessagesPlugin;
    @Inject
    private Configuration configuration;
    @Inject
    private ITableProvider tableProvider;

    /**
     * Display the list of custom attributes for a data type. It's possible to
     * switch to another data type.
     * 
     * @param dataTypeName
     *            the name of the selected data type
     */
    public Result list(String dataTypeName) {

        DataType dataType = DataType.getDataType(dataTypeName);
        if (dataType == null || !dataType.isCustomAttribute()) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        // construct the form to switch the data type
        Form<DataTypeForm> dataTypeForm = dataTypeFormTemplate.fill(new DataTypeForm(dataType));

        // construct the table
        List<CustomAttributeDefinition> customAttributeDefinitions = null;
        try {
            customAttributeDefinitions = CustomAttributeDefinition.getOrderedCustomAttributeDefinitions(Class.forName(dataType.getDataTypeClassName()));
        } catch (Exception e) {
            return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());
        }

        List<CustomAttributeListView> customAttributeListView = new ArrayList<CustomAttributeListView>();
        for (CustomAttributeDefinition customAttributeDefinition : customAttributeDefinitions) {
            customAttributeListView.add(new CustomAttributeListView(customAttributeDefinition));
        }

        Table<CustomAttributeListView> customAttributesTable = this.getTableProvider().get().customAttribute.templateTable.fill(customAttributeListView);

        return ok(views.html.admin.config.customattribute.list.render(dataType, dataTypeForm, customAttributesTable));
    }

    /**
     * Switch to another data type for the custom attributes' list.
     */
    public Result changeDataType() {

        Form<DataTypeForm> boundForm = dataTypeFormTemplate.bindFromRequest();

        DataTypeForm dataTypeForm = boundForm.get();

        DataType dataType = DataType.getDataTypeFromClassName(dataTypeForm.dataTypeClassName);

        return redirect(controllers.admin.routes.ConfigurationCustomAttributeController.list(dataType.getDataName()));
    }

    /**
     * Change the order of a custom attribute (for an object type).
     * 
     * @param id
     *            the custom attribute definition id
     * @param isDecrement
     *            if true then we decrement the order, else we increment it.
     */
    public Result changeOrder(Long id, Boolean isDecrement) {

        CustomAttributeDefinition customAttribute = CustomAttributeDefinition.getCustomAttributeDefinitionFromId(id);

        CustomAttributeDefinition customAttributeToReverse = null;
        try {
            if (isDecrement) {
                customAttributeToReverse = CustomAttributeDefinition.getPrevious(Class.forName(customAttribute.objectType), customAttribute.order);
            } else {
                customAttributeToReverse = CustomAttributeDefinition.getNext(Class.forName(customAttribute.objectType), customAttribute.order);
            }
        } catch (Exception e) {
            return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());
        }

        if (customAttributeToReverse != null) {

            Integer newOrder = customAttributeToReverse.order;

            customAttributeToReverse.order = customAttribute.order;
            customAttributeToReverse.save();

            customAttribute.order = newOrder;
            customAttribute.save();

        }

        this.getTableProvider().flushFilterConfig();
        this.getTableProvider().flushTables();

        DataType dataType = DataType.getDataTypeFromClassName(customAttribute.objectType);

        return redirect(controllers.admin.routes.ConfigurationCustomAttributeController.list(dataType.getDataName()));

    }

    /**
     * Form to create/edit a custom attribute.
     * 
     * @param objectType
     *            the object type (full qualified class name)
     * @param id
     *            the custom attribute definition id (0 for create case)
     */
    public Result manage(String objectType, Long id) {

        DataType dataType = DataType.getDataTypeFromClassName(objectType);
        if (dataType == null || !dataType.isCustomAttribute()) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        Form<CustomAttributeDefinitionFormData> customAttributeForm;

        String uuid = null;
        boolean canAddConditionalRule = true;

        // edit case: inject values
        if (!id.equals(0L)) {

            CustomAttributeDefinition customAttribute = CustomAttributeDefinition.getCustomAttributeDefinitionFromId(id);
            uuid = customAttribute.uuid;
            canAddConditionalRule = customAttribute.isAuthorizedAttributeTypeForConditionalRule();

            customAttributeForm = customAttributeFormTemplate.fill(new CustomAttributeDefinitionFormData(customAttribute, getI18nMessagesPlugin()));

        } else {
            customAttributeForm = customAttributeFormTemplate.fill(new CustomAttributeDefinitionFormData(objectType));
        }

        try {
            return ok(views.html.admin.config.customattribute.manage.render(dataType, customAttributeForm, canAddConditionalRule,
                    CustomAttributeDefinition.getConditionalRuleAuthorizedFields(Class.forName(objectType), uuid)));
        } catch (ClassNotFoundException e) {
            return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());
        }

    }

    /**
     * Process the form to create/edit a custom attribute.
     */
    public Result processManage() {

        // bind the form
        Form<CustomAttributeDefinitionFormData> boundForm = customAttributeFormTemplate.bindFromRequest();

        // get the data type
        String objectType = request().body().asFormUrlEncoded().get("objectType")[0];
        DataType dataType = DataType.getDataTypeFromClassName(objectType);
        if (dataType == null || !dataType.isCustomAttribute()) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        // get the uuid
        String uuid = request().body().asFormUrlEncoded().get("uuid")[0];

        // get canAddConditionalRule
        boolean canAddConditionalRule = Boolean.valueOf(request().body().asFormUrlEncoded().get("canAddConditionalRule")[0]);

        if (boundForm.hasErrors()) {

            try {
                return ok(views.html.admin.config.customattribute.manage.render(dataType, boundForm, canAddConditionalRule,
                        CustomAttributeDefinition.getConditionalRuleAuthorizedFields(Class.forName(objectType), uuid)));
            } catch (ClassNotFoundException e) {
                return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());
            }

        }

        CustomAttributeDefinitionFormData customAttributeDefinitionFormData = boundForm.get();

        // check attribute type is authorized
        if (unauthorizedAttributeTypes.contains(customAttributeDefinitionFormData.attributeType)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        CustomAttributeDefinition customAttribute = null;

        if (customAttributeDefinitionFormData.id == null) { // create case

            customAttribute = new CustomAttributeDefinition();
            customAttribute.objectType = objectType;
            try {
                customAttribute.order = CustomAttributeDefinition.getLastOrder(Class.forName(customAttribute.objectType)) + 1;
            } catch (Exception e) {
                return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());
            }
            customAttribute.uuid = "CUSTOM_ATTRIBUTE_" + dataType.getDataName() + "_" + customAttribute.order;

            customAttributeDefinitionFormData.fill(customAttribute);
            customAttribute.save();

            Utilities.sendSuccessFlashMessage(Msg.get("admin.configuration.custom_attribute.add.successful"));

        } else { // edit case

            customAttribute = CustomAttributeDefinition.getCustomAttributeDefinitionFromId(customAttributeDefinitionFormData.id);

            customAttributeDefinitionFormData.fill(customAttribute);
            customAttribute.update();

            Utilities.sendSuccessFlashMessage(Msg.get("admin.configuration.custom_attribute.edit.successful"));
        }

        customAttributeDefinitionFormData.name.persist(getI18nMessagesPlugin());
        customAttributeDefinitionFormData.description.persist(getI18nMessagesPlugin());

        this.getTableProvider().flushFilterConfig();
        this.getTableProvider().flushTables();
        ScriptCustomAttributeValue.flushCache(customAttributeDefinitionFormData.uuid);

        if (customAttributeDefinitionFormData.id == null && itemizableAttributeTypes.contains(customAttributeDefinitionFormData.attributeType)) {
            return redirect(controllers.admin.routes.ConfigurationCustomAttributeController.items(customAttribute.id));
        } else {
            return redirect(controllers.admin.routes.ConfigurationCustomAttributeController.list(dataType.getDataName()));
        }
    }

    /**
     * List of items for a "select" custom attribute.
     * 
     * @param id
     *            the custom attribute definition id
     */
    public Result items(Long id) {

        CustomAttributeDefinition customAttribute = CustomAttributeDefinition.getCustomAttributeDefinitionFromId(id);

        ISelectableValueHolderCollection<Long> items = null;

        // get the items
        if (customAttribute.attributeType.equals("SINGLE_ITEM")) {
            items = CustomAttributeItemOption.getSelectableValuesForDefinitionId(customAttribute.id);
        } else if (customAttribute.attributeType.equals("MULTI_ITEM")) {
            items = CustomAttributeMultiItemOption.getSelectableValuesForDefinitionId(customAttribute.id);
        }

        // construct the table
        List<CustomAttributeItemListView> customAttributeItemListView = new ArrayList<CustomAttributeItemListView>();
        for (ISelectableValueHolder<Long> valueHolder : items.getSortedValues()) {
            customAttributeItemListView.add(new CustomAttributeItemListView(id, valueHolder));
        }
        Table<CustomAttributeItemListView> customAttributeItemsTable = this.getTableProvider().get().customAttributeItem.templateTable
                .fill(customAttributeItemListView);

        DataType dataType = DataType.getDataTypeFromClassName(customAttribute.objectType);

        return ok(views.html.admin.config.customattribute.items.render(dataType, customAttribute, customAttributeItemsTable));
    }

    /**
     * Change the order of an item.
     * 
     * @param id
     *            the custom attribute definition id
     * @param itemId
     *            the item id
     * @param isDecrement
     *            if true then we decrement the order, else we increment it.
     * @return
     */
    public Result changeItemOrder(Long id, Long itemId, Boolean isDecrement) {

        CustomAttributeDefinition customAttribute = CustomAttributeDefinition.getCustomAttributeDefinitionFromId(id);

        if (customAttribute.attributeType.equals("SINGLE_ITEM")) {

            CustomAttributeItemOption item = CustomAttributeItemOption.getCustomAttributeItemOptionById(itemId);

            CustomAttributeItemOption itemToReverse = null;
            if (isDecrement) {
                itemToReverse = CustomAttributeItemOption.getPrevious(id, item.order);
            } else {
                itemToReverse = CustomAttributeItemOption.getNext(id, item.order);
            }

            if (itemToReverse != null) {

                Integer newOrder = itemToReverse.order;

                itemToReverse.order = item.order;
                itemToReverse.save();

                item.order = newOrder;
                item.save();

            }

        } else if (customAttribute.attributeType.equals("MULTI_ITEM")) {

            CustomAttributeMultiItemOption item = CustomAttributeMultiItemOption.getCustomAttributeMultiItemOptionById(itemId);

            CustomAttributeMultiItemOption itemToReverse = null;
            if (isDecrement) {
                itemToReverse = CustomAttributeMultiItemOption.getPrevious(id, item.order);
            } else {
                itemToReverse = CustomAttributeMultiItemOption.getNext(id, item.order);
            }

            if (itemToReverse != null) {

                Integer newOrder = itemToReverse.order;

                itemToReverse.order = item.order;
                itemToReverse.save();

                item.order = newOrder;
                item.save();

            }

        }

        this.getTableProvider().flushFilterConfig();
        this.getTableProvider().flushTables();

        return redirect(controllers.admin.routes.ConfigurationCustomAttributeController.items(id));
    }

    /**
     * Form to create/edit an item.
     * 
     * @param id
     *            the custom attribute definition id
     * @param itemId
     *            the item id (0 for create)
     */
    public Result manageItem(Long id, Long itemId) {

        CustomAttributeDefinition customAttribute = CustomAttributeDefinition.getCustomAttributeDefinitionFromId(id);

        Form<CustomAttributeItemFormData> itemForm = itemFormTemplate;

        if (customAttribute.attributeType.equals("SINGLE_ITEM")) {

            // edit case: inject values
            if (!itemId.equals(Long.valueOf(0))) {
                CustomAttributeItemOption item = CustomAttributeItemOption.getCustomAttributeItemOptionById(itemId);
                itemForm = itemFormTemplate.fill(new CustomAttributeItemFormData(item, getI18nMessagesPlugin()));
            }

        } else if (customAttribute.attributeType.equals("MULTI_ITEM")) {

            // edit case: inject values
            if (!itemId.equals(Long.valueOf(0))) {
                CustomAttributeMultiItemOption item = CustomAttributeMultiItemOption.getCustomAttributeMultiItemOptionById(itemId);
                itemForm = itemFormTemplate.fill(new CustomAttributeItemFormData(item, getI18nMessagesPlugin()));
            }

        }

        DataType dataType = DataType.getDataTypeFromClassName(customAttribute.objectType);

        return ok(views.html.admin.config.customattribute.item_manage.render(dataType, customAttribute, itemForm));
    }

    /**
     * Process the form to create/edit an item.
     */
    public Result processManageItem() {

        // bind the form
        Form<CustomAttributeItemFormData> boundForm = itemFormTemplate.bindFromRequest();

        // get the custom attribute
        Long id = Long.valueOf(request().body().asFormUrlEncoded().get("customAttributeDefinitionId")[0]);
        CustomAttributeDefinition customAttribute = CustomAttributeDefinition.getCustomAttributeDefinitionFromId(id);

        // get the data type
        DataType dataType = DataType.getDataTypeFromClassName(customAttribute.objectType);

        if (boundForm.hasErrors()) {
            return ok(views.html.admin.config.customattribute.item_manage.render(dataType, customAttribute, boundForm));
        }

        CustomAttributeItemFormData itemFormData = boundForm.get();

        if (customAttribute.attributeType.equals("SINGLE_ITEM")) {

            CustomAttributeItemOption item = null;

            if (itemFormData.id == null) { // create case

                item = new CustomAttributeItemOption();
                item.customAttributeDefinition = customAttribute;
                item.order = CustomAttributeItemOption.getLastOrder(customAttribute.id) + 1;

                itemFormData.fill(item);
                item.save();

                Utilities.sendSuccessFlashMessage(Msg.get("admin.configuration.custom_attribute.item.add.successful"));

            } else { // edit case

                item = CustomAttributeItemOption.getCustomAttributeItemOptionById(itemFormData.id);

                itemFormData.fill(item);
                item.update();

                Utilities.sendSuccessFlashMessage(Msg.get("admin.configuration.custom_attribute.item.edit.successful"));
            }

            itemFormData.name.persist(getI18nMessagesPlugin());
            itemFormData.description.persist(getI18nMessagesPlugin());

        } else if (customAttribute.attributeType.equals("MULTI_ITEM")) {

            CustomAttributeMultiItemOption item = null;

            if (itemFormData.id == null) { // create case

                item = new CustomAttributeMultiItemOption();
                item.customAttributeDefinition = customAttribute;
                item.order = CustomAttributeMultiItemOption.getLastOrder(customAttribute.id) + 1;

                itemFormData.fill(item);
                item.save();

                Utilities.sendSuccessFlashMessage(Msg.get("admin.configuration.custom_attribute.item.add.successful"));

            } else { // edit case

                item = CustomAttributeMultiItemOption.getCustomAttributeMultiItemOptionById(itemFormData.id);

                itemFormData.fill(item);
                item.update();

                Utilities.sendSuccessFlashMessage(Msg.get("admin.configuration.custom_attribute.item.edit.successful"));
            }

            itemFormData.name.persist(getI18nMessagesPlugin());
            itemFormData.description.persist(getI18nMessagesPlugin());

        }

        this.getTableProvider().flushFilterConfig();
        this.getTableProvider().flushTables();

        return redirect(controllers.admin.routes.ConfigurationCustomAttributeController.items(customAttribute.id));

    }

    /**
     * Delete an item.
     * 
     * @param id
     *            the custom attribute definition id
     * @param itemId
     *            the item id
     */
    public Result deleteItem(Long id, Long itemId) {

        CustomAttributeDefinition customAttribute = CustomAttributeDefinition.getCustomAttributeDefinitionFromId(id);

        if (customAttribute.attributeType.equals("SINGLE_ITEM")) {

            CustomAttributeItemOption item = CustomAttributeItemOption.getCustomAttributeItemOptionById(itemId);
            item.doDelete();

        } else if (customAttribute.attributeType.equals("MULTI_ITEM")) {

            CustomAttributeMultiItemOption item = CustomAttributeMultiItemOption.getCustomAttributeMultiItemOptionById(itemId);
            item.doDelete();

        }

        Utilities.sendSuccessFlashMessage(Msg.get("admin.configuration.custom_attribute.item.delete.successful"));

        this.getTableProvider().flushFilterConfig();
        this.getTableProvider().flushTables();

        return redirect(controllers.admin.routes.ConfigurationCustomAttributeController.items(customAttribute.id));

    }

    /**
     * Delete a custom attribute.
     * 
     * @param id
     *            the custom attribute definition id
     */
    public Result delete(Long id) {

        CustomAttributeDefinition customAttribute = CustomAttributeDefinition.getCustomAttributeDefinitionFromId(id);

        // check attribute type is authorized
        if (unauthorizedAttributeTypes.contains(customAttribute.attributeType)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        customAttribute.doDelete();

        Utilities.sendSuccessFlashMessage(Msg.get("admin.configuration.custom_attribute.delete.successful"));

        this.getTableProvider().flushFilterConfig();
        this.getTableProvider().flushTables();

        DataType dataType = DataType.getDataTypeFromClassName(customAttribute.objectType);

        return redirect(controllers.admin.routes.ConfigurationCustomAttributeController.list(dataType.getDataName()));
    }

    /**
     * Displays the table of all the groups defined for a data type
     *
     * @param dataTypeName the data type name
     */
    public Result groups(String dataTypeName) {
        DataType dataType = DataType.getDataType(dataTypeName);

        List<CustomAttributeGroup> groups = CustomAttributeGroup.getOrderedCustomAttributeGroupsByObjectType(dataType.getDataTypeClassName());

        if (groups.size() == 0) {
            groups.add(CustomAttributeGroup.getOrCreateDefaultGroup(dataType.getDataTypeClassName()));
        }

        List<CustomAttributeGroupListView> listView = new ArrayList<>(groups.size());

        groups.stream().forEach(group -> listView.add(new CustomAttributeGroupListView(group)));

        Table<CustomAttributeGroupListView> table = this.getTableProvider().get().customAttributeGroup.templateTable.fill(listView);

        return ok(views.html.admin.config.customattribute.group.render(dataType, table));
    }

    /**
     * Changes the display order of the group
     *
     * @param id the id of the group
     * @param isDecrement true if the order is to be decremented, false instead
     */
    public Result changeGroupOrder(Long id, Boolean isDecrement) {

        CustomAttributeGroup group = CustomAttributeGroup.getById(id);
        CustomAttributeGroup groupToReverse = isDecrement ? group.previous() : group.next();

        if (groupToReverse != null) {
            Integer newOrder = groupToReverse.order;

            groupToReverse.order = group.order;
            groupToReverse.save();

            group.order = newOrder;
            group.save();
        }

        this.getTableProvider().flushFilterConfig();
        this.getTableProvider().flushTables();

        DataType dataType = DataType.getDataTypeFromClassName(group.objectType);
        return redirect(controllers.admin.routes.ConfigurationCustomAttributeController.groups(dataType != null ? dataType.getDataName() : null));
    }

    /**
     * Displays the form for creating or editing a group
     *
     * @param dataTypeName the custom attribute associated {@link DataType}
     * @param id the id of the group (0 if it is a creation)
     */
    public Result manageGroup(String dataTypeName, Long id) {
        DataType dataType = DataType.getDataType(dataTypeName);

        Form<CustomAttributeGroupFormData> form = groupFormTemplate;

        if (!id.equals(0L)) {
            CustomAttributeGroup customAttributeGroup = CustomAttributeGroup.getById(id);
            form = groupFormTemplate.fill(new CustomAttributeGroupFormData(customAttributeGroup, getI18nMessagesPlugin()));
        }

        return ok(views.html.admin.config.customattribute.group_manage.render(dataType, form));
    }

    /**
     * Process the form for creating or editing a group
     */
    public Result processManageGroup() {

        Form<CustomAttributeGroupFormData> form = groupFormTemplate.bindFromRequest();
        CustomAttributeGroupFormData formData = form.get();

        DataType dataType = DataType.getDataTypeFromClassName(formData.objectType);

        if (form.hasErrors()) {
            return ok(views.html.admin.config.customattribute.group_manage.render(dataType, form));
        }


        CustomAttributeGroup group;

        if (formData.id == null) {
            group = new CustomAttributeGroup();
            group.order = CustomAttributeGroup.getLastOrder(formData.objectType) + 1;
            formData.fill(group);
            group.save();

            Utilities.sendSuccessFlashMessage(Msg.get("admin.configuration.custom_attribute.group.add.successful"));
        } else {

            group = CustomAttributeGroup.getById(formData.id);

            formData.fill(group);
            group.update();

            Utilities.sendSuccessFlashMessage(Msg.get("admin.configuration.custom_attribute.group.edit.successful"));
        }

        formData.label.persist(getI18nMessagesPlugin());

        this.getTableProvider().flushFilterConfig();
        this.getTableProvider().flushTables();

        return redirect(routes.ConfigurationCustomAttributeController.groups(dataType != null ? dataType.getDataName() : null));
    }

    /**
     * Delete a {@link CustomAttributeGroup}
     *
     * Deleting a group is not possible if some {@link CustomAttributeDefinition} still belong to that group <br />
     * or if it is the last group of its type.
     *
     * @param id the id of the custom attribute group
     */
    public Result deleteGroup(Long id) {

        CustomAttributeGroup group = CustomAttributeGroup.getById(id);
        List<CustomAttributeGroup> groups = CustomAttributeGroup.getOrderedCustomAttributeGroupsByObjectType(group.objectType);
        DataType dataType = DataType.getDataTypeFromClassName(group.objectType);

        if (!group.customAttributeDefinitions.isEmpty()) {
            Utilities.sendErrorFlashMessage(Msg.get("admin.configuration.custom_attribute.group.delete.error.group_not_empty"));
        } else if (groups.size() == 1) {
            Utilities.sendErrorFlashMessage(Msg.get("admin.configuration.custom_attribute.group.delete.error.last_group"));
        } else {
            group.doDelete();
            Utilities.sendSuccessFlashMessage(Msg.get("admin.configuration.custom_attribute.group.delete.successful"));
        }

        this.getTableProvider().flushFilterConfig();
        this.getTableProvider().flushTables();

        return redirect(routes.ConfigurationCustomAttributeController.groups(dataType != null ? dataType.getDataName() : null));
    }

    /**
     * Get all data types that supports the custom attributes.
     */
    public static ISelectableValueHolderCollection<String> getAllDataTypes() {
        DefaultSelectableValueHolderCollection<String> valueHolderCollection = new DefaultSelectableValueHolderCollection<String>();
        for (DataType dataType : DataType.getAllCustomAttributeDataTypes()) {
            valueHolderCollection.add(new DefaultSelectableValueHolder<String>(dataType.getDataTypeClassName(), Msg.get(dataType.getLabel())));
        }
        return valueHolderCollection;
    }

    /**
     * Get all authorized attribute types.
     */
    public static ISelectableValueHolderCollection<String> getAllAttributeTypes() {
        DefaultSelectableValueHolderCollection<String> valueHolderCollection = new DefaultSelectableValueHolderCollection<String>();
        for (AttributeType attributeType : ICustomAttributeValue.AttributeType.values()) {
            if (!unauthorizedAttributeTypes.contains(attributeType.name())) {
                valueHolderCollection.add(new DefaultSelectableValueHolder<String>(attributeType.name(), attributeType.getLabel()));
            }
        }
        return valueHolderCollection;
    }

    /**
     * The data type form.
     * 
     * @author Johann Kohler
     * 
     */
    public static class DataTypeForm {

        @Required
        public String dataTypeClassName;

        /**
         * Default constructor.
         */
        public DataTypeForm() {
        }

        /**
         * Initialize the form with a default value.
         * 
         * @param selected
         *            the selected data type.
         */
        public DataTypeForm(DataType selected) {
            this.dataTypeClassName = selected.getDataTypeClassName();
        }

    }

    /**
     * Get the i18n messages service.
     */
    private II18nMessagesPlugin getI18nMessagesPlugin() {
        return i18nMessagesPlugin;
    }

    /**
     * Get the Play configuration service.
     */
    private Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Get the table provider.
     */
    private ITableProvider getTableProvider() {
        return this.tableProvider;
    }

}
