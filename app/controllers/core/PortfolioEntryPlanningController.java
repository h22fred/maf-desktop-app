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
package controllers.core;

import be.objectify.deadbolt.java.actions.Dynamic;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.ExpressionList;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import constants.IMafConstants;
import controllers.ControllersUtils;
import dao.delivery.IterationDAO;
import dao.finance.PortfolioEntryResourcePlanDAO;
import dao.governance.LifeCycleMilestoneDao;
import dao.governance.LifeCyclePlanningDao;
import dao.pmo.ActorDao;
import dao.pmo.PortfolioEntryDao;
import dao.pmo.PortfolioEntryPlanningPackageDao;
import dao.pmo.StakeholderDao;
import dao.timesheet.TimesheetDao;
import framework.security.ISecurityService;
import framework.services.account.AccountManagementException;
import framework.services.account.IPreferenceManagerPlugin;
import framework.services.configuration.II18nMessagesPlugin;
import framework.services.custom_attribute.ICustomAttributeManagerService;
import framework.services.notification.INotificationManagerPlugin;
import framework.services.session.IUserSessionManagerPlugin;
import framework.services.storage.IAttachmentManagerPlugin;
import framework.utils.*;
import models.common.ResourceAllocationDetail;
import models.delivery.Iteration;
import models.finance.*;
import models.framework_models.account.NotificationCategory;
import models.framework_models.account.NotificationCategory.Code;
import models.framework_models.common.Attachment;
import models.governance.LifeCycleInstance;
import models.governance.LifeCycleInstancePlanning;
import models.governance.LifeCyclePhase;
import models.governance.PlannedLifeCycleMilestoneInstance;
import models.pmo.*;
import org.apache.commons.lang3.tuple.Pair;
import play.Configuration;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import security.CheckPortfolioEntryExists;
import services.budgettracking.IBudgetTrackingService;
import services.datasyndication.IDataSyndicationService;
import services.datasyndication.models.DataSyndicationAgreementItem;
import services.datasyndication.models.DataSyndicationAgreementLink;
import services.tableprovider.ITableProvider;
import utils.SortableCollection;
import utils.SortableCollection.ComplexSortableObject;
import utils.SortableCollection.DateSortableObject;
import utils.SortableCollection.ISortableObject;
import utils.form.*;
import utils.gantt.SourceDataValue;
import utils.gantt.SourceItem;
import utils.gantt.SourceValue;
import utils.table.*;
import views.html.core.portfolioentryplanning.allocated_org_unit_manage;
import views.html.error.access_forbidden;

import javax.inject.Inject;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The controller which allows to manage the planning of a portfolio entry.
 * 
 * @author Johann Kohler
 */
public class PortfolioEntryPlanningController extends Controller {

    @Inject
    private II18nMessagesPlugin messagesPlugin;
    @Inject
    private IAttachmentManagerPlugin attachmentManagerPlugin;
    @Inject
    private IUserSessionManagerPlugin userSessionManagerPlugin;
    @Inject
    private ISecurityService securityService;
    @Inject
    private Configuration configuration;
    @Inject
    private IDataSyndicationService dataSyndicationService;
    @Inject
    private IPreferenceManagerPlugin preferenceManagerPlugin;
    @Inject
    private II18nMessagesPlugin i18nMessagesPlugin;
    @Inject
    private IBudgetTrackingService budgetTrackingService;
    @Inject
    private INotificationManagerPlugin notificationManagerService;
    @Inject
    private ITableProvider tableProvider;
    @Inject
    private ICustomAttributeManagerService customAttributeManagerService;

    private static Logger.ALogger log = Logger.of(PortfolioEntryPlanningController.class);

    public static Form<OverviewConfiguration> overviewConfigurationFormTemplate = Form.form(OverviewConfiguration.class);
    public static Form<PortfolioEntryPlanningPackageFormData> planningPackageFormTemplate = Form.form(PortfolioEntryPlanningPackageFormData.class);
    public static Form<PortfolioEntryPlanningPackageGroupsFormData> planningPackageGroupsFormTemplate = Form
            .form(PortfolioEntryPlanningPackageGroupsFormData.class);
    public static Form<PortfolioEntryResourcePlanAllocatedActorFormData> allocatedActorFormTemplate = Form
            .form(PortfolioEntryResourcePlanAllocatedActorFormData.class);
    public static Form<PortfolioEntryResourcePlanAllocatedOrgUnitFormData> allocatedOrgUnitFormTemplate = Form
            .form(PortfolioEntryResourcePlanAllocatedOrgUnitFormData.class);
    public static Form<PortfolioEntryResourcePlanAllocatedCompetencyFormData> allocatedCompetencyFormTemplate = Form
            .form(PortfolioEntryResourcePlanAllocatedCompetencyFormData.class);
    public static Form<PortfolioEntryResourcePlanAllocatedActorFormData> reallocateOrgUnitFormTemplate = Form
            .form(PortfolioEntryResourcePlanAllocatedActorFormData.class);
    public static Form<PortfolioEntryResourcePlanAllocatedActorFormData> reallocateCompetencyFormTemplate = Form
            .form(PortfolioEntryResourcePlanAllocatedActorFormData.class);
    private static Form<AttachmentFormData> attachmentFormTemplate = Form.form(AttachmentFormData.class);
    private static Form<PortfolioEntryPlanningPackagesFormData> planningPackagesFormDataTemplate = Form.form(PortfolioEntryPlanningPackagesFormData.class);

    /**
     * Display global gantt chart of the initiative.
     * 
     * Life cycle phases of the process (with is_roadmap_phase = false)<br/>
     * Life cycle milestones (diamond)<br/>
     * Planning packages<br/>
     * Iterations<br/>
     * 
     * Configuration:<br/>
     * -Display phases<br/>
     * -Display milestones<br/>
     * -Display packages<br/>
     * -Display iterations<br/>
     * 
     * 
     * @param id
     *            the portfolio entry id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result overview(Long id) {

        // load the overview configuration from the user preference
        OverviewConfiguration conf = OverviewConfiguration.load(this.getPreferenceManagerPlugin());

        // prepare the overview configuration form
        Form<OverviewConfiguration> overviewConfigurationForm = overviewConfigurationFormTemplate.fill(conf);

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the active life cycle process instance
        LifeCycleInstance processInstance = portfolioEntry.activeLifeCycleInstance;

        // get the last planned milestone instances
        List<PlannedLifeCycleMilestoneInstance> lastPlannedMilestoneInstances = LifeCyclePlanningDao
                .getPlannedLCMilestoneInstanceLastAsListByPE(portfolioEntry.id);

        // initiate the sortable collection
        SortableCollection<ComplexSortableObject> sortableCollection = new SortableCollection<>();

        /** Life cycle phases of the process (with is_roadmap_phase = false) **/

        if (conf.phases) {

            // get the all phases of a process
            List<LifeCyclePhase> lifeCyclePhases = LifeCycleMilestoneDao.getLCPhaseAsListByLCProcess(processInstance.lifeCycleProcess.id);

            if (lifeCyclePhases != null && !lifeCyclePhases.isEmpty() && lastPlannedMilestoneInstances != null && lastPlannedMilestoneInstances.size() > 0) {

                // get the CSS class
                String cssClass = "success";

                // create the source data value
                SourceDataValue dataValue = new SourceDataValue(controllers.core.routes.PortfolioEntryGovernanceController.index(portfolioEntry.id).url(),
                        null, null, null, null);

                // transform the list of last planned milestone instances to a
                // map
                Map<Long, PlannedLifeCycleMilestoneInstance> lastPlannedMilestoneInstancesAsMap = new HashMap<Long, PlannedLifeCycleMilestoneInstance>();
                for (PlannedLifeCycleMilestoneInstance plannedMilestoneInstance : lastPlannedMilestoneInstances) {
                    lastPlannedMilestoneInstancesAsMap.put(plannedMilestoneInstance.lifeCycleMilestone.id, plannedMilestoneInstance);
                }

                for (LifeCyclePhase phase : lifeCyclePhases) {

                    if (lastPlannedMilestoneInstancesAsMap.containsKey(phase.startLifeCycleMilestone.id)
                            && lastPlannedMilestoneInstancesAsMap.containsKey(phase.endLifeCycleMilestone.id)) {

                        // get the from date
                        Date from = LifeCyclePlanningDao
                                .getPlannedLCMilestoneInstanceAsPassedDate(lastPlannedMilestoneInstancesAsMap.get(phase.startLifeCycleMilestone.id).id);

                        // get the to date
                        Date to = LifeCyclePlanningDao
                                .getPlannedLCMilestoneInstanceAsPassedDate(lastPlannedMilestoneInstancesAsMap.get(phase.endLifeCycleMilestone.id).id);

                        if (from != null && to != null) {

                            to = JqueryGantt.cleanToDate(from, to);

                            // add gap for the from date
                            if (phase.gapDaysStart != null && phase.gapDaysStart.intValue() > 0) {
                                Calendar c = Calendar.getInstance();
                                c.setTime(from);
                                c.add(Calendar.DATE, phase.gapDaysStart);
                                from = c.getTime();
                            }

                            // remove gap for the to date
                            if (phase.gapDaysEnd != null && phase.gapDaysEnd.intValue() > 0) {
                                Calendar c = Calendar.getInstance();
                                c.setTime(to);
                                c.add(Calendar.DATE, -1 * phase.gapDaysEnd);
                                to = c.getTime();
                            }

                            SourceItem item = new SourceItem(phase.getName(), null);

                            item.values.add(new SourceValue(from, to, "", phase.getName(), cssClass, dataValue));

                            sortableCollection.addObject(new ComplexSortableObject(from, 1, phase.order != null ? phase.order : 0, item));

                        }

                    }

                }

            }

        }

        /** Life cycle milestones (diamond) **/

        if (conf.milestones && lastPlannedMilestoneInstances != null && lastPlannedMilestoneInstances.size() > 0) {

            // get the CSS class
            String cssClass = "diamond diamond-danger";

            for (PlannedLifeCycleMilestoneInstance plannedMilestoneInstance : lastPlannedMilestoneInstances) {

                // create the source data value
                SourceDataValue dataValue = new SourceDataValue(controllers.core.routes.PortfolioEntryGovernanceController
                        .viewMilestone(portfolioEntry.id, plannedMilestoneInstance.lifeCycleMilestone.id).url(), null, null, null, null);

                // get the from date
                Date from = LifeCyclePlanningDao.getPlannedLCMilestoneInstanceAsPassedDate(plannedMilestoneInstance.id);

                // get the to date
                Date to = from;

                if (from != null) {

                    SourceItem item = new SourceItem(plannedMilestoneInstance.lifeCycleMilestone.getShortName(), null);

                    item.values.add(new SourceValue(from, to, "", "", cssClass, dataValue));

                    sortableCollection.addObject(new ComplexSortableObject(from, 2, 0, item));

                }

            }

        }

        /** Planning packages **/

        if (conf.packages) {

            for (PortfolioEntryPlanningPackage planningPackage : PortfolioEntryPlanningPackageDao.getPEPlanningPackageAsListByPE(portfolioEntry.id)) {

                // create the source data value
                SourceDataValue dataValue = new SourceDataValue(
                        controllers.core.routes.PortfolioEntryPlanningController.viewPackage(portfolioEntry.id, planningPackage.id).url(), null, null, null,
                        null);

                // get the from date
                Date from = planningPackage.startDate;

                // get the to date
                Date to = planningPackage.endDate;

                if (to != null) {

                    String cssClass = null;

                    if (from != null) {

                        to = JqueryGantt.cleanToDate(from, to);
                        cssClass = "warning";

                    } else {

                        from = to;
                        cssClass = "diamond diamond-warning";

                    }

                    SourceItem item = new SourceItem(planningPackage.name, null);

                    item.values.add(new SourceValue(from, to, "", planningPackage.name, cssClass, dataValue));

                    sortableCollection.addObject(new ComplexSortableObject(from, 5, 0, item));

                }

            }

        }

        /** Iterations **/

        if (conf.iterations) {

            for (Iteration iteration : IterationDAO.getIterationAllAsListByPE(portfolioEntry.id)) {

                SourceDataValue dataValue = new SourceDataValue(
                        controllers.core.routes.PortfolioEntryDeliveryController.viewIteration(portfolioEntry.id, iteration.id).url(), null, null, null,
                        null);

                // get the from date
                Date from = iteration.startDate;

                // get the to date
                Date to = iteration.endDate;

                if (to != null) {

                    String cssClass = null;

                    if (from != null) {

                        to = JqueryGantt.cleanToDate(from, to);
                        cssClass = "primary";

                    } else {

                        from = to;
                        cssClass = "diamond diamond-primary";

                    }

                    SourceItem item = new SourceItem(iteration.name, null);

                    item.values.add(new SourceValue(from, to, "", iteration.name, cssClass, dataValue));

                    sortableCollection.addObject(new ComplexSortableObject(from, 4, 0, item));

                }

            }

        }

        /** construct the gantt **/

        List<SourceItem> items = new ArrayList<SourceItem>();
        for (ISortableObject sortableObject : sortableCollection.getSorted()) {
            Logger.debug(sortableObject.getSortableAttributesAsString());
            SourceItem item = (SourceItem) sortableObject.getObject();
            items.add(item);
        }

        String ganttSource = "";
        try {
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            ganttSource = ow.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            Logger.error(e.getMessage());
        }

        return ok(views.html.core.portfolioentryplanning.overview.render(portfolioEntry, ganttSource, overviewConfigurationForm));

    }

    /**
     * Change the configuration of the overview planning.
     * 
     * @param id
     *            the portfolio entry id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result overviewChangeConf(Long id) {

        Form<OverviewConfiguration> boundForm = overviewConfigurationFormTemplate.bindFromRequest();

        OverviewConfiguration conf = boundForm.get();

        OverviewConfiguration.store(this.getPreferenceManagerPlugin(), conf);

        return redirect(controllers.core.routes.PortfolioEntryPlanningController.overview(id));
    }

    /**
     * Display the planning packages of a portfolio entry with a gantt view.
     * 
     * @param id
     *            the portfolio entry id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result packages(Long id) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        try {

            // get the filter config
            String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());
            FilterConfig<PortfolioEntryPlanningPackageListView> filterConfig = this.getTableProvider().get().portfolioEntryPlanningPackage.filterConfig
                    .getCurrent(uid, request());

            // get the table
            Pair<Table<PortfolioEntryPlanningPackageListView>, Pagination<PortfolioEntryPlanningPackage>> t = getPackagesTable(id, filterConfig,
                    getMessagesPlugin());

            // get the available groups
            List<PortfolioEntryPlanningPackageGroup> groups = PortfolioEntryPlanningPackageDao.getPEPlanningPackageGroupActiveNonEmptyAsList();

            // get the syndicated reports
            List<DataSyndicationAgreementLink> agreementLinks = new ArrayList<>();
            DataSyndicationAgreementItem agreementItem = null;
            if (portfolioEntry.isSyndicated && getDataSyndicationService().isActive()) {
                try {
                    agreementItem = getDataSyndicationService().getAgreementItemByDataTypeAndDescriptor(PortfolioEntry.class.getName(), "PLANNING_PACKAGE");
                    if (agreementItem != null) {
                        agreementLinks = getDataSyndicationService().getAgreementLinksOfItemAndSlaveObject(agreementItem, PortfolioEntry.class.getName(), id);
                    }
                } catch (Exception e) {
                    Logger.error("impossible to get the syndicated planning package data", e);
                }
            }

            // construct the gantt

            ExpressionList<PortfolioEntryPlanningPackage> expressionList = filterConfig
                    .updateWithSearchExpression(PortfolioEntryPlanningPackageDao.getPEPlanningPackageAsExprByPE(id));
            filterConfig.updateWithSortExpression(expressionList);

            // initiate the sortable collection
            SortableCollection<DateSortableObject> sortableCollection = new SortableCollection<>();

            for (PortfolioEntryPlanningPackage planningPackage : expressionList.findList()) {

                // get the from date
                Date from = planningPackage.startDate;

                // get the to date
                Date to = planningPackage.endDate;

                if (to != null) {

                    SourceItem item = new SourceItem(planningPackage.name, "");

                    String cssClass = null;

                    if (from != null) {

                        to = JqueryGantt.cleanToDate(from, to);
                        cssClass = planningPackage.portfolioEntryPlanningPackageType.cssClass;

                    } else {

                        from = to;
                        cssClass = "diamond diamond-" + planningPackage.portfolioEntryPlanningPackageType.cssClass;

                    }

                    item.values.add(new SourceValue(from, to, "", "", cssClass, null));

                    sortableCollection.addObject(new DateSortableObject(from, item));

                }

            }

            List<SourceItem> items = new ArrayList<SourceItem>();
            for (ISortableObject sortableObject : sortableCollection.getSorted()) {
                SourceItem item = (SourceItem) sortableObject.getObject();
                items.add(item);
            }

            String ganttSource = "";
            try {
                ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
                ganttSource = ow.writeValueAsString(items);
            } catch (JsonProcessingException e) {
                Logger.error(e.getMessage());
            }

            return ok(views.html.core.portfolioentryplanning.packages.render(portfolioEntry, t.getLeft(), t.getRight(), filterConfig, groups, ganttSource,
                    agreementLinks, agreementItem));

        } catch (Exception e) {

            return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getMessagesPlugin());

        }

    }

    /**
     * Filter the packages.
     * 
     * @param id
     *            the portfolio entry id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result packagesFilter(Long id) {

        try {

            // get the filter config
            String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());
            FilterConfig<PortfolioEntryPlanningPackageListView> filterConfig = this.getTableProvider().get().portfolioEntryPlanningPackage.filterConfig
                    .persistCurrentInDefault(uid, request());

            if (filterConfig == null) {
                return ok(views.html.framework_views.parts.table.dynamic_tableview_no_more_compatible.render());
            } else {

                // get the table
                Pair<Table<PortfolioEntryPlanningPackageListView>, Pagination<PortfolioEntryPlanningPackage>> t = getPackagesTable(id, filterConfig,
                        getMessagesPlugin());

                return ok(views.html.framework_views.parts.table.dynamic_tableview.render(t.getLeft(), t.getRight()));

            }

        } catch (Exception e) {
            return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getMessagesPlugin());
        }

    }

    /**
     * Get the packages table for a portfolio entry and a filter config.
     * 
     * @param portfolioEntryId
     *            the portfolio entry id
     * @param filterConfig
     *            the filter config.
     * @param messagesPlugin
     *            the message service
     */
    private Pair<Table<PortfolioEntryPlanningPackageListView>, Pagination<PortfolioEntryPlanningPackage>> getPackagesTable(Long portfolioEntryId,
            FilterConfig<PortfolioEntryPlanningPackageListView> filterConfig, II18nMessagesPlugin messagesPlugin) {

        ExpressionList<PortfolioEntryPlanningPackage> expressionList = filterConfig
                .updateWithSearchExpression(PortfolioEntryPlanningPackageDao.getPEPlanningPackageAsExprByPE(portfolioEntryId));
        filterConfig.updateWithSortExpression(expressionList);

        Pagination<PortfolioEntryPlanningPackage> pagination = new Pagination<PortfolioEntryPlanningPackage>(this.getPreferenceManagerPlugin(),
                expressionList);
        pagination.setCurrentPage(filterConfig.getCurrentPage());

        List<PortfolioEntryPlanningPackageListView> portfolioEntryPlanningPackageListView = new ArrayList<PortfolioEntryPlanningPackageListView>();
        for (PortfolioEntryPlanningPackage portfolioEntryPlanningPackage : pagination.getListOfObjects()) {
            portfolioEntryPlanningPackageListView.add(new PortfolioEntryPlanningPackageListView(portfolioEntryPlanningPackage, this.getI18nMessagesPlugin(),
                    this.getPreferenceManagerPlugin()));
        }

        Set<String> hideColumnsForPackage = filterConfig.getColumnsToHide();
        if (!getSecurityService().dynamic("PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION", "")) {
            hideColumnsForPackage.add("editActionLink");
            hideColumnsForPackage.add("deleteActionLink");
        }

        Table<PortfolioEntryPlanningPackageListView> table = this.getTableProvider().get().portfolioEntryPlanningPackage.templateTable
                .fillForFilterConfig(portfolioEntryPlanningPackageListView, hideColumnsForPackage);

        return Pair.of(table, pagination);

    }

    /**
     * Display the details of a planning package.
     * 
     * @param id
     *            the portfolio entry id
     * @param planningPackageId
     *            the planning package id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result viewPackage(Long id, Long planningPackageId) {

        // get the portfolio entry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the planning package
        PortfolioEntryPlanningPackage planningPackage = PortfolioEntryPlanningPackageDao.getPEPlanningPackageById(planningPackageId);

        // construct the corresponding form data (for the custom attributes)
        PortfolioEntryPlanningPackageFormData portfolioEntryPlanningPackageFormData = new PortfolioEntryPlanningPackageFormData(planningPackage);

        // security: the portfolioEntry must be related to the object
        if (!planningPackage.portfolioEntry.id.equals(id)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        // get the allocated resources' days
        BigDecimal allocatedResourcesDays = PortfolioEntryResourcePlanDAO.getPEPlanAllocatedActorAsDaysByPlanningPackage(planningPackage)
                .add(PortfolioEntryResourcePlanDAO.getPEResourcePlanAllocatedOrgUnitAsDaysByPlanningPackage(planningPackage))
                .add(PortfolioEntryResourcePlanDAO.getPEResourcePlanAllocatedCompetencyAsDaysByPlanningPackage(planningPackage));

        // get the timesheets' days
        BigDecimal timesheetsDays = TimesheetDao.getTimesheetLogAsTotalHoursByPEPlanningPackage(planningPackage)
                .divide(TimesheetDao.getTimesheetReportHoursPerDay(this.getPreferenceManagerPlugin()), BigDecimal.ROUND_HALF_UP);

        /*
         * Get the attachments
         */

        // authorize the attachments
        FileAttachmentHelper.getFileAttachmentsForDisplay(PortfolioEntryPlanningPackage.class, planningPackageId, getAttachmentManagerPlugin(),
                getUserSessionManagerPlugin());

        // create the table
        List<Attachment> attachments = Attachment.getAttachmentsFromObjectTypeAndObjectId(PortfolioEntryPlanningPackage.class, planningPackageId);

        List<AttachmentListView> attachmentsListView = new ArrayList<AttachmentListView>();
        for (Attachment attachment : attachments) {
            attachmentsListView.add(new AttachmentListView(attachment,
                    controllers.core.routes.PortfolioEntryPlanningController.deletePackageAttachment(id, planningPackageId, attachment.id).url()));
        }

        Set<String> hideColumns = new HashSet<String>();
        if (!getSecurityService().dynamic("PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION", "")) {
            hideColumns.add("removeActionLink");
        }

        Table<AttachmentListView> attachmentsTable = this.getTableProvider().get().attachment.templateTable.fill(attachmentsListView, hideColumns);

        return ok(views.html.core.portfolioentryplanning.package_view.render(portfolioEntry, planningPackage, portfolioEntryPlanningPackageFormData,
                allocatedResourcesDays, timesheetsDays, attachmentsTable));
    }

    /**
     * Create or edit a planning package.
     * 
     * @param id
     *            the portfolio entry id
     * @param planningPackageId
     *            the planning package id (0 for create case)
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result managePackage(Long id, Long planningPackageId) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // initiate the form with the template
        Form<PortfolioEntryPlanningPackageFormData> planningPackageForm = planningPackageFormTemplate;

        // edit case: inject values
        if (!planningPackageId.equals(Long.valueOf(0))) {

            PortfolioEntryPlanningPackage planningPackage = PortfolioEntryPlanningPackageDao.getPEPlanningPackageById(planningPackageId);

            // security: the portfolioEntry must be related to the object
            if (!planningPackage.portfolioEntry.id.equals(id)) {
                return forbidden(views.html.error.access_forbidden.render(""));
            }

            planningPackageForm = planningPackageFormTemplate.fill(new PortfolioEntryPlanningPackageFormData(planningPackage));

            // add the custom attributes values
            this.getCustomAttributeManagerService().fillWithValues(planningPackageForm, PortfolioEntryPlanningPackage.class, planningPackageId);
        } else {
            planningPackageForm = planningPackageFormTemplate.fill(new PortfolioEntryPlanningPackageFormData());

            // add the custom attributes default values
            this.getCustomAttributeManagerService().fillWithValues(planningPackageForm, PortfolioEntryPlanningPackage.class, null);
        }

        DefaultSelectableValueHolderCollection<CssValueForValueHolder> selectablePortfolioEntryPlanningPackageTypes = PortfolioEntryPlanningPackageDao
                .getPEPlanningPackageTypeActiveAsCssVH();

        return ok(views.html.core.portfolioentryplanning.package_manage.render(portfolioEntry, selectablePortfolioEntryPlanningPackageTypes,
                planningPackageForm, PortfolioEntryPlanningPackageDao.getPEPlanningPackageGroupActiveAsVH(), getPackageStatusAsValueHolderCollection()));
    }

    /**
     * Save a planning package.
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processManagePackage() {

        // bind the form
        Form<PortfolioEntryPlanningPackageFormData> boundForm = planningPackageFormTemplate.bindFromRequest();

        // get the portfolioEntry
        Long id = Long.valueOf(boundForm.data().get("id"));
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        DefaultSelectableValueHolderCollection<CssValueForValueHolder> selectablePortfolioEntryPlanningPackageTypes = PortfolioEntryPlanningPackageDao
                .getPEPlanningPackageTypeActiveAsCssVH();

        if (boundForm.hasErrors() || this.getCustomAttributeManagerService().validateValues(boundForm, PortfolioEntryPlanningPackage.class)) {
            return ok(views.html.core.portfolioentryplanning.package_manage.render(portfolioEntry, selectablePortfolioEntryPlanningPackageTypes, boundForm,
                    PortfolioEntryPlanningPackageDao.getPEPlanningPackageGroupActiveAsVH(), getPackageStatusAsValueHolderCollection()));
        }

        PortfolioEntryPlanningPackageFormData planningPackageFormData = boundForm.get();

        PortfolioEntryPlanningPackage planningPackage = null;

        if (planningPackageFormData.planningPackageId == null) { // create case

            planningPackage = new PortfolioEntryPlanningPackage();

            planningPackage.portfolioEntry = portfolioEntry;

            planningPackageFormData.fill(planningPackage);

            planningPackage.save();

            // if exists, add the document
            if (FileAttachmentHelper.hasFileField("document")) {
                Logger.debug("has document");
                try {
                    FileAttachmentHelper.saveAsAttachement("document", PortfolioEntryPlanningPackage.class, planningPackage.id, getAttachmentManagerPlugin());
                } catch (IOException e) {
                    Logger.error("impossible to add the document for the created package '" + planningPackage.id + "'. The package still created.", e);
                }
            }

            Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.package.add.successful"));

        } else { // edit case

            planningPackage = PortfolioEntryPlanningPackageDao.getPEPlanningPackageById(planningPackageFormData.planningPackageId);

            Date oldStartDate = planningPackage.startDate;
            Date oldEndDate = planningPackage.endDate;

            // security: the portfolioEntry must be related to the object
            if (!planningPackage.portfolioEntry.id.equals(id)) {
                return forbidden(views.html.error.access_forbidden.render(""));
            }

            planningPackageFormData.fill(planningPackage);

            planningPackage.update();

            Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.package.edit.successful"));

            alertManagerDatesHaveChanged(oldStartDate, oldEndDate, planningPackage);
        }

        propagatePackageDates(planningPackage);

        // save the custom attributes
        this.getCustomAttributeManagerService().validateAndSaveValues(boundForm, PortfolioEntryPlanningPackage.class, planningPackage.id);

        return redirect(controllers.core.routes.PortfolioEntryPlanningController.packages(id));

    }

    /**
     * Display the form to add one or many package groups.
     * 
     * The available package groups are the ones related to the portfolio entry
     * type of the portfolio entry.
     * 
     * @param id
     *            the portfolio entry id
     * @return
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result addPackageGroups(Long id) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        List<PortfolioEntryPlanningPackageGroup> groups = new ArrayList<>();
        for (ISelectableValueHolder<Long> value : PortfolioEntryPlanningPackageDao.getPEPlanningPackageGroupActiveNonEmptyAsVH().getSortedValues()) {
            groups.add((PortfolioEntryPlanningPackageGroup) value);
        }

        return ok(views.html.core.portfolioentryplanning.package_add_groups.render(portfolioEntry, groups, planningPackageGroupsFormTemplate));
    }

    /**
     * Process the package groups to add, meaning create the planning package
     * according to the patterns.
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processAddPackageGroups() {

        // bind the form
        Form<PortfolioEntryPlanningPackageGroupsFormData> boundForm = planningPackageGroupsFormTemplate.bindFromRequest();

        // get the portfolioEntry
        Long id = Long.valueOf(boundForm.data().get("id"));
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the available groups
        List<PortfolioEntryPlanningPackageGroup> groups = PortfolioEntryPlanningPackageDao.getPEPlanningPackageGroupActiveNonEmptyAsList();

        if (boundForm.hasErrors()) {
            return ok(views.html.core.portfolioentryplanning.package_add_groups.render(portfolioEntry, groups, boundForm));
        }

        PortfolioEntryPlanningPackageGroupsFormData groupsFormData = boundForm.get();

        List<Long> groupsId = new ArrayList<Long>();
        for (Long groupId : groupsFormData.groups) {
            if (groupId != null) {
                groupsId.add(groupId);
            }
        }

        if (groupsId.size() == 0) {
            boundForm.reject("groups", Msg.get("core.portfolio_entry_planning.package.groups.selectagroup"));
            return ok(views.html.core.portfolioentryplanning.package_add_groups.render(portfolioEntry, groups, boundForm));
        }

        for (Long groupId : groupsId) {
            PortfolioEntryPlanningPackageGroup group = PortfolioEntryPlanningPackageDao.getPEPlanningPackageGroupById(groupId);
            for (PortfolioEntryPlanningPackagePattern pattern : group.portfolioEntryPlanningPackagePatterns) {
                PortfolioEntryPlanningPackage planningPackage = new PortfolioEntryPlanningPackage(portfolioEntry, pattern);
                planningPackage.save();
            }
        }

        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.package.groups.successful"));

        return redirect(controllers.core.routes.PortfolioEntryPlanningController.packages(portfolioEntry.id));
    }

    /**
     * Delete a planning package.
     * 
     * @param id
     *            the portfolio entry id
     * @param planningPackageId
     *            the planning package id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result deletePackage(Long id, Long planningPackageId) {

        // get the planning package
        PortfolioEntryPlanningPackage planningPackage = PortfolioEntryPlanningPackageDao.getPEPlanningPackageById(planningPackageId);

        // security: the portfolioEntry must be related to the object
        if (!planningPackage.portfolioEntry.id.equals(id)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        // set the delete flag to true
        planningPackage.doDelete();

        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.package.delete.successful"));

        return redirect(controllers.core.routes.PortfolioEntryPlanningController.packages(id));

    }

    /**
     * Form to manage all planning packages.
     * 
     * @param id
     *            the portfolio entry id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result manageAllPackages(Long id) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        return ok(views.html.core.portfolioentryplanning.packages_manage.render(portfolioEntry, getPortfolioEntryPlanningPackagesForm(id),
                PortfolioEntryPlanningPackageDao.getPEPlanningPackageTypeActiveAsVH(), getPackageStatusAsValueHolderCollection(),
                PortfolioEntryPlanningPackageDao.getPEPlanningPackageAsExprByPE(id).findRowCount()));

    }

    /**
     * Add a row (a planning package) in the manage all packages table.
     * 
     * This method simply returns the render for the row (no add in the DB is
     * done).
     * 
     * @param id
     *            the portfolio entry id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result addRowForManageAllPackages(Long id) {

        Form<PortfolioEntryPlanningPackagesFormData> planningPackagesFormData = planningPackagesFormDataTemplate
                .fill(new PortfolioEntryPlanningPackagesFormData());

        return ok(views.html.core.portfolioentryplanning.packages_manage_form_row_fragment.render(planningPackagesFormData,
                PortfolioEntryPlanningPackageDao.getPEPlanningPackageTypeActiveAsVH(), getPackageStatusAsValueHolderCollection()));
    }

    /**
     * Process the form to manage all planning packages.
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processManageAllPackages() {

        // bind the form
        Form<PortfolioEntryPlanningPackagesFormData> boundForm = planningPackagesFormDataTemplate.bindFromRequest();

        // get the portfolioEntry
        Long id = Long.valueOf(boundForm.data().get("id"));
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        this.getCustomAttributeManagerService().validateValues(boundForm, PortfolioEntryPlanningPackage.class, null, "planningPackagesFormData", true);
        if (boundForm.hasErrors()) {
            return badRequest(views.html.core.portfolioentryplanning.packages_manage_form_fragment.render(portfolioEntry, boundForm,
                    PortfolioEntryPlanningPackageDao.getPEPlanningPackageTypeActiveAsVH(), getPackageStatusAsValueHolderCollection()));
        }

        PortfolioEntryPlanningPackagesFormData portfolioEntryPlanningPackagesFormData = boundForm.get();

        List<Long> objectIds = new ArrayList<Long>();

        for (PortfolioEntryPlanningPackageFormData planningPackageFormData : portfolioEntryPlanningPackagesFormData.planningPackagesFormData) {

            PortfolioEntryPlanningPackage planningPackage = null;

            if (planningPackageFormData.planningPackageId == null) {

                planningPackage = new PortfolioEntryPlanningPackage();

                planningPackage.portfolioEntry = portfolioEntry;
                planningPackageFormData.fill(planningPackage);

                planningPackage.save();

            } else {

                planningPackage = PortfolioEntryPlanningPackageDao.getPEPlanningPackageById(planningPackageFormData.planningPackageId);

                Date oldStartDate = planningPackage.startDate;
                Date oldEndDate = planningPackage.endDate;

                // security: the portfolioEntry must be related to the object
                if (!planningPackage.portfolioEntry.id.equals(id)) {
                    return forbidden(views.html.error.access_forbidden.render(""));
                }

                planningPackageFormData.fill(planningPackage);
                planningPackage.update();

                alertManagerDatesHaveChanged(oldStartDate, oldEndDate, planningPackage);

            }

            propagatePackageDates(planningPackage);

            objectIds.add(planningPackage.id);

        }

        // save the custom attributes
        this.getCustomAttributeManagerService().validateAndSaveValues(boundForm, PortfolioEntryPlanningPackage.class, null, "planningPackagesFormData",
                objectIds, true);

        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.packages.manage.successful"));

        return redirect(controllers.core.routes.PortfolioEntryPlanningController.packages(portfolioEntry.id));
    }

    /**
     * When edit a package, if the dates (start and end) have changed and the
     * package contains confirmed allocated actors, then notify their manager.
     * 
     * @param oldStartDate
     *            the start date before change
     * @param oldEndDate
     *            the end date before change
     * @param planningPackage
     *            the changed planning package
     */
    private void alertManagerDatesHaveChanged(Date oldStartDate, Date oldEndDate, PortfolioEntryPlanningPackage planningPackage) {
        if ((oldStartDate == null && planningPackage.startDate != null) || (oldStartDate != null && !oldStartDate.equals(planningPackage.startDate))
                || (oldEndDate == null && planningPackage.endDate != null) || (oldEndDate != null && !oldEndDate.equals(planningPackage.endDate))) {

            planningPackage.portfolioEntryResourcePlanAllocatedActors
                    .stream()
                    .filter(allocatedActor ->
                            allocatedActor.portfolioEntryResourcePlanAllocationStatusType.status.equals(PortfolioEntryResourcePlanAllocationStatusType.AllocationStatus.CONFIRMED)
                                    && allocatedActor.followPackageDates != null && allocatedActor.followPackageDates)
                    .forEach(allocatedActor ->
                            ActorDao.sendNotification(
                                this.getNotificationManagerService(),
                                this.getI18nMessagesPlugin(),
                                allocatedActor.actor.manager,
                                NotificationCategory.getByCode(Code.PORTFOLIO_ENTRY),
                                routes.ActorController.allocation(allocatedActor.actor.id).url(),
                                "core.portfolio_entry_planning.allocated_actor.edit_confirmed.notification.title",
                                "core.portfolio_entry_planning.allocated_actor.edit_confirmed.notification.message",
                                allocatedActor.actor.getNameHumanReadable()
                            )
                    );
        }
    }

    /**
     * Propagate the planning package dates to the related objects that follow
     * the package dates.
     * 
     * @param planningPackage
     *            the planning package
     */
    private void propagatePackageDates(PortfolioEntryPlanningPackage planningPackage) {

        for (PortfolioEntryResourcePlanAllocatedActor a : planningPackage.portfolioEntryResourcePlanAllocatedActors) {
            if (a.followPackageDates != null && a.followPackageDates) {
                a.startDate = planningPackage.startDate;
                a.endDate = planningPackage.endDate;
                a.computeAllocationDetails(getBudgetTrackingService().isActive(), getPreferenceManagerPlugin().getPreferenceValueAsBoolean(IMafConstants.RESOURCES_WEEK_DAYS_ALLOCATION_PREFERENCE));
                a.save();
            }
        }
        for (PortfolioEntryResourcePlanAllocatedOrgUnit oo : planningPackage.portfolioEntryResourcePlanAllocatedOrgUnits) {
            if (oo.followPackageDates != null && oo.followPackageDates) {
                oo.startDate = planningPackage.startDate;
                oo.endDate = planningPackage.endDate;
                oo.computeAllocationDetails(getBudgetTrackingService().isActive(), getPreferenceManagerPlugin().getPreferenceValueAsBoolean(IMafConstants.RESOURCES_WEEK_DAYS_ALLOCATION_PREFERENCE));
                oo.save();
            }
        }
        for (PortfolioEntryResourcePlanAllocatedCompetency c : planningPackage.portfolioEntryResourcePlanAllocatedCompetencies) {
            if (c.followPackageDates != null && c.followPackageDates) {
                c.startDate = planningPackage.startDate;
                c.endDate = planningPackage.endDate;
                c.save();
            }
        }
        for (WorkOrder o : planningPackage.workOrders) {
            if (o.followPackageDates != null && o.followPackageDates) {
                o.startDate = planningPackage.startDate;
                o.dueDate = planningPackage.endDate;
                o.save();
            }
        }
    }

    /**
     * Get the filled planning packages form.
     * 
     * @param portfolioEntryId
     *            the portfolio entry id
     */
    private Form<PortfolioEntryPlanningPackagesFormData> getPortfolioEntryPlanningPackagesForm(Long portfolioEntryId) {

        // get the planning packages
        List<PortfolioEntryPlanningPackage> portfolioEntryPlanningPackages = PortfolioEntryPlanningPackageDao
                .getPEPlanningPackageOrderedAsListByPE(portfolioEntryId);
        List<Long> objectIds = new ArrayList<>();
        for (PortfolioEntryPlanningPackage portfolioEntryPlanningPackage : portfolioEntryPlanningPackages) {
            objectIds.add(portfolioEntryPlanningPackage.id);
        }

        // construct the form
        Form<PortfolioEntryPlanningPackagesFormData> portfolioEntryPlanningPackagesForm = planningPackagesFormDataTemplate
                .fill(new PortfolioEntryPlanningPackagesFormData(portfolioEntryPlanningPackages, portfolioEntryId));

        this.getCustomAttributeManagerService().fillWithValues(portfolioEntryPlanningPackagesForm, PortfolioEntryPlanningPackage.class, null,
                "planningPackagesFormData", objectIds);

        return portfolioEntryPlanningPackagesForm;

    }

    /**
     * Form to add an attachment to a planning package.
     * 
     * @param id
     *            the portfolio entry id
     * @param planningPackageId
     *            the planning package id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result createPackageAttachment(Long id, Long planningPackageId) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the package
        PortfolioEntryPlanningPackage planningPackage = PortfolioEntryPlanningPackageDao.getPEPlanningPackageById(planningPackageId);

        // construct the form
        Form<AttachmentFormData> attachmentForm = attachmentFormTemplate.fill(new AttachmentFormData());

        return ok(views.html.core.portfolioentryplanning.package_attachment_create.render(portfolioEntry, planningPackage, attachmentForm));

    }

    /**
     * Process the form to add an attachment to a planning package.
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processCreatePackageAttachment() {

        Form<AttachmentFormData> boundForm = attachmentFormTemplate.bindFromRequest();

        // get the portfolioEntry
        Long id = Long.valueOf(boundForm.data().get("id"));
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the package
        Long planningPackageId = Long.valueOf(boundForm.data().get("objectId"));
        PortfolioEntryPlanningPackage planningPackage = PortfolioEntryPlanningPackageDao.getPEPlanningPackageById(planningPackageId);

        if (boundForm.hasErrors()) {
            return ok(views.html.core.portfolioentryplanning.package_attachment_create.render(portfolioEntry, planningPackage, boundForm));
        }

        // store the document
        try {
            FileAttachmentHelper.saveAsAttachement("document", PortfolioEntryPlanningPackage.class, planningPackage.id, getAttachmentManagerPlugin());
        } catch (Exception e) {
            return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getMessagesPlugin());
        }

        // success message
        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry.attachment.new.successful"));

        return redirect(controllers.core.routes.PortfolioEntryPlanningController.viewPackage(portfolioEntry.id, planningPackage.id));
    }

    /**
     * Delete an attachment of a planning package.
     * 
     * @param id
     *            the portfolio entry id
     * @param planningPackageId
     *            the planning package id
     * @param attachmentId
     *            the attachment id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result deletePackageAttachment(Long id, Long planningPackageId, Long attachmentId) {

        // get the package
        PortfolioEntryPlanningPackage planningPackage = PortfolioEntryPlanningPackageDao.getPEPlanningPackageById(planningPackageId);

        // get the attachment
        Attachment attachment = Attachment.getAttachmentFromId(attachmentId);

        // security: the planning package must be related to the attachment, and
        // the portfolio entry to the planning package
        if (!attachment.objectId.equals(planningPackageId) || !planningPackage.portfolioEntry.id.equals(id)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        // delete the attachment
        FileAttachmentHelper.deleteFileAttachment(attachmentId, getAttachmentManagerPlugin(), getUserSessionManagerPlugin());

        attachment.doDelete();

        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry.attachment.delete"));

        return redirect(controllers.core.routes.PortfolioEntryPlanningController.viewPackage(id, planningPackageId));
    }

    /**
     * Display the resources of a portfolio entry.
     * 
     * @param id
     *            the portfolio entry id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result resources(Long id) throws AccountManagementException {

        // Get user session id
        String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // Get resource plan
        PortfolioEntryResourcePlan resourcePlan = getResourcePlan(portfolioEntry);

        // construct the allocated actors table
        FilterConfig<PortfolioEntryResourcePlanAllocatedActorListView> allocatedActorFilterConfig =
                getTableProvider().get().portfolioEntryResourcePlanAllocatedActor.filterConfig.getCurrent(uid, request());

        Pair<Table<PortfolioEntryResourcePlanAllocatedActorListView>, Pagination<PortfolioEntryResourcePlanAllocatedActor>> allocatedActorTable =
                getAllocatedActorTable(resourcePlan, allocatedActorFilterConfig);

        // construct the org unit allocation table
        FilterConfig<PortfolioEntryResourcePlanAllocatedOrgUnitListView> allocatedOrgUnitFilterConfig =
                getTableProvider().get().portfolioEntryResourcePlanAllocatedOrgUnit.filterConfig.getCurrent(uid, request());

        Pair<Table<PortfolioEntryResourcePlanAllocatedOrgUnitListView>, Pagination<PortfolioEntryResourcePlanAllocatedOrgUnit>> allocatedOrgUnitTable =
                getAllocatedOrgUnitTable(resourcePlan, allocatedOrgUnitFilterConfig);

        // construct the competency allocation table
        FilterConfig<PortfolioEntryResourcePlanAllocatedCompetencyListView> allocatedCompetencyFilterConfig =
                getTableProvider().get().portfolioEntryResourcePlanAllocatedCompetency.filterConfig.getCurrent(uid, request());

        Pair<Table<PortfolioEntryResourcePlanAllocatedCompetencyListView>, Pagination<PortfolioEntryResourcePlanAllocatedCompetency>> allocatedCompetencyTable =
                getAllocatedCompetencyTable(resourcePlan, allocatedCompetencyFilterConfig);

        return ok(views.html.core.portfolioentryplanning.resources.render(
                portfolioEntry,
                allocatedActorTable.getLeft(),
                allocatedActorTable.getRight(),
                allocatedActorFilterConfig,
                allocatedOrgUnitTable.getLeft(),
                allocatedOrgUnitTable.getRight(),
                allocatedOrgUnitFilterConfig,
                allocatedCompetencyTable.getLeft(),
                allocatedCompetencyTable.getRight(),
                allocatedCompetencyFilterConfig
        ));
    }

    /**
     * Confirm the selected actors portfolio entry allocations.
     */
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result confirmCompetenciesAllocations() {

        List<String> ids = FilterConfig.getIdsFromRequest(request());

        updateCompentencyAllocationsStatus(ids, PortfolioEntryResourcePlanAllocationStatusType.AllocationStatus.CONFIRMED);

        return ok("<div class=\"alert alert-success\">" + Msg.get("core.org_unit.allocation.details.actors.portfolio_entry.action.confirm.successful")
                + "</div>");

    }

    /**
     * Cancel the selected actors portfolio entry allocations.
     */
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result cancelCompetenciesAllocations() {

        List<String> ids = FilterConfig.getIdsFromRequest(request());

        updateCompentencyAllocationsStatus(ids, PortfolioEntryResourcePlanAllocationStatusType.AllocationStatus.DRAFT);

        return ok("<div class=\"alert alert-success\">" + Msg.get("core.org_unit.allocation.details.actors.portfolio_entry.action.cancel.successful")
                + "</div>");

    }

    private void updateCompentencyAllocationsStatus(List<String> ids, PortfolioEntryResourcePlanAllocationStatusType.AllocationStatus status) {
        if (ids != null) {
            ids.forEach(idAsString -> {
                Long id = Long.parseLong(idAsString);
                PortfolioEntryResourcePlanAllocatedCompetency allocatedCompentency = PortfolioEntryResourcePlanDAO.getPEResourcePlanAllocatedCompetencyById(id);
                allocatedCompentency.portfolioEntryResourcePlanAllocationStatusType = PortfolioEntryResourcePlanDAO.getAllocationStatusByType(status);
                allocatedCompentency.lastStatusTypeUpdateTime = new Date();
                allocatedCompentency.lastStatusTypeUpdateActor = ActorDao.getActorByUid(getUserSessionManagerPlugin().getUserSessionId(ctx()));
                allocatedCompentency.update();
            });
        }
    }

    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result resourcesAllocatedActorFilter(Long id) throws AccountManagementException {

        String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());
        FilterConfig<PortfolioEntryResourcePlanAllocatedActorListView> filterConfig = this.getTableProvider().get().portfolioEntryResourcePlanAllocatedActor.filterConfig.persistCurrentInDefault(uid, request());

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // Get resource plan
        PortfolioEntryResourcePlan resourcePlan = getResourcePlan(portfolioEntry);

        if (filterConfig == null) {
            return ok(views.html.framework_views.parts.table.dynamic_tableview_no_more_compatible.render());
        } else {
            Pair<Table<PortfolioEntryResourcePlanAllocatedActorListView>, Pagination<PortfolioEntryResourcePlanAllocatedActor>> t = getAllocatedActorTable(resourcePlan, filterConfig);
            return ok(views.html.framework_views.parts.table.dynamic_tableview.render(t.getLeft(), t.getRight()));
        }
    }

    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result resourcesAllocatedOrgUnitFilter(Long id) {

        String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());
        FilterConfig<PortfolioEntryResourcePlanAllocatedOrgUnitListView> filterConfig = getTableProvider().get().portfolioEntryResourcePlanAllocatedOrgUnit.filterConfig.persistCurrentInDefault(uid, request());

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // Get resource plan
        PortfolioEntryResourcePlan resourcePlan = getResourcePlan(portfolioEntry);

        if (filterConfig == null) {
            return ok(views.html.framework_views.parts.table.dynamic_tableview_no_more_compatible.render());
        } else {
            Pair<Table<PortfolioEntryResourcePlanAllocatedOrgUnitListView>, Pagination<PortfolioEntryResourcePlanAllocatedOrgUnit>> table = getAllocatedOrgUnitTable(resourcePlan, filterConfig);
            return ok(views.html.framework_views.parts.table.dynamic_tableview.render(table.getLeft(), table.getRight()));
        }
    }

    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result resourcesAllocatedCompetencyFilter(Long id) {

        String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());
        FilterConfig<PortfolioEntryResourcePlanAllocatedCompetencyListView> filterConfig = getTableProvider().get().portfolioEntryResourcePlanAllocatedCompetency.filterConfig.persistCurrentInDefault(uid, request());

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // Get resource plan
        PortfolioEntryResourcePlan resourcePlan = getResourcePlan(portfolioEntry);

        if (filterConfig == null) {
            return ok(views.html.framework_views.parts.table.dynamic_tableview_no_more_compatible.render());
        } else {
            Pair<Table<PortfolioEntryResourcePlanAllocatedCompetencyListView>, Pagination<PortfolioEntryResourcePlanAllocatedCompetency>> table = getAllocatedCompetencyTable(resourcePlan, filterConfig);
            return ok(views.html.framework_views.parts.table.dynamic_tableview.render(table.getLeft(), table.getRight()));
        }
    }

    /**
     * Get Allocated actors table
     *
     * @param resourcePlan the resource plan associated to the allocations
     * @param filterConfig the table filter config
     *
     * @return a Pair of table and pagination
     */
    private Pair<Table<PortfolioEntryResourcePlanAllocatedActorListView>, Pagination<PortfolioEntryResourcePlanAllocatedActor>> getAllocatedActorTable(
            PortfolioEntryResourcePlan resourcePlan,
            FilterConfig<PortfolioEntryResourcePlanAllocatedActorListView> filterConfig
    ) throws AccountManagementException {

        ExpressionList<PortfolioEntryResourcePlanAllocatedActor> expressionList = PortfolioEntryResourcePlanDAO.findPEResourcePlanAllocatedActor
                .where()
                .eq("deleted", false)
                .eq("portfolioEntryResourcePlan.id", resourcePlan.id);

        expressionList = filterConfig.updateWithSearchExpression(expressionList);
        filterConfig.updateWithSortExpression(expressionList);

        Pagination<PortfolioEntryResourcePlanAllocatedActor> pagination = new Pagination<>(getPreferenceManagerPlugin(), expressionList);
        pagination.setCurrentPage(filterConfig.getCurrentPage());
        pagination.setPageQueryName("allocatedActorsPage");

        List<PortfolioEntryResourcePlanAllocatedActor> allocatedActorList = pagination.getListOfObjects();

        List<PortfolioEntryResourcePlanAllocatedActorListView> portfolioEntryResourcePlanAllocatedActorListView = new ArrayList<>();
        portfolioEntryResourcePlanAllocatedActorListView.addAll(allocatedActorList.stream().map(PortfolioEntryResourcePlanAllocatedActorListView::new).collect(Collectors.toList()));

        Set<String> notDisplayedColumns = filterConfig.getColumnsToHide();
        notDisplayedColumns.add("portfolioEntryName");
        if (!getSecurityService().dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION, "")) {
            notDisplayedColumns.add("editActionLink");
            notDisplayedColumns.add("removeActionLink");
        }

        Table<PortfolioEntryResourcePlanAllocatedActorListView> allocatedActorsTable = this.getTableProvider()
                .get().portfolioEntryResourcePlanAllocatedActor.templateTable.fillForFilterConfig(portfolioEntryResourcePlanAllocatedActorListView,
                        notDisplayedColumns);

        if (securityService.dynamic(IMafConstants.PORTFOLIO_ENTRY_CONFIRM_ALLOCATIONS_DYNAMIC_PERMISSION, "")) {
            allocatedActorsTable.addAjaxRowAction(Msg.get("core.org_unit.allocation.details.actors.portfolio_entry.action.confirm"),
                    controllers.core.routes.OrgUnitController.confirmActorsPortfolioEntryAllocations().url(), "actor-result");
        }

        if (securityService.dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION, "")) {

            allocatedActorsTable.addAjaxRowAction(Msg.get("core.org_unit.allocation.details.actors.portfolio_entry.action.submit"),
                    controllers.core.routes.OrgUnitController.submitActorsPortfolioEntryAllocations().url(), "actor-result");

            allocatedActorsTable.addAjaxRowAction(Msg.get("button.cancel"),
                    controllers.core.routes.OrgUnitController.cancelActorsPortfolioEntryAllocations().url(), "actor-result");

            allocatedActorsTable.setAllIdsUrl(routes.PortfolioEntryPlanningController.getPortfolioEntryActorAllocationIds(resourcePlan.id).url());
        }

        return Pair.of(allocatedActorsTable, pagination);
    }

    /**
     * Get all delivery unit allocation ids according to the current
     * filter configuration.
     *
     * @param id the resource plan id
     */
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result getPortfolioEntryActorAllocationIds(Long resourcePlanId) {
        String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());

        FilterConfig<PortfolioEntryResourcePlanAllocatedActorListView> filterConfig = this.getTableProvider().get().portfolioEntryResourcePlanAllocatedActor.filterConfig.persistCurrentInDefault(uid, request());
        ExpressionList<PortfolioEntryResourcePlanAllocatedActor> expressionList = filterConfig.updateWithSearchExpression(PortfolioEntryResourcePlanDAO.findPEResourcePlanAllocatedActor
                .where()
                .eq("deleted", false)
                .eq("portfolioEntryResourcePlan.id", resourcePlanId)
        );

        List<String> ids = expressionList.findList().stream().map(list -> String.valueOf(list.id)).collect(Collectors.toList());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.valueToTree(ids);

        return ok(jsonNode);
    }

    /**
     * Get allocated org unit table
     *
     * @param resourcePlan the resource plan linked to the allocations
     * @param filterConfig the filter configuration for the table
     *
     * @return a pair of the table and the pagination
     */
    private Pair<Table<PortfolioEntryResourcePlanAllocatedOrgUnitListView>, Pagination<PortfolioEntryResourcePlanAllocatedOrgUnit>> getAllocatedOrgUnitTable(PortfolioEntryResourcePlan resourcePlan, FilterConfig<PortfolioEntryResourcePlanAllocatedOrgUnitListView> filterConfig) {
        ExpressionList<PortfolioEntryResourcePlanAllocatedOrgUnit> expressionList = PortfolioEntryResourcePlanDAO.findPEResourcePlanAllocatedOrgUnit
                .fetch("orgUnit")
                .where()
                .eq("deleted", false)
                .eq("portfolioEntryResourcePlan.id", resourcePlan.id);

        expressionList = filterConfig.updateWithSearchExpression(expressionList);
        filterConfig.updateWithSortExpression(expressionList);

        Pagination<PortfolioEntryResourcePlanAllocatedOrgUnit> pagination = new Pagination<>(getPreferenceManagerPlugin(), expressionList);
        pagination.setCurrentPage(filterConfig.getCurrentPage());
        pagination.setPageQueryName("allocatedOrgUnitPage");

        List<PortfolioEntryResourcePlanAllocatedOrgUnit> allocatedOrgUnitList = pagination.getListOfObjects();

        List<PortfolioEntryResourcePlanAllocatedOrgUnitListView> listView = new ArrayList<>();
        listView.addAll(allocatedOrgUnitList.stream().map(PortfolioEntryResourcePlanAllocatedOrgUnitListView::new).collect(Collectors.toList()));

        Set<String> notDisplayedColumns = filterConfig.getColumnsToHide();

        Table<PortfolioEntryResourcePlanAllocatedOrgUnitListView> allocatedOrgUnitTable =
                getTableProvider().get().portfolioEntryResourcePlanAllocatedOrgUnit.templateTable.fillForFilterConfig(listView, notDisplayedColumns);

        if (securityService.dynamic(IMafConstants.PORTFOLIO_ENTRY_CONFIRM_ALLOCATIONS_DYNAMIC_PERMISSION, "")) {
            allocatedOrgUnitTable.addAjaxRowAction(Msg.get("core.org_unit.allocation.details.actors.portfolio_entry.action.confirm"),
                    controllers.core.routes.OrgUnitController.confirmDeliveryUnitsAllocations().url(), "orgunit-result");
        }

        if (securityService.dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION, "")) {

            allocatedOrgUnitTable.addAjaxRowAction(Msg.get("core.org_unit.allocation.details.actors.portfolio_entry.action.submit"),
                    controllers.core.routes.OrgUnitController.submitDeliveryUnitsAllocations().url(), "orgunit-result");

            allocatedOrgUnitTable.addAjaxRowAction(Msg.get("button.cancel"),
                    controllers.core.routes.OrgUnitController.cancelDeliveryUnitsAllocations().url(), "orgunit-result");

            allocatedOrgUnitTable.setAllIdsUrl(routes.PortfolioEntryPlanningController.getDeliveryUnitAllocationIds(resourcePlan.id).url());
        } else {
            notDisplayedColumns.add("editActionLink");
            notDisplayedColumns.add("removeActionLink");
            notDisplayedColumns.add("reallocate");
        }

        if (!getBudgetTrackingService().isActive()) {
            notDisplayedColumns.add("currency");
            notDisplayedColumns.add("dailyRate");
            notDisplayedColumns.add("forecastDays");
            notDisplayedColumns.add("forecastDailyRate");
        }

        return Pair.of(allocatedOrgUnitTable, pagination);
    }

    /**
     * Get all delivery unit allocation ids according to the current
     * filter configuration.
     *
     * @param id the resource plan id
     */
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result getDeliveryUnitAllocationIds(Long resourcePlanId) {
        String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());

        FilterConfig<PortfolioEntryResourcePlanAllocatedOrgUnitListView> filterConfig = this.getTableProvider().get().portfolioEntryResourcePlanAllocatedOrgUnit.filterConfig.persistCurrentInDefault(uid, request());
        ExpressionList<PortfolioEntryResourcePlanAllocatedOrgUnit> expressionList = filterConfig.updateWithSearchExpression(PortfolioEntryResourcePlanDAO.findPEResourcePlanAllocatedOrgUnit
                .fetch("orgUnit")
                .where()
                .eq("deleted", false)
                .eq("portfolioEntryResourcePlan.id", resourcePlanId)
        );

        List<String> ids = expressionList.findList().stream().map(list -> String.valueOf(list.id)).collect(Collectors.toList());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.valueToTree(ids);

        return ok(jsonNode);
    }

    /**
     * Get allocated org unit table
     *
     * @param resourcePlan the resource plan linked to the allocations
     * @param filterConfig the filter configuration for the table
     *
     * @return a pair of the table and the pagination
     */
    private Pair<Table<PortfolioEntryResourcePlanAllocatedCompetencyListView>, Pagination<PortfolioEntryResourcePlanAllocatedCompetency>> getAllocatedCompetencyTable(PortfolioEntryResourcePlan resourcePlan, FilterConfig<PortfolioEntryResourcePlanAllocatedCompetencyListView> filterConfig) {
        ExpressionList<PortfolioEntryResourcePlanAllocatedCompetency> expressionList = PortfolioEntryResourcePlanDAO.findPEResourcePlanAllocatedCompetency
                .where()
                .eq("deleted", false)
                .eq("portfolioEntryResourcePlan.id", resourcePlan.id);

        expressionList = filterConfig.updateWithSearchExpression(expressionList);
        filterConfig.updateWithSortExpression(expressionList);

        Pagination<PortfolioEntryResourcePlanAllocatedCompetency> pagination = new Pagination<>(getPreferenceManagerPlugin(), expressionList);
        pagination.setCurrentPage(filterConfig.getCurrentPage());
        pagination.setPageQueryName("allocatedCompetencyPage");

        List<PortfolioEntryResourcePlanAllocatedCompetency> allocatedCompetencyList = pagination.getListOfObjects();

        List<PortfolioEntryResourcePlanAllocatedCompetencyListView> listView = new ArrayList<>();
        listView.addAll(allocatedCompetencyList.stream().map(PortfolioEntryResourcePlanAllocatedCompetencyListView::new).collect(Collectors.toList()));

        Set<String> notDisplayedColumns = filterConfig.getColumnsToHide();
        notDisplayedColumns.add("portfolioEntryName");

        Table<PortfolioEntryResourcePlanAllocatedCompetencyListView> allocatedCompetencyTable =
                getTableProvider().get().portfolioEntryResourcePlanAllocatedCompetency.templateTable.fillForFilterConfig(listView, notDisplayedColumns);

        if (securityService.dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION, "")) {

            allocatedCompetencyTable.addAjaxRowAction(Msg.get("core.org_unit.allocation.details.actors.portfolio_entry.action.confirm"),
                    routes.PortfolioEntryPlanningController.confirmCompetenciesAllocations().url(), "competency-result");

            allocatedCompetencyTable.addAjaxRowAction(Msg.get("button.cancel"),
                    controllers.core.routes.PortfolioEntryPlanningController.cancelCompetenciesAllocations().url(), "competency-result");

            allocatedCompetencyTable.setAllIdsUrl(routes.PortfolioEntryPlanningController.getCompetencyAllocationIds(resourcePlan.id).url());
        }

        return Pair.of(allocatedCompetencyTable, pagination);
    }

    /**
     * Get all compentency allocation ids according to the current
     * filter configuration.
     *
     * @param id the resource plan id
     */
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result getCompetencyAllocationIds(Long resourcePlanId) {
        String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());

        FilterConfig<PortfolioEntryResourcePlanAllocatedCompetencyListView> filterConfig = this.getTableProvider().get().portfolioEntryResourcePlanAllocatedCompetency.filterConfig.persistCurrentInDefault(uid, request());
        ExpressionList<PortfolioEntryResourcePlanAllocatedCompetency> expressionList = filterConfig.updateWithSearchExpression(PortfolioEntryResourcePlanDAO.findPEResourcePlanAllocatedCompetency
                .where()
                .eq("deleted", false)
                .eq("portfolioEntryResourcePlan.id", resourcePlanId)
        );

        List<String> ids = expressionList.findList().stream().map(list -> String.valueOf(list.id)).collect(Collectors.toList());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.valueToTree(ids);

        return ok(jsonNode);
    }

    /**
     * Get or create if not exist the resource plan associated to an initiative
     *
     * @param portfolioEntry the initiative
     *
     * @return the PortfolioEntryResourcePlan
     */
    private PortfolioEntryResourcePlan getResourcePlan(PortfolioEntry portfolioEntry) {
        // get current planning
        LifeCycleInstancePlanning planning = portfolioEntry.activeLifeCycleInstance.getCurrentLifeCycleInstancePlanning();

        /*
         * create the resource plan allocated actors/org unit tables
         */

        // get or create the resource plan
        PortfolioEntryResourcePlan resourcePlan = planning.portfolioEntryResourcePlan;
        if (resourcePlan == null) {
            Logger.debug("a new resource plan has been automatically created for the portfolio entry " + portfolioEntry.getName());
            resourcePlan = new PortfolioEntryResourcePlan();
            planning.portfolioEntryResourcePlan = resourcePlan;
            planning.save();
        }

        return resourcePlan;
    }

    /**
     * allocated actors
     */

    /**
     * Create or edit an allocated actor (for a resource plan).
     * 
     * @param id
     *            the portfolio entry id
     * @param allocatedActorId
     *            the allocated actor id, set to 0 for create case
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result manageAllocatedActor(Long id, Long allocatedActorId) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // initiate the form with the template
        Form<PortfolioEntryResourcePlanAllocatedActorFormData> allocatedActorForm;

        // edit case: inject values
        if (!allocatedActorId.equals(0L)) {

            PortfolioEntryResourcePlanAllocatedActor allocatedActor = PortfolioEntryResourcePlanDAO.getPEPlanAllocatedActorById(allocatedActorId);

            // security: the portfolioEntry must be related to the object
            if (!allocatedActor.portfolioEntryResourcePlan.lifeCycleInstancePlannings.get(0).lifeCycleInstance.portfolioEntry.id.equals(id)) {
                return forbidden(views.html.error.access_forbidden.render(""));
            }

            allocatedActorForm = allocatedActorFormTemplate.fill(new PortfolioEntryResourcePlanAllocatedActorFormData(allocatedActor));

            // add the custom attributes values
            this.getCustomAttributeManagerService().fillWithValues(allocatedActorForm, PortfolioEntryResourcePlanAllocatedActor.class, allocatedActorId);
        } else {
            allocatedActorForm = allocatedActorFormTemplate.fill(new PortfolioEntryResourcePlanAllocatedActorFormData());

            // add the custom attributes default values
            this.getCustomAttributeManagerService().fillWithValues(allocatedActorForm, PortfolioEntryResourcePlanAllocatedActor.class, null);
        }

        return ok(views.html.core.portfolioentryplanning.allocated_actor_manage.render(portfolioEntry, allocatedActorForm));

    }

    /**
     * Update the number of days for an allocated actor
     */
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result updateAllocatedActorDetail() {

        JsonNode json = request().body().asJson();
        Long allocatedActorId = json.findPath("allocatedActorId").asLong();
        Double days = json.findPath("days").asDouble();
        Integer month = json.findPath("month").asInt();
        Integer year = json.findPath("year").asInt();

        PortfolioEntryResourcePlanAllocatedActor allocatedActor = PortfolioEntryResourcePlanDAO.getPEPlanAllocatedActorById(allocatedActorId);
        allocatedActor.createOrUpdateAllocationDetail(year, month, days);
        if (getBudgetTrackingService().isActive()) {
            allocatedActor.forecastDays = allocatedActor.getDetailsAsTotalDays();
        } else {
            allocatedActor.days = allocatedActor.getDetailsAsTotalDays();
        }
        allocatedActor.save();

        return status(Http.Status.NO_CONTENT);
    }

    /**
     * Save an allocated actor.
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processManageAllocatedActor() {

        // bind the form
        Form<PortfolioEntryResourcePlanAllocatedActorFormData> boundForm = allocatedActorFormTemplate.bindFromRequest();

        // get the portfolioEntry
        Long id = Long.valueOf(boundForm.data().get("id"));
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        if (boundForm.hasErrors() || this.getCustomAttributeManagerService().validateValues(boundForm, PortfolioEntryResourcePlanAllocatedActor.class)) {
            return ok(views.html.core.portfolioentryplanning.allocated_actor_manage.render(portfolioEntry, boundForm));
        }

        PortfolioEntryResourcePlanAllocatedActorFormData portfolioEntryResourcePlanAllocatedActorFormData = boundForm.get();

        PortfolioEntryResourcePlanAllocatedActor allocatedActor;

        Ebean.beginTransaction();
        try {

            if (portfolioEntryResourcePlanAllocatedActorFormData.allocationId == null) { // create

                // get current planning
                LifeCycleInstancePlanning planning = portfolioEntry.activeLifeCycleInstance.getCurrentLifeCycleInstancePlanning();

                // get the resource plan
                PortfolioEntryResourcePlan resourcePlan = planning.portfolioEntryResourcePlan;

                allocatedActor = new PortfolioEntryResourcePlanAllocatedActor();
                allocatedActor.portfolioEntryResourcePlan = resourcePlan;

                portfolioEntryResourcePlanAllocatedActorFormData.fill(allocatedActor);
                allocatedActor.lastStatusTypeUpdateActor = ActorDao.getActorByUid(getUserSessionManagerPlugin().getUserSessionId(ctx()));
                allocatedActor.lastStatusTypeUpdateTime = new Date();

                allocatedActor.save();

                // create the stakeholder if not already
                if (StakeholderDao.getStakeholderByActorAndTypeAndPE(allocatedActor.actor.id,
                        portfolioEntryResourcePlanAllocatedActorFormData.stakeholderType, portfolioEntry.id) == null) {
                    Stakeholder stakeholder = new Stakeholder();
                    stakeholder.actor = allocatedActor.actor;
                    stakeholder.portfolioEntry = portfolioEntry;
                    stakeholder.stakeholderType = StakeholderDao.getStakeholderTypeById(portfolioEntryResourcePlanAllocatedActorFormData.stakeholderType);
                    stakeholder.save();
                }

                Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.allocated_actor.add.successful"));

            } else { // edit

                allocatedActor = PortfolioEntryResourcePlanDAO.getPEPlanAllocatedActorById(portfolioEntryResourcePlanAllocatedActorFormData.allocationId);

                PortfolioEntryResourcePlanAllocationStatusType portfolioEntryResourcePlanAllocationStatusType = allocatedActor.portfolioEntryResourcePlanAllocationStatusType;

                // security: the portfolioEntry must be related to the object
                if (!allocatedActor.portfolioEntryResourcePlan.lifeCycleInstancePlannings.get(0).lifeCycleInstance.portfolioEntry.id.equals(id)) {
                    return forbidden(views.html.error.access_forbidden.render(""));
                }

                portfolioEntryResourcePlanAllocatedActorFormData.fill(allocatedActor);
                allocatedActor.lastStatusTypeUpdateActor = ActorDao.getActorByUid(getUserSessionManagerPlugin().getUserSessionId(ctx()));
                allocatedActor.lastStatusTypeUpdateTime = new Date();
                allocatedActor.update();

                Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.allocated_actor.edit.successful"));

                // if the allocation was previously confirmed, then we send a
                // notification to the manager of the concerned actor
                if (portfolioEntryResourcePlanAllocationStatusType.status.equals(PortfolioEntryResourcePlanAllocationStatusType.AllocationStatus.CONFIRMED)) {
                    ActorDao.sendNotification(this.getNotificationManagerService(), this.getI18nMessagesPlugin(), allocatedActor.actor.manager,
                            NotificationCategory.getByCode(Code.PORTFOLIO_ENTRY),
                            controllers.core.routes.ActorController.allocation(allocatedActor.actor.id).url(),
                            "core.portfolio_entry_planning.allocated_actor.edit_confirmed.notification.title",
                            "core.portfolio_entry_planning.allocated_actor.edit_confirmed.notification.message", allocatedActor.actor.getNameHumanReadable());
                }

                // create or update the stakeholder if not already
                Stakeholder stakeholder = StakeholderDao.getStakeholderByActorAndTypeAndPE(allocatedActor.actor.id,
                        portfolioEntryResourcePlanAllocatedActorFormData.stakeholderType, portfolioEntry.id);
                if (stakeholder == null) {
                    stakeholder = new Stakeholder();
                    stakeholder.actor = allocatedActor.actor;
                    stakeholder.portfolioEntry = portfolioEntry;
                    stakeholder.stakeholderType = StakeholderDao.getStakeholderTypeById(portfolioEntryResourcePlanAllocatedActorFormData.stakeholderType);
                    stakeholder.save();
                }

            }

            // save the custom attributes
            this.getCustomAttributeManagerService().validateAndSaveValues(boundForm, PortfolioEntryResourcePlanAllocatedActor.class, allocatedActor.id);

            Ebean.commitTransaction();
            Ebean.endTransaction();

        } catch (Exception e) {

            Ebean.rollbackTransaction();
            Ebean.endTransaction();
            return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());

        }

        return redirect(controllers.core.routes.PortfolioEntryPlanningController.resources(id));

    }

    /**
     * Delete an allocated actor.
     * 
     * @param id
     *            the portfolio entry id
     * @param allocatedActorId
     *            the allocated actor id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result deleteAllocatedActor(Long id, Long allocatedActorId) {

        // get the allocated actor
        PortfolioEntryResourcePlanAllocatedActor allocatedActor = PortfolioEntryResourcePlanDAO.getPEPlanAllocatedActorById(allocatedActorId);

        // security: the portfolioEntry must be related to the object
        if (!allocatedActor.portfolioEntryResourcePlan.lifeCycleInstancePlannings.get(0).lifeCycleInstance.portfolioEntry.id.equals(id)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        // set the delete flag to true
        allocatedActor.doDelete();

        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.allocated_actor.delete.successful"));

        return redirect(controllers.core.routes.PortfolioEntryPlanningController.resources(id));
    }

    /**
     * allocated delivery units
     */

    /**
     * Create or edit an allocated delivery unit (for a resource plan).
     * 
     * @param id
     *            the porfolio entry id
     * @param allocatedOrgUnitId
     *            the allocated delivery unit id, set to 0 for create case
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result manageAllocatedOrgUnit(Long id, Long allocatedOrgUnitId) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // initiate the form with the template
        Form<PortfolioEntryResourcePlanAllocatedOrgUnitFormData> allocatedOrgUnitForm = allocatedOrgUnitFormTemplate;

        // edit case: inject values
        if (!allocatedOrgUnitId.equals(Long.valueOf(0))) {

            PortfolioEntryResourcePlanAllocatedOrgUnit allocatedOrgUnit = PortfolioEntryResourcePlanDAO
                    .getPEResourcePlanAllocatedOrgUnitById(allocatedOrgUnitId);

            // security: the portfolioEntry must be related to the object
            if (!allocatedOrgUnit.portfolioEntryResourcePlan.lifeCycleInstancePlannings.get(0).lifeCycleInstance.portfolioEntry.id.equals(id)) {
                return forbidden(views.html.error.access_forbidden.render(""));
            }

            allocatedOrgUnitForm = allocatedOrgUnitFormTemplate.fill(new PortfolioEntryResourcePlanAllocatedOrgUnitFormData(allocatedOrgUnit));

            // add the custom attributes values
            this.getCustomAttributeManagerService().fillWithValues(allocatedOrgUnitForm, PortfolioEntryResourcePlanAllocatedOrgUnit.class,
                    allocatedOrgUnitId);
        } else {
            allocatedOrgUnitForm = allocatedOrgUnitFormTemplate.fill(new PortfolioEntryResourcePlanAllocatedOrgUnitFormData());
            // add the custom attributes default values
            this.getCustomAttributeManagerService().fillWithValues(allocatedOrgUnitForm, PortfolioEntryResourcePlanAllocatedOrgUnit.class, null);
        }

        return ok(views.html.core.portfolioentryplanning.allocated_org_unit_manage.render(portfolioEntry, allocatedOrgUnitForm));

    }

    /**
     * Save an allocated delivery unit.
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processManageAllocatedOrgUnit() {

        // bind the form
        Form<PortfolioEntryResourcePlanAllocatedOrgUnitFormData> boundForm = allocatedOrgUnitFormTemplate.bindFromRequest();

        // get the portfolioEntry
        Long id = Long.valueOf(boundForm.data().get("id"));
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        if (boundForm.hasErrors() || this.getCustomAttributeManagerService().validateValues(boundForm, PortfolioEntryResourcePlanAllocatedOrgUnit.class)) {
            return ok(allocated_org_unit_manage.render(portfolioEntry, boundForm));
        }

        PortfolioEntryResourcePlanAllocatedOrgUnitFormData portfolioEntryResourcePlanAllocatedOrgUnitFormData = boundForm.get();

        PortfolioEntryResourcePlanAllocatedOrgUnit allocatedOrgUnit = null;

        Ebean.beginTransaction();
        try {

            if (portfolioEntryResourcePlanAllocatedOrgUnitFormData.allocationId == null) { // create

                // get current planning
                LifeCycleInstancePlanning planning = portfolioEntry.activeLifeCycleInstance.getCurrentLifeCycleInstancePlanning();

                // get the resource plan
                PortfolioEntryResourcePlan resourcePlan = planning.portfolioEntryResourcePlan;

                allocatedOrgUnit = new PortfolioEntryResourcePlanAllocatedOrgUnit();
                allocatedOrgUnit.portfolioEntryResourcePlan = resourcePlan;

                portfolioEntryResourcePlanAllocatedOrgUnitFormData.fill(allocatedOrgUnit);
                allocatedOrgUnit.lastStatusTypeUpdateActor = ActorDao.getActorByUid(getUserSessionManagerPlugin().getUserSessionId(ctx()));
                allocatedOrgUnit.lastStatusTypeUpdateTime = new Date();

                allocatedOrgUnit.save();

                Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.allocated_org_unit.add.successful"));

            } else { // edit

                allocatedOrgUnit = PortfolioEntryResourcePlanDAO
                        .getPEResourcePlanAllocatedOrgUnitById(portfolioEntryResourcePlanAllocatedOrgUnitFormData.allocationId);

                // security: the portfolioEntry must be related to the object
                if (!allocatedOrgUnit.portfolioEntryResourcePlan.lifeCycleInstancePlannings.get(0).lifeCycleInstance.portfolioEntry.id.equals(id)) {
                    return forbidden(access_forbidden.render(""));
                }

                portfolioEntryResourcePlanAllocatedOrgUnitFormData.fill(allocatedOrgUnit);
                allocatedOrgUnit.lastStatusTypeUpdateActor = ActorDao.getActorByUid(getUserSessionManagerPlugin().getUserSessionId(ctx()));
                allocatedOrgUnit.lastStatusTypeUpdateTime = new Date();
                allocatedOrgUnit.update();

                Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.allocated_org_unit.edit.successful"));
            }

            // assign the delivery unit to the PE (if not already)
            if (!PortfolioEntryDao.isDeliveryUnitOfPE(allocatedOrgUnit.orgUnit.id, portfolioEntry.id)) {
                if (portfolioEntry.deliveryUnits == null) {
                    portfolioEntry.deliveryUnits = new ArrayList<>();
                }
                portfolioEntry.deliveryUnits.add(allocatedOrgUnit.orgUnit);
                portfolioEntry.save();
            }

            // save the custom attributes
            this.getCustomAttributeManagerService().validateAndSaveValues(boundForm, PortfolioEntryResourcePlanAllocatedOrgUnit.class, allocatedOrgUnit.id);

            Ebean.commitTransaction();
            Ebean.endTransaction();

        } catch (Exception e) {

            Ebean.rollbackTransaction();
            Ebean.endTransaction();
            return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());

        }

        return redirect(routes.PortfolioEntryPlanningController.resources(id));
    }

    /**
     * Delete an allocated delivery unit.
     * 
     * @param id
     *            the portfolio entry id
     * @param allocatedOrgUnitId
     *            the allocated delivery unit id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result deleteAllocatedOrgUnit(Long id, Long allocatedOrgUnitId) {

        // get the allocated org unit
        PortfolioEntryResourcePlanAllocatedOrgUnit allocatedOrgUnit = PortfolioEntryResourcePlanDAO.getPEResourcePlanAllocatedOrgUnitById(allocatedOrgUnitId);

        // security: the portfolioEntry must be related to the object
        if (!allocatedOrgUnit.portfolioEntryResourcePlan.lifeCycleInstancePlannings.get(0).lifeCycleInstance.portfolioEntry.id.equals(id)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        // set the delete flag to true
        allocatedOrgUnit.doDelete();

        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.allocated_org_unit.delete.successful"));

        return redirect(controllers.core.routes.PortfolioEntryPlanningController.resources(id));
    }

    /**
     * Create or edit an allocated competency (for a resource plan).
     * 
     * @param id
     *            the porfolio entry id
     * @param allocatedCompetencyId
     *            the allocated competency id, set to 0 for create case
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result manageAllocatedCompetency(Long id, Long allocatedCompetencyId) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // initiate the form with the template
        Form<PortfolioEntryResourcePlanAllocatedCompetencyFormData> allocatedCompetencyForm = allocatedCompetencyFormTemplate;

        // edit case: inject values
        if (!allocatedCompetencyId.equals(Long.valueOf(0))) {

            PortfolioEntryResourcePlanAllocatedCompetency allocatedCompetency = PortfolioEntryResourcePlanDAO
                    .getPEResourcePlanAllocatedCompetencyById(allocatedCompetencyId);

            // security: the portfolioEntry must be related to the object
            if (!allocatedCompetency.portfolioEntryResourcePlan.lifeCycleInstancePlannings.get(0).lifeCycleInstance.portfolioEntry.id.equals(id)) {
                return forbidden(views.html.error.access_forbidden.render(""));
            }

            allocatedCompetencyForm = allocatedCompetencyFormTemplate.fill(new PortfolioEntryResourcePlanAllocatedCompetencyFormData(allocatedCompetency));

            // add the custom attributes values
            this.getCustomAttributeManagerService().fillWithValues(allocatedCompetencyForm, PortfolioEntryResourcePlanAllocatedCompetency.class,
                    allocatedCompetencyId);
        } else {
            allocatedCompetencyForm = allocatedCompetencyFormTemplate.fill(new PortfolioEntryResourcePlanAllocatedCompetencyFormData());
            // add the custom attributes default values
            this.getCustomAttributeManagerService().fillWithValues(allocatedCompetencyForm, PortfolioEntryResourcePlanAllocatedCompetency.class, null);
        }

        return ok(views.html.core.portfolioentryplanning.allocated_competency_manage.render(portfolioEntry, allocatedCompetencyForm));
    }

    /**
     * Save an allocated competency.
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processManageAllocatedCompetency() {

        // bind the form
        Form<PortfolioEntryResourcePlanAllocatedCompetencyFormData> boundForm = allocatedCompetencyFormTemplate.bindFromRequest();

        // get the portfolioEntry
        Long id = Long.valueOf(boundForm.data().get("id"));
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        if (boundForm.hasErrors() || this.getCustomAttributeManagerService().validateValues(boundForm, PortfolioEntryResourcePlanAllocatedCompetency.class)) {
            return ok(views.html.core.portfolioentryplanning.allocated_competency_manage.render(portfolioEntry, boundForm));
        }

        PortfolioEntryResourcePlanAllocatedCompetencyFormData allocatedCompetencyFormData = boundForm.get();

        PortfolioEntryResourcePlanAllocatedCompetency allocatedCompetency = null;

        Ebean.beginTransaction();
        try {

            if (allocatedCompetencyFormData.allocationId == null) { // create

                // get current planning
                LifeCycleInstancePlanning planning = portfolioEntry.activeLifeCycleInstance.getCurrentLifeCycleInstancePlanning();

                // get the resource plan
                PortfolioEntryResourcePlan resourcePlan = planning.portfolioEntryResourcePlan;

                allocatedCompetency = new PortfolioEntryResourcePlanAllocatedCompetency();
                allocatedCompetency.portfolioEntryResourcePlan = resourcePlan;

                allocatedCompetencyFormData.fill(allocatedCompetency);
                allocatedCompetency.lastStatusTypeUpdateActor = ActorDao.getActorByUid(getUserSessionManagerPlugin().getUserSessionId(ctx()));
                allocatedCompetency.lastStatusTypeUpdateTime = new Date();

                allocatedCompetency.save();

                Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.allocated_competency.add.successful"));

            } else { // edit

                allocatedCompetency = PortfolioEntryResourcePlanDAO
                        .getPEResourcePlanAllocatedCompetencyById(allocatedCompetencyFormData.allocationId);

                // security: the portfolioEntry must be related to the object
                if (!allocatedCompetency.portfolioEntryResourcePlan.lifeCycleInstancePlannings.get(0).lifeCycleInstance.portfolioEntry.id.equals(id)) {
                    return forbidden(views.html.error.access_forbidden.render(""));
                }

                allocatedCompetencyFormData.fill(allocatedCompetency);
                allocatedCompetency.lastStatusTypeUpdateActor = ActorDao.getActorByUid(getUserSessionManagerPlugin().getUserSessionId(ctx()));
                allocatedCompetency.lastStatusTypeUpdateTime = new Date();
                allocatedCompetency.update();

                Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.allocated_competency.edit.successful"));
            }

            // save the custom attributes
            this.getCustomAttributeManagerService().validateAndSaveValues(boundForm, PortfolioEntryResourcePlanAllocatedCompetency.class,
                    allocatedCompetency.id);

            Ebean.commitTransaction();
            Ebean.endTransaction();

        } catch (Exception e) {

            Ebean.rollbackTransaction();
            Ebean.endTransaction();
            return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());

        }

        return redirect(controllers.core.routes.PortfolioEntryPlanningController.resources(id));

    }

    /**
     * Delete an allocated competency.
     * 
     * @param id
     *            the portfolio entry id
     * @param allocatedCompetencyId
     *            the allocated competency id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result deleteAllocatedCompetency(Long id, Long allocatedCompetencyId) {

        // get the allocated competency
        PortfolioEntryResourcePlanAllocatedCompetency allocatedCompetency = PortfolioEntryResourcePlanDAO
                .getPEResourcePlanAllocatedCompetencyById(allocatedCompetencyId);

        // security: the portfolioEntry must be related to the object
        if (!allocatedCompetency.portfolioEntryResourcePlan.lifeCycleInstancePlannings.get(0).lifeCycleInstance.portfolioEntry.id.equals(id)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        // set the delete flag to true
        allocatedCompetency.doDelete();

        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.allocated_competency.delete.successful"));

        return redirect(controllers.core.routes.PortfolioEntryPlanningController.resources(id));

    }

    /**
     * Display the form to reallocate an org unit to an actor.
     * 
     * @param id
     *            the portfolio entry id
     * @param allocatedOrgUnitId
     *            the allocated org unit
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result reallocateOrgUnit(Long id, Long allocatedOrgUnitId) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the allocated org unit
        PortfolioEntryResourcePlanAllocatedOrgUnit allocatedOrgUnit = PortfolioEntryResourcePlanDAO.getPEResourcePlanAllocatedOrgUnitById(allocatedOrgUnitId);

        // get the org unit
        OrgUnit orgUnit = allocatedOrgUnit.orgUnit;

        // security: the portfolioEntry must be related to the object
        if (!allocatedOrgUnit.portfolioEntryResourcePlan.lifeCycleInstancePlannings.get(0).lifeCycleInstance.portfolioEntry.id.equals(id)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        Form<PortfolioEntryResourcePlanAllocatedActorFormData> allocatedActorForm = reallocateOrgUnitFormTemplate
                .fill(new PortfolioEntryResourcePlanAllocatedActorFormData(allocatedOrgUnit));

        return ok(views.html.core.portfolioentryplanning.allocated_org_unit_reallocate.render(portfolioEntry, orgUnit, allocatedActorForm));

    }

    /**
     * Process the reallocation of an org unit to an actor.
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processReallocateOrgUnit() {

        // bind the form
        Form<PortfolioEntryResourcePlanAllocatedActorFormData> boundForm = reallocateOrgUnitFormTemplate.bindFromRequest();

        // get the portfolio entry
        Long id = Long.valueOf(boundForm.data().get("id"));
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the allocated org unit
        Long allocatedOrgUnitId = Long.valueOf(boundForm.data().get("allocatedOrgUnitId"));
        PortfolioEntryResourcePlanAllocatedOrgUnit allocatedOrgUnit = PortfolioEntryResourcePlanDAO.getPEResourcePlanAllocatedOrgUnitById(allocatedOrgUnitId);

        if (boundForm.hasErrors() || this.getCustomAttributeManagerService().validateValues(boundForm, PortfolioEntryResourcePlanAllocatedActor.class)) {
            return ok(views.html.core.portfolioentryplanning.allocated_org_unit_reallocate.render(portfolioEntry, allocatedOrgUnit.orgUnit, boundForm));
        }

        PortfolioEntryResourcePlanAllocatedActorFormData allocatedActorFormData = boundForm.get();

        // get current planning
        LifeCycleInstancePlanning planning = portfolioEntry.activeLifeCycleInstance.getCurrentLifeCycleInstancePlanning();

        // get the resource plan
        PortfolioEntryResourcePlan resourcePlan = planning.portfolioEntryResourcePlan;

        // create the allocated actor
        PortfolioEntryResourcePlanAllocatedActor allocatedActor = new PortfolioEntryResourcePlanAllocatedActor();
        allocatedActor.portfolioEntryResourcePlan = resourcePlan;
        allocatedActorFormData.fill(allocatedActor);
        allocatedActor.lastStatusTypeUpdateActor = ActorDao.getActorByUid(getUserSessionManagerPlugin().getUserSessionId(ctx()));
        allocatedActor.lastStatusTypeUpdateTime = new Date();
        allocatedActor.save();

        // save the custom attributes
        this.getCustomAttributeManagerService().validateAndSaveValues(boundForm, PortfolioEntryResourcePlanAllocatedActor.class, allocatedActor.id);

        // remove the allocated org unit
        allocatedOrgUnit.doDelete();

        // create the stakeholder if necessary
        if (StakeholderDao.getStakeholderByActorAndTypeAndPE(allocatedActor.actor.id, allocatedActorFormData.stakeholderType, portfolioEntry.id) == null) {
            Stakeholder stakeholder = new Stakeholder();
            stakeholder.actor = allocatedActor.actor;
            stakeholder.portfolioEntry = portfolioEntry;
            stakeholder.stakeholderType = StakeholderDao.getStakeholderTypeById(allocatedActorFormData.stakeholderType);
            stakeholder.save();
        }

        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.reallocate_resource.successful"));

        if (allocatedOrgUnit.days.compareTo(allocatedActor.days) > 0) {
            // Compute remaining balance by substracting days allocated to the actor from the initial org unit allocation month by month (minimum 0 for a month)
            Double daysBalance = allocatedOrgUnit.portfolioEntryResourcePlanAllocatedOrgUnitDetails.stream()
                    .mapToDouble(detail -> {
                        Double days = detail.days;
                        ResourceAllocationDetail actorDetail = allocatedActor.getDetail(detail.year, detail.month);
                        // If the corresponding month exists in the actor allocation, substract (minimum 0)
                        if (actorDetail != null) {
                            days = actorDetail.getDays() > days ? 0 : days - actorDetail.getDays();
                        }
                        return days;
                    })
                    .sum();

            return redirect(controllers.core.routes.PortfolioEntryPlanningController.reallocateOrgUnitReportBalance(
                    portfolioEntry.id,
                    allocatedOrgUnit.id,
                    allocatedActor.id,
                    daysBalance
            ));
        } else {
            return redirect(controllers.core.routes.PortfolioEntryPlanningController.resources(id));
        }

    }

    /**
     * Report the days balance of an org unit reallocation.
     * 
     * @param id
     *            the portfolio entry id
     * @param allocatedOrgUnitId
     *            the allocated org unit id
     * @param days
     *            the balance to report
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result reallocateOrgUnitReportBalance(Long id, Long allocatedOrgUnitId, Long allocatedActorId, Double days) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the deleted allocated org unit
        PortfolioEntryResourcePlanAllocatedOrgUnit allocatedOrgUnit = PortfolioEntryResourcePlanDAO
                .getPEResourcePlanAllocatedOrgUnitDeletedById(allocatedOrgUnitId);

        return ok(views.html.core.portfolioentryplanning.allocated_org_unit_reallocate_report_balance.render(portfolioEntry, allocatedOrgUnit, allocatedActorId, days));
    }

    /**
     * Process the reporting of the days balance of an org unit reallocation.
     * 
     * @param id
     *            the portfolio entry id
     * @param allocatedOrgUnitId
     *            the allocated org unit id
     * @param days
     *            the balance to report
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processReallocateOrgUnitReportBalance(Long id, Long allocatedOrgUnitId, Long allocatedActorId) {

        // get the deleted allocated org unit
        PortfolioEntryResourcePlanAllocatedOrgUnit allocatedOrgUnit = PortfolioEntryResourcePlanDAO
                .getPEResourcePlanAllocatedOrgUnitDeletedById(allocatedOrgUnitId);

        // get the allocated actor
        PortfolioEntryResourcePlanAllocatedActor allocatedActor = PortfolioEntryResourcePlanDAO.getPEPlanAllocatedActorById(allocatedActorId);

        // restore the initial allocated org unit with the days
        allocatedOrgUnit.deleted = false;
        allocatedOrgUnit.forecastDays = null;

        // Substract details month by month
        allocatedOrgUnit.getDetails()
                .stream()
                .forEach(detail -> {
                    ResourceAllocationDetail allocatedActorDetail = allocatedActor.getDetail(detail.getYear(), detail.getMonth());
                    if (allocatedActorDetail != null) {
                        if (detail.getDays().compareTo(allocatedActorDetail.getDays()) > 0) {
                            detail.setDays(detail.getDays() - allocatedActorDetail.getDays());
                            allocatedOrgUnit.days = allocatedOrgUnit.days.subtract(BigDecimal.valueOf(allocatedActorDetail.getDays()));
                        } else {
                            allocatedOrgUnit.days = allocatedOrgUnit.days.subtract(BigDecimal.valueOf(detail.getDays()));
                            detail.setDays(0.0);
                        }
                        detail.save();
                    }
                });

        allocatedOrgUnit.save();

        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.reallocate.report_balance.successful"));

        return redirect(controllers.core.routes.PortfolioEntryPlanningController.resources(id));
    }

    /**
     * Display the form to reallocate a competency to an actor.
     * 
     * @param id
     *            the portfolio entry id
     * @param allocatedCompetencyId
     *            the allocated competency
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result reallocateCompetency(Long id, Long allocatedCompetencyId) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the allocated org unit
        PortfolioEntryResourcePlanAllocatedCompetency allocatedCompetency = PortfolioEntryResourcePlanDAO
                .getPEResourcePlanAllocatedCompetencyById(allocatedCompetencyId);

        // get the competency
        Competency competency = allocatedCompetency.competency;

        // security: the portfolioEntry must be related to the object
        if (!allocatedCompetency.portfolioEntryResourcePlan.lifeCycleInstancePlannings.get(0).lifeCycleInstance.portfolioEntry.id.equals(id)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        Form<PortfolioEntryResourcePlanAllocatedActorFormData> allocatedActorForm = reallocateCompetencyFormTemplate
                .fill(new PortfolioEntryResourcePlanAllocatedActorFormData(allocatedCompetency));

        return ok(views.html.core.portfolioentryplanning.allocated_competency_reallocate.render(portfolioEntry, competency, allocatedActorForm));

    }

    /**
     * Process the reallocation of an competency to an actor.
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processReallocateCompetency() {

        // bind the form
        Form<PortfolioEntryResourcePlanAllocatedActorFormData> boundForm = reallocateCompetencyFormTemplate.bindFromRequest();

        // get the portfolio entry
        Long id = Long.valueOf(boundForm.data().get("id"));
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the allocated competency
        Long allocatedCompetencyId = Long.valueOf(boundForm.data().get("allocatedCompetencyId"));
        PortfolioEntryResourcePlanAllocatedCompetency allocatedCompetency = PortfolioEntryResourcePlanDAO
                .getPEResourcePlanAllocatedCompetencyById(allocatedCompetencyId);

        if (boundForm.hasErrors() || this.getCustomAttributeManagerService().validateValues(boundForm, PortfolioEntryResourcePlanAllocatedActor.class)) {
            return ok(
                    views.html.core.portfolioentryplanning.allocated_competency_reallocate.render(portfolioEntry, allocatedCompetency.competency, boundForm));
        }

        PortfolioEntryResourcePlanAllocatedActorFormData allocatedActorFormData = boundForm.get();

        // get current planning
        LifeCycleInstancePlanning planning = portfolioEntry.activeLifeCycleInstance.getCurrentLifeCycleInstancePlanning();

        // get the resource plan
        PortfolioEntryResourcePlan resourcePlan = planning.portfolioEntryResourcePlan;

        // create the allocated actor
        PortfolioEntryResourcePlanAllocatedActor allocatedActor = new PortfolioEntryResourcePlanAllocatedActor();
        allocatedActor.portfolioEntryResourcePlan = resourcePlan;
        allocatedActorFormData.fill(allocatedActor);
        allocatedActor.lastStatusTypeUpdateActor = ActorDao.getActorByUid(getUserSessionManagerPlugin().getUserSessionId(ctx()));
        allocatedActor.lastStatusTypeUpdateTime = new Date();
        allocatedActor.save();

        // save the custom attributes
        this.getCustomAttributeManagerService().validateAndSaveValues(boundForm, PortfolioEntryResourcePlanAllocatedActor.class, allocatedActor.id);

        // remove the allocated competency
        allocatedCompetency.doDelete();

        // create the stakeholder if necessary
        if (StakeholderDao.getStakeholderByActorAndTypeAndPE(allocatedActor.actor.id, allocatedActorFormData.stakeholderType, portfolioEntry.id) == null) {
            Stakeholder stakeholder = new Stakeholder();
            stakeholder.actor = allocatedActor.actor;
            stakeholder.portfolioEntry = portfolioEntry;
            stakeholder.stakeholderType = StakeholderDao.getStakeholderTypeById(allocatedActorFormData.stakeholderType);
            stakeholder.save();
        }

        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.reallocate_resource.successful"));

        if (allocatedCompetency.days.compareTo(allocatedActor.days) > 0) {
            return redirect(controllers.core.routes.PortfolioEntryPlanningController.reallocateCompetencyReportBalance(portfolioEntry.id,
                    allocatedCompetency.id, allocatedCompetency.days.subtract(allocatedActor.days).doubleValue()));
        } else {
            return redirect(controllers.core.routes.PortfolioEntryPlanningController.resources(id));
        }

    }

    /**
     * Report the days balance of a competency reallocation.
     * 
     * @param id
     *            the portfolio entry id
     * @param allocatedCompetencyId
     *            the allocated competency id
     * @param days
     *            the balance to report
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result reallocateCompetencyReportBalance(Long id, Long allocatedCompetencyId, Double days) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the deleted allocated competency
        PortfolioEntryResourcePlanAllocatedCompetency allocatedCompetency = PortfolioEntryResourcePlanDAO
                .getPEResourcePlanAllocatedCompetencyDeletedById(allocatedCompetencyId);

        return ok(views.html.core.portfolioentryplanning.allocated_competency_reallocate_report_balance.render(portfolioEntry, allocatedCompetency, days));
    }

    /**
     * Process the reporting of the days balance of a competency reallocation.
     * 
     * @param id
     *            the portfolio entry id
     * @param allocatedCompetencyId
     *            the competency unit id
     * @param days
     *            the balance to report
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processReallocateCompetencyReportBalance(Long id, Long allocatedCompetencyId, Double days) {

        // get the deleted allocated competency
        PortfolioEntryResourcePlanAllocatedCompetency allocatedCompetency = PortfolioEntryResourcePlanDAO
                .getPEResourcePlanAllocatedCompetencyDeletedById(allocatedCompetencyId);

        // restore the initial allocated competency with the days
        allocatedCompetency.deleted = false;
        allocatedCompetency.days = new BigDecimal(days);
        allocatedCompetency.save();

        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_planning.reallocate.report_balance.successful"));

        return redirect(controllers.core.routes.PortfolioEntryPlanningController.resources(id));
    }

    /**
     * Get the package status as a value holder collection.
     */
    public static ISelectableValueHolderCollection<String> getPackageStatusAsValueHolderCollection() {
        ISelectableValueHolderCollection<String> allStatus = new DefaultSelectableValueHolderCollection<String>();
        for (PortfolioEntryPlanningPackage.Status status : PortfolioEntryPlanningPackage.Status.values()) {
            allStatus.add(new DefaultSelectableValueHolder<String>(status.name(),
                    Msg.get("object.portfolio_entry_planning_package.status." + status.name() + ".label")));
        }
        return allStatus;
    }

    /**
     * Get the message service.
     */
    private II18nMessagesPlugin getMessagesPlugin() {
        return messagesPlugin;
    }

    /**
     * Get the attachment service.
     */
    private IAttachmentManagerPlugin getAttachmentManagerPlugin() {
        return attachmentManagerPlugin;
    }

    /**
     * Get the preference manager service.
     */
    private IPreferenceManagerPlugin getPreferenceManagerPlugin() {
        return this.preferenceManagerPlugin;
    }

    /**
     * The configuration of the overview (gantt chart).
     * 
     * @author Johann Kohler
     * 
     */
    public static class OverviewConfiguration {

        public boolean phases;
        public boolean milestones;
        public boolean packages;
        public boolean iterations;

        /**
         * Default constructor.
         */
        public OverviewConfiguration() {
        }

        /**
         * Construct an overview configuration with a default value.
         * 
         * @param defaultValue
         *            the default value (same for all attributes)
         */
        public OverviewConfiguration(boolean defaultValue) {
            this.phases = defaultValue;
            this.milestones = defaultValue;
            this.packages = defaultValue;
            this.iterations = defaultValue;
        }

        /**
         * Load the configuration from a user preference.
         * 
         * @param preferenceManagerPlugin
         *            the preference manager service
         */
        public static OverviewConfiguration load(IPreferenceManagerPlugin preferenceManagerPlugin) {
            String overviewPreferenceAsJson = preferenceManagerPlugin.getPreferenceValueAsString(IMafConstants.PORTFOLIO_ENTRY_PLANNING_OVERVIEW_PREFERENCE);
            if (overviewPreferenceAsJson == null || overviewPreferenceAsJson.equals("")) {
                OverviewConfiguration conf = new OverviewConfiguration(true);
                store(preferenceManagerPlugin, conf);
                return conf;
            } else {
                try {
                    return new ObjectMapper().readValue(overviewPreferenceAsJson, OverviewConfiguration.class);
                } catch (Exception e) {
                    Logger.warn("impossible to parse the value of PORTFOLIO_ENTRY_PLANNING_OVERVIEW_PREFERENCE", e);
                    OverviewConfiguration conf = new OverviewConfiguration(true);
                    store(preferenceManagerPlugin, conf);
                    return conf;
                }
            }
        }

        /**
         * Store the configuration in a user preference.
         * 
         * @param preferenceManagerPlugin
         *            the preference manager service
         * @param conf
         *            the overview configuration
         */
        public static void store(IPreferenceManagerPlugin preferenceManagerPlugin, OverviewConfiguration conf) {
            String json = "";
            try {
                ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
                json = ow.writeValueAsString(conf);
            } catch (JsonProcessingException e) {
                Logger.error("impossible to jsonify the OverviewConfiguration", e);
            }
            preferenceManagerPlugin.updatePreferenceValue(IMafConstants.PORTFOLIO_ENTRY_PLANNING_OVERVIEW_PREFERENCE, json);
        }

    }

    /**
     * Get the user session manager service.
     */
    private IUserSessionManagerPlugin getUserSessionManagerPlugin() {
        return userSessionManagerPlugin;
    }

    /**
     * Get the data syndication service.
     */
    private IDataSyndicationService getDataSyndicationService() {
        return dataSyndicationService;
    }

    /**
     * Get the security service.
     */
    private ISecurityService getSecurityService() {
        return securityService;
    }

    /**
     * Get the Play configuration service.
     */
    private Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Get the i18n messages service.
     */
    private II18nMessagesPlugin getI18nMessagesPlugin() {
        return i18nMessagesPlugin;
    }

    /**
     * Get the budget tracking service.
     */
    private IBudgetTrackingService getBudgetTrackingService() {
        return this.budgetTrackingService;
    }

    /**
     * Get the notification manager service.
     */
    private INotificationManagerPlugin getNotificationManagerService() {
        return this.notificationManagerService;
    }

    /**
     * Get the table provider.
     */
    private ITableProvider getTableProvider() {
        return this.tableProvider;
    }

    /**
     * Get the custom attribute manager service.
     */
    private ICustomAttributeManagerService getCustomAttributeManagerService() {
        return this.customAttributeManagerService;
    }

}
