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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;

import dao.pmo.*;
import models.pmo.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.avaje.ebean.ExpressionList;

import be.objectify.deadbolt.java.actions.Dynamic;
import constants.IMafConstants;
import controllers.ControllersUtils;
import dao.governance.LifeCycleMilestoneDao;
import dao.pmo.PortfolioEntryRiskAndIssueDao;
import dao.reporting.ReportingDao;
import dao.timesheet.TimesheetDao;
import framework.security.ISecurityService;
import framework.services.account.IPreferenceManagerPlugin;
import framework.services.configuration.II18nMessagesPlugin;
import framework.services.custom_attribute.ICustomAttributeManagerService;
import framework.services.notification.INotificationManagerPlugin;
import framework.services.session.IUserSessionManagerPlugin;
import framework.services.storage.IAttachmentManagerPlugin;
import framework.services.storage.IPersonalStoragePlugin;
import framework.services.system.ISysAdminUtils;
import framework.utils.CssValueForValueHolder;
import framework.utils.DefaultSelectableValueHolderCollection;
import framework.utils.FileAttachmentHelper;
import framework.utils.FilterConfig;
import framework.utils.Msg;
import framework.utils.Pagination;
import framework.utils.Table;
import framework.utils.TableExcelRenderer;
import framework.utils.Utilities;
import models.framework_models.account.NotificationCategory;
import models.framework_models.account.NotificationCategory.Code;
import models.framework_models.common.Attachment;
import models.governance.LifeCycleMilestoneInstance;
import models.reporting.Reporting;
import models.reporting.Reporting.Format;
import models.timesheet.TimesheetLog;
import play.Configuration;
import play.Logger;
import play.data.Form;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import scala.concurrent.duration.Duration;
import security.CheckPortfolioEntryExists;
import services.datasyndication.IDataSyndicationService;
import services.datasyndication.models.DataSyndicationAgreementItem;
import services.datasyndication.models.DataSyndicationAgreementLink;
import services.tableprovider.ITableProvider;
import utils.form.*;
import utils.reporting.IReportingUtils;
import utils.table.*;

/**
 * The controller which allows to manage the status reporting (events, risks,
 * reports...) of a portfolio entry.
 * 
 * @author Johann Kohler
 */
public class PortfolioEntryStatusReportingController extends Controller {
    @Inject
    private INotificationManagerPlugin notificationManagerPlugin;
    @Inject
    private IPersonalStoragePlugin personalStoragePlugin;
    @Inject
    private IUserSessionManagerPlugin userSessionManagerPlugin;
    @Inject
    private IPreferenceManagerPlugin preferenceManagerPlugin;
    @Inject
    private IReportingUtils reportingUtils;
    @Inject
    private ISysAdminUtils sysAdminUtils;
    @Inject
    private II18nMessagesPlugin i18nMessagesPlugin;
    @Inject
    private IAttachmentManagerPlugin attachmentManagerPlugin;
    @Inject
    private ISecurityService securityService;
    @Inject
    private IDataSyndicationService dataSyndicationService;
    @Inject
    private Configuration configuration;
    @Inject
    private ITableProvider tableProvider;
    @Inject
    private ICustomAttributeManagerService customAttributeManagerService;

    private static Logger.ALogger log = Logger.of(PortfolioEntryStatusReportingController.class);

    public static Form<PortfolioEntryReportFormData> reportFormTemplate = Form.form(PortfolioEntryReportFormData.class);
    public static Form<PortfolioEntryRiskFormData> riskFormTemplate = Form.form(PortfolioEntryRiskFormData.class);
    public static Form<PortfolioEntryIssueFormData> issueFormTemplate = Form.form(PortfolioEntryIssueFormData.class);
    public static Form<PortfolioEntryEventFormData> eventFormTemplate = Form.form(PortfolioEntryEventFormData.class);
    private static Form<AttachmentFormData> attachmentFormTemplate = Form.form(AttachmentFormData.class);

    /**
     * Export the actor allocation.
     * 
     * @param id
     *            the initiative id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Promise<Result> exportStatusReport(Long id) {

        return Promise.promise(() -> {

            String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());

            Pair<String, Format> reportNameAndFormat = getReportStatusTemplateNameAndFormat();

            if (reportNameAndFormat == null) {

                List<PortfolioEntryReport> reports = PortfolioEntryReportDao.getPEReportAsListByPE(id);

                List<PortfolioEntryReportListView> listView = reports.stream().map(PortfolioEntryReportListView::new).collect(Collectors.toList());

                Set<String> columnsToHide = new HashSet<>();
                columnsToHide.add("editActionLink");
                columnsToHide.add("removeActionLink");

                Table<PortfolioEntryReportListView> table = this.getTableProvider().get().portfolioEntryReport.templateTable
                        .fill(listView, columnsToHide);

                final byte[] excelFile = TableExcelRenderer.renderFormatted(table);

                final String fileName = String.format("statusReportsExport_%1$td_%1$tm_%1$ty_%1$tH-%1$tM-%1$tS.xlsx", new Date());
                final String successTitle = Msg.get("excel.export.success.title");
                final String successMessage = Msg.get("excel.export.success.message", fileName);
                final String failureTitle = Msg.get("excel.export.failure.title");
                final String failureMessage = Msg.get("excel.export.failure.message");

                getSysAdminUtils().scheduleOnce(false, "Status Reports Excel Report", Duration.create(0, TimeUnit.MILLISECONDS), () -> {
                    try {
                        OutputStream out = getPersonalStoragePlugin().createNewFile(uid, fileName);
                        IOUtils.copy(new ByteArrayInputStream(excelFile), out);
                        getNotificationManagerPlugin().sendNotification(uid, NotificationCategory.getByCode(Code.DOCUMENT), successTitle, successMessage,
                                controllers.my.routes.MyPersonalStorage.index().url());
                    } catch (IOException e) {
                        log.error("Unable to export the excel file", e);
                        getNotificationManagerPlugin().sendNotification(uid, NotificationCategory.getByCode(Code.ISSUE), failureTitle, failureMessage,
                                routes.PortfolioEntryStatusReportingController.registers(id, 1, 1, 1, false, false).url());
                    }
                });

            } else {
                Reporting report = ReportingDao.getReportingByTemplate(reportNameAndFormat.getLeft());

                // construct the report parameters
                Map<String, Object> reportParameters = new HashMap<>();
                reportParameters.put("REPORT_" + reportNameAndFormat.getLeft().toUpperCase() + "_PORTFOLIO_ENTRY", id);

                getReportingUtils().generate(ctx(), report, getI18nMessagesPlugin().getCurrentLanguage().getCode(), reportNameAndFormat.getRight(), reportParameters);

            }

            Utilities.sendSuccessFlashMessage(Msg.get("core.reporting.generate.request.success"));

            return ok(Json.newObject());
       });
    }

    /**
     * Display the registers (list of reports, risk and issues) of a portfolio
     * entry.
     * 
     * note: risks and issues are stored in the same DB table
     * (portfolio_entry_risk), this is the flag has_occured that determines if
     * it's a risk (false) or an issue (true)
     * 
     * @param id
     *            the portfolio entry id
     * @param pageReports
     *            the current page for reports table
     * @param pageRisks
     *            the current page for the risks table
     * @param pageIssues
     *            the current page for the issues table
     * @param viewAllRisks
     *            set to true if all risks (including the inactive) must be
     *            displayed
     * @param viewAllIssues
     *            set to true if all issues (including the inactive) must be
     *            displayed
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result registers(Long id, Integer pageReports, Integer pageRisks, Integer pageIssues, Boolean viewAllRisks, Boolean viewAllIssues) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the reports

        Pagination<PortfolioEntryReport> reportsPagination = PortfolioEntryReportDao.getPEReportAsPaginationByPE(id, true);
        reportsPagination.setPageQueryName("pageReports");
        reportsPagination.setCurrentPage(pageReports);

        List<PortfolioEntryReportListView> portfolioEntryReportsListView = reportsPagination.getListOfObjects().stream()
                .map(PortfolioEntryReportListView::new)
                .collect(Collectors.toList());

        Set<String> hideColumnsForReport = new HashSet<>();
        if (!getSecurityService().dynamic("PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION", "")) {
            hideColumnsForReport.add("editActionLink");
            hideColumnsForReport.add("deleteActionLink");
        }

        Table<PortfolioEntryReportListView> filledReportsTable = this.getTableProvider().get().portfolioEntryReport.templateTable
                .fill(portfolioEntryReportsListView, hideColumnsForReport);

        // get the syndicated reports
        List<DataSyndicationAgreementLink> agreementLinks = new ArrayList<>();
        DataSyndicationAgreementItem agreementItem = null;
        if (portfolioEntry.isSyndicated && dataSyndicationService.isActive()) {
            try {
                agreementItem = dataSyndicationService.getAgreementItemByDataTypeAndDescriptor(PortfolioEntry.class.getName(), "REPORT");
                if (agreementItem != null) {
                    agreementLinks = dataSyndicationService.getAgreementLinksOfItemAndSlaveObject(agreementItem, PortfolioEntry.class.getName(), id);
                }
            } catch (Exception e) {
                Logger.error("impossible to get the syndicated report data", e);
            }
        }

        // get the portfolioEntry risks

        Pagination<PortfolioEntryRisk> risksPagination = PortfolioEntryRiskAndIssueDao.getPERiskAsPaginationByPE(id, viewAllRisks);
        risksPagination.setPageQueryName("pageRisks");
        risksPagination.setCurrentPage(pageRisks);

        List<PortfolioEntryRiskListView> portfolioEntryRisksListView = risksPagination.getListOfObjects().stream().map(PortfolioEntryRiskListView::new).collect(Collectors.toList());

        Set<String> hideColumnsForRisk = new HashSet<String>();
        hideColumnsForRisk.add("isMitigated");
        hideColumnsForRisk.add("dueDate");
        if (!getSecurityService().dynamic("PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION", "")) {
            hideColumnsForRisk.add("editActionLink");
            hideColumnsForRisk.add("deleteActionLink");
        }

        Table<PortfolioEntryRiskListView> filledRisksTable = this.getTableProvider().get().portfolioEntryRisk.templateTable.fill(portfolioEntryRisksListView,
                hideColumnsForRisk);

        // get the portfolioEntry issues

        Pagination<PortfolioEntryIssue> issuesPagination = PortfolioEntryRiskAndIssueDao.getPEIssueAsPaginationByPE(id, viewAllIssues);
        issuesPagination.setPageQueryName("pageIssues");
        issuesPagination.setCurrentPage(pageIssues);

        List<PortfolioEntryIssueListView> portfolioEntryIssuesListView = issuesPagination.getListOfObjects().stream().map(PortfolioEntryIssueListView::new).collect(Collectors.toList());

        Set<String> hideColumnsForIssue = new HashSet<>();
        if (!getSecurityService().dynamic("PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION", "")) {
            hideColumnsForIssue.add("editActionLink");
            hideColumnsForIssue.add("deleteActionLink");
        }

        Table<PortfolioEntryIssueListView> filledIssuesTable = this.getTableProvider().get().portfolioEntryIssue.templateTable
                .fill(portfolioEntryIssuesListView, hideColumnsForIssue);

        return ok(views.html.core.portfolioentrystatusreporting.registers.render(portfolioEntry, filledReportsTable, reportsPagination, filledRisksTable,
                risksPagination, filledIssuesTable, issuesPagination, viewAllRisks, viewAllIssues, agreementLinks, agreementItem));
    }

    /**
     * Display the list of events.
     * 
     * @param id
     *            the portfolio entry id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result events(Long id) {

        // get the portfolio entry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        try {

            // get the filter config
            String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());
            FilterConfig<PortfolioEntryEventListView> filterConfig = this.getTableProvider().get().portfolioEntryEvent.filterConfig.getCurrent(uid,
                    request());

            // get the table
            Pair<Table<PortfolioEntryEventListView>, Pagination<PortfolioEntryEvent>> t = getEventsTable(id, filterConfig);

            return ok(views.html.core.portfolioentrystatusreporting.events.render(portfolioEntry, t.getLeft(), t.getRight(), filterConfig));

        } catch (Exception e) {

            return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());

        }
    }

    /**
     * Filter the events.
     * 
     * @param id
     *            the portfolio entry id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result eventsFilter(Long id) {

        try {

            // get the filter config
            String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());
            FilterConfig<PortfolioEntryEventListView> filterConfig = this.getTableProvider().get().portfolioEntryEvent.filterConfig
                    .persistCurrentInDefault(uid, request());

            if (filterConfig == null) {
                return ok(views.html.framework_views.parts.table.dynamic_tableview_no_more_compatible.render());
            } else {

                // get the table
                Pair<Table<PortfolioEntryEventListView>, Pagination<PortfolioEntryEvent>> t = getEventsTable(id, filterConfig);

                return ok(views.html.framework_views.parts.table.dynamic_tableview.render(t.getLeft(), t.getRight()));

            }

        } catch (Exception e) {
            return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());
        }
    }

    /**
     * Export the content of the current events table as Excel.
     * 
     * @param id
     *            the portfolio entry id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Promise<Result> exportEventsAsExcel(final Long id) {

        return Promise.promise(() -> {

            try {

                // Get the current user
                final String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());

                // construct the table
                FilterConfig<PortfolioEntryEventListView> filterConfig = getTableProvider().get().portfolioEntryEvent.filterConfig.getCurrent(uid,
                        request());

                ExpressionList<PortfolioEntryEvent> expressionList = filterConfig
                        .updateWithSearchExpression(PortfolioEntryEventDao.getPEEventAsExprByPE(id));
                filterConfig.updateWithSortExpression(expressionList);

                List<PortfolioEntryEventListView> portfolioEntryEventListView = expressionList.findList().stream().map(PortfolioEntryEventListView::new).collect(Collectors.toList());

                Table<PortfolioEntryEventListView> table = getTableProvider().get().portfolioEntryEvent.templateTable
                        .fillForFilterConfig(portfolioEntryEventListView, filterConfig.getColumnsToHide());

                final byte[] excelFile = TableExcelRenderer.renderFormatted(table);

                final String fileName = String.format("eventsExport_%1$td_%1$tm_%1$ty_%1$tH-%1$tM-%1$tS.xlsx", new Date());
                final String successTitle = Msg.get("excel.export.success.title");
                final String successMessage = Msg.get("excel.export.success.message", fileName);
                final String failureTitle = Msg.get("excel.export.failure.title");
                final String failureMessage = Msg.get("excel.export.failure.message");

                // Execute asynchronously
                getSysAdminUtils().scheduleOnce(false, "Events Excel Export", Duration.create(0, TimeUnit.MILLISECONDS), new Runnable() {
                    @Override
                    public void run() {

                        try {
                            OutputStream out = getPersonalStoragePlugin().createNewFile(uid, fileName);
                            IOUtils.copy(new ByteArrayInputStream(excelFile), out);
                            getNotificationManagerPlugin().sendNotification(uid, NotificationCategory.getByCode(Code.DOCUMENT), successTitle,
                                    successMessage, controllers.my.routes.MyPersonalStorage.index().url());
                        } catch (IOException e) {
                            log.error("Unable to export the excel file", e);
                            getNotificationManagerPlugin().sendNotification(uid, NotificationCategory.getByCode(Code.ISSUE), failureTitle, failureMessage,
                                    routes.PortfolioEntryStatusReportingController.events(id).url());
                        }
                    }
                });

                return ok(Json.newObject());

            } catch (Exception e) {
                return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());
            }
        });

    }

    /**
     * Get the events table for a portfolio entry and a filter config.
     * 
     * @param portfolioEntryId
     *            the portfolio entry id
     * @param filterConfig
     *            the filter config.
     */
    private Pair<Table<PortfolioEntryEventListView>, Pagination<PortfolioEntryEvent>> getEventsTable(Long portfolioEntryId,
            FilterConfig<PortfolioEntryEventListView> filterConfig) {

        ExpressionList<PortfolioEntryEvent> expressionList = filterConfig
                .updateWithSearchExpression(PortfolioEntryEventDao.getPEEventAsExprByPE(portfolioEntryId));
        filterConfig.updateWithSortExpression(expressionList);

        Pagination<PortfolioEntryEvent> pagination = new Pagination<PortfolioEntryEvent>(this.getPreferenceManagerPlugin(), expressionList);
        pagination.setCurrentPage(filterConfig.getCurrentPage());

        List<PortfolioEntryEventListView> portfolioEntryEventListView = pagination.getListOfObjects().stream().map(PortfolioEntryEventListView::new).collect(Collectors.toList());

        Set<String> hideColumnsForEvent = filterConfig.getColumnsToHide();
        if (!getSecurityService().dynamic("PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION", "")) {
            hideColumnsForEvent.add("editActionLink");
            hideColumnsForEvent.add("deleteActionLink");
        }

        Table<PortfolioEntryEventListView> table = this.getTableProvider().get().portfolioEntryEvent.templateTable
                .fillForFilterConfig(portfolioEntryEventListView, hideColumnsForEvent);

        return Pair.of(table, pagination);

    }

    /**
     * Display the details of a portfolio entry report.
     * 
     * @param id
     *            the portfolio entry id
     * @param reportId
     *            the report id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result viewReport(Long id, Long reportId) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the portfolio entry report
        PortfolioEntryReport portfolioEntryReport = PortfolioEntryReportDao.getPEReportById(reportId);

        // construct the corresponding form data (for the custom attributes)
        PortfolioEntryReportFormData portfolioEntryReportFormData = new PortfolioEntryReportFormData(portfolioEntryReport);

        // security: the portfolioEntry must be related to the object
        if (!portfolioEntryReport.portfolioEntry.id.equals(id)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        /*
         * Get the attachments
         */

        // authorize the attachments
        FileAttachmentHelper.getFileAttachmentsForDisplay(PortfolioEntryReport.class, reportId, getAttachmentManagerPlugin(), getUserSessionManagerPlugin());

        // create the table
        List<Attachment> attachments = Attachment.getAttachmentsFromObjectTypeAndObjectId(PortfolioEntryReport.class, reportId);

        List<AttachmentListView> attachmentsListView = attachments.stream().map(attachment -> new AttachmentListView(attachment,
                routes.PortfolioEntryStatusReportingController.deleteReportAttachment(id, reportId, attachment.id).url())).collect(Collectors.toList());

        Set<String> hideColumns = new HashSet<>();
        if (!getSecurityService().dynamic("PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION", "")) {
            hideColumns.add("removeActionLink");
        }

        Table<AttachmentListView> attachmentsTable = this.getTableProvider().get().attachment.templateTable.fill(attachmentsListView, hideColumns);

        return ok(views.html.core.portfolioentrystatusreporting.report_view.render(portfolioEntry, portfolioEntryReport, portfolioEntryReportFormData,
                attachmentsTable));
    }

    /**
     * Form to edit/create a new report for a portfolio entry.
     * 
     * @param id
     *            the portfolio entry id
     * @param reportId
     *            the report id (set to 0 for create case)
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result manageReport(Long id, Long reportId) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);
        
     // get the portfolio entry report
        PortfolioEntryReport portfolioEntryReport = new PortfolioEntryReport();

        // initiate the form with the template
        Form<PortfolioEntryReportFormData> reportForm;

        // edit case: inject values
        if (!reportId.equals(0L)) {
            portfolioEntryReport = PortfolioEntryReportDao.getPEReportById(reportId);

            // security: the portfolioEntry must be related to the object
            if (!portfolioEntryReport.portfolioEntry.id.equals(id)) {
                return forbidden(views.html.error.access_forbidden.render(""));
            }

            reportForm = reportFormTemplate.fill(new PortfolioEntryReportFormData(portfolioEntryReport));

            // add the custom attributes values
            this.getCustomAttributeManagerService().fillWithValues(reportForm, PortfolioEntryReport.class, reportId);

        } else {

            reportForm = reportFormTemplate.fill(new PortfolioEntryReportFormData());

            // add the custom attributes default values
            this.getCustomAttributeManagerService().fillWithValues(reportForm, PortfolioEntryReport.class, null);
        }

        // get the selectable portfolioEntry report status types
        DefaultSelectableValueHolderCollection<CssValueForValueHolder> selectablePortfolioEntryReportStatusTypes = PortfolioEntryReportDao
                .getPEReportStatusTypeActiveAsCssVH();
        
        /*
         * Get the attachments
         */

        // authorize the attachments
        FileAttachmentHelper.getFileAttachmentsForDisplay(PortfolioEntryReport.class, reportId, getAttachmentManagerPlugin(), getUserSessionManagerPlugin());

        // create the table
        List<Attachment> attachments = Attachment.getAttachmentsFromObjectTypeAndObjectId(PortfolioEntryReport.class, reportId);

        List<AttachmentListView> attachmentsListView = attachments.stream().map(attachment -> new AttachmentListView(attachment,
                routes.PortfolioEntryStatusReportingController.deleteReportAttachment(id, reportId, attachment.id).url())).collect(Collectors.toList());

        Set<String> hideColumns = new HashSet<>();
        if (!getSecurityService().dynamic("PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION", "")) {
            hideColumns.add("removeActionLink");
        }
        
        Table<AttachmentListView> attachmentsTable = this.getTableProvider().get().attachment.templateTable.fill(attachmentsListView, hideColumns);

        return ok(views.html.core.portfolioentrystatusreporting.report_manage.render(portfolioEntry, selectablePortfolioEntryReportStatusTypes, reportForm, portfolioEntryReport, attachmentsTable));
    }

    /**
     * Perform the save for a new/update report.
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processManageReport() {

        // bind the form
        Form<PortfolioEntryReportFormData> boundForm = reportFormTemplate.bindFromRequest();

        // get the portfolioEntry
        Long id = Long.valueOf(boundForm.data().get("id"));
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        if (boundForm.hasErrors() || this.getCustomAttributeManagerService().validateValues(boundForm, PortfolioEntryReport.class)) {

            // get the selectable portfolioEntry report status types
            DefaultSelectableValueHolderCollection<CssValueForValueHolder> selectablePortfolioEntryReportStatusTypes = PortfolioEntryReportDao
                    .getPEReportStatusTypeActiveAsCssVH();

            return ok(
                    views.html.core.portfolioentrystatusreporting.report_manage.render(portfolioEntry, selectablePortfolioEntryReportStatusTypes, boundForm, null, null));
        }

        PortfolioEntryReportFormData portfolioEntryReportFormData = boundForm.get();

        PortfolioEntryReport portfolioEntryReport;

        // create case
        if (portfolioEntryReportFormData.reportId == null) {

            portfolioEntryReport = new PortfolioEntryReport();
            portfolioEntryReport.portfolioEntry = portfolioEntry;
            portfolioEntryReport.isPublished = true;
            portfolioEntryReport.creationDate = new Date();
            portfolioEntryReport.publicationDate = portfolioEntryReport.creationDate;

            // get the current actor
            String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());
            Actor actor = ActorDao.getActorByUid(uid);
            if (actor == null) {
                return redirect(controllers.dashboard.routes.DashboardController.index(0, false));
            }

            portfolioEntryReport.author = actor;

            portfolioEntryReportFormData.fill(portfolioEntryReport);

            portfolioEntryReport.save();

            portfolioEntry.lastPortfolioEntryReport = portfolioEntryReport;
            portfolioEntry.save();

            // if exists, add the document
            if (FileAttachmentHelper.hasFileField("document")) {
                Logger.debug("has document");
                try {
                    FileAttachmentHelper.saveAsAttachement("document", PortfolioEntryReport.class, portfolioEntryReport.id, getAttachmentManagerPlugin());
                } catch (IOException e) {
                    Logger.error("impossible to add the document for the created report '" + portfolioEntryReport.id + "'. The report still created.", e);
                }
            }

            Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_status_reporting.report.add.successful"));

        } else { // edit case

            portfolioEntryReport = PortfolioEntryReportDao.getPEReportById(portfolioEntryReportFormData.reportId);

            // security: the portfolioEntry must be related to the object
            if (!portfolioEntryReport.portfolioEntry.id.equals(id)) {
                return forbidden(views.html.error.access_forbidden.render(""));
            }

            portfolioEntryReportFormData.fill(portfolioEntryReport);
            portfolioEntryReport.update();

            Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_status_reporting.report.edit.successful"));
        }

        // save the custom attributes
        this.getCustomAttributeManagerService().validateAndSaveValues(boundForm, PortfolioEntryReport.class, portfolioEntryReport.id);

        return redirect(controllers.core.routes.PortfolioEntryStatusReportingController.registers(portfolioEntryReportFormData.id, 0, 0, 0, false, false));
    }

    /**
     * Delete a report.
     * 
     * @param id
     *            the portfolio entry id
     * @param reportId
     *            the report id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result deleteReport(Long id, Long reportId) {

        // get the portfolio entry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the report
        PortfolioEntryReport portfolioEntryReport = PortfolioEntryReportDao.getPEReportById(reportId);

        // security: the portfolioEntry must be related to the object
        if (!portfolioEntryReport.portfolioEntry.id.equals(id)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        // set the delete flag to true
        portfolioEntryReport.doDelete();

        // if necessary update the last report of the PE
        if (portfolioEntry.lastPortfolioEntryReport.id.equals(portfolioEntryReport.id)) {
            portfolioEntry.lastPortfolioEntryReport = PortfolioEntryReportDao.getPEReportAsLastByPE(id);
            portfolioEntry.save();
        }

        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_status_reporting.report.delete.successful"));

        return redirect(controllers.core.routes.PortfolioEntryStatusReportingController.registers(id, 0, 0, 0, false, false));

    }

    /**
     * Delete a risk.
     *
     * @param id
     *            the portfolio entry id
     * @param riskId
     *            the risk id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result deleteRisk(Long id, Long riskId) {

        // get the portfolioEntry risk
        PortfolioEntryRisk portfolioEntryRisk = PortfolioEntryRiskAndIssueDao.getPERiskById(riskId);

        // security: the portfolioEntry must be related to the object
        if (!portfolioEntryRisk.portfolioEntry.id.equals(id)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        portfolioEntryRisk.doDelete();

        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_status_reporting.event.delete.successful"));
        return redirect(controllers.core.routes.PortfolioEntryStatusReportingController.registers(id, 0, 0, 0, false, false));
    }

    /**
     * Delete an issue.
     *
     * @param id
     *            the portfolio entry id
     * @param issueId
     *            the issue id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result deleteIssue(Long id, Long issueId) {

        // get the portfolioEntry issue
        PortfolioEntryIssue portfolioEntryIssue = PortfolioEntryRiskAndIssueDao.getPEIssueById(issueId);

        // security: the portfolioEntry must be related to the object
        if (!portfolioEntryIssue.portfolioEntry.id.equals(id)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        portfolioEntryIssue.doDelete();

        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_status_reporting.event.delete.successful"));
        return redirect(controllers.core.routes.PortfolioEntryStatusReportingController.registers(id, 0, 0, 0, false, false));
    }

    /**
     * Form to add an attachment to a report.
     * 
     * @param id
     *            the portfolio entry id
     * @param reportId
     *            the report id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result createReportAttachment(Long id, Long reportId) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the report
        PortfolioEntryReport report = PortfolioEntryReportDao.getPEReportById(reportId);

        // construct the form
        Form<AttachmentFormData> attachmentForm = attachmentFormTemplate.fill(new AttachmentFormData());

        return ok(views.html.core.portfolioentrystatusreporting.report_attachment_create.render(portfolioEntry, report, attachmentForm));

    }

    /**
     * Process the form to add an attachment to a report.
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processCreateReportAttachment() {

        Form<AttachmentFormData> boundForm = attachmentFormTemplate.bindFromRequest();

        // get the portfolioEntry
        Long id = Long.valueOf(boundForm.data().get("id"));
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the report
        Long reportId = Long.valueOf(boundForm.data().get("objectId"));
        PortfolioEntryReport report = PortfolioEntryReportDao.getPEReportById(reportId);

        if (boundForm.hasErrors()) {
            return ok(views.html.core.portfolioentrystatusreporting.report_attachment_create.render(portfolioEntry, report, boundForm));
        }

        // store the document
        try {
            FileAttachmentHelper.saveAsAttachement("document", PortfolioEntryReport.class, report.id, getAttachmentManagerPlugin());
        } catch (Exception e) {
            return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());
        }

        // success message
        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry.attachment.new.successful"));

        return redirect(controllers.core.routes.PortfolioEntryStatusReportingController.viewReport(portfolioEntry.id, report.id));
    }

    /**
     * Delete an attachment of a report.
     * 
     * @param id
     *            the portfolio entry id
     * @param reportId
     *            the report id
     * @param attachmentId
     *            the attachment id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result deleteReportAttachment(Long id, Long reportId, Long attachmentId) {

        // get the report
        PortfolioEntryReport report = PortfolioEntryReportDao.getPEReportById(reportId);

        // get the attachment
        Attachment attachment = Attachment.getAttachmentFromId(attachmentId);

        // security: the report must be related to the attachment, and
        // the portfolio entry to the report
        if (!attachment.objectId.equals(reportId) || !report.portfolioEntry.id.equals(id)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        // delete the attachment
        FileAttachmentHelper.deleteFileAttachment(attachmentId, getAttachmentManagerPlugin(), getUserSessionManagerPlugin());

        attachment.doDelete();

        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry.attachment.delete"));

        return redirect(controllers.core.routes.PortfolioEntryStatusReportingController.viewReport(id, reportId));

    }

    /**
     * Display the details of a portfolio entry risk.
     *
     * @param id
     *            the portfolio entry id
     * @param riskId
     *            the risk/issue id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result viewRisk(Long id, Long riskId) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the portfolioEntry risk
        PortfolioEntryRisk portfolioEntryRisk = PortfolioEntryRiskAndIssueDao.getPERiskById(riskId);

        // construct the corresponding form data (for the custom attributes)
        PortfolioEntryRiskFormData portfolioEntryRiskFormData = new PortfolioEntryRiskFormData(portfolioEntryRisk);

        // security: the portfolioEntry must be related to the object
        if (!portfolioEntryRisk.portfolioEntry.id.equals(id)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        return ok(views.html.core.portfolioentrystatusreporting.risk_view.render(portfolioEntry, portfolioEntryRisk, portfolioEntryRiskFormData));
    }

    /**
     * Display the details of a portfolio entry issue.
     *
     * @param id
     *            the portfolio entry id
     * @param issueId
     *            the issue id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result viewIssue(Long id, Long issueId) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the portfolioEntry issue
        PortfolioEntryIssue portfolioEntryIssue = PortfolioEntryRiskAndIssueDao.getPEIssueById(issueId);

        // construct the corresponding form data (for the custom attributes)
        PortfolioEntryIssueFormData portfolioEntryIssueFormData = new PortfolioEntryIssueFormData(portfolioEntryIssue);

        // security: the portfolioEntry must be related to the object
        if (!portfolioEntryIssue.portfolioEntry.id.equals(id)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        return ok(views.html.core.portfolioentrystatusreporting.issue_view.render(portfolioEntry, portfolioEntryIssue, portfolioEntryIssueFormData));
    }

    /**
     * Form to create/edit a risk.
     *
     * @param id
     *            the portfolio entry id
     * @param riskId
     *            the risk id (set to 0 for create case)
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result manageRisk(Long id, Long riskId) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // initiate the form with the template
        Form<PortfolioEntryRiskFormData> riskForm;

        // initiate the form data
        PortfolioEntryRiskFormData portfolioEntryRiskFormData;

        // edit case: get the portfolioEntry risk instance
        if (!riskId.equals(0L)) {
            PortfolioEntryRisk portfolioEntryRisk = PortfolioEntryRiskAndIssueDao.getPERiskById(riskId);

            // security: the portfolioEntry must be related to the object
            if (!portfolioEntryRisk.portfolioEntry.id.equals(id)) {
                return forbidden(views.html.error.access_forbidden.render(""));
            }
            portfolioEntryRiskFormData = new PortfolioEntryRiskFormData(portfolioEntryRisk);

        } else { // create case: set default values
            portfolioEntryRiskFormData = new PortfolioEntryRiskFormData();
            portfolioEntryRiskFormData.isActive = true;
        }

        riskForm = riskFormTemplate.fill(portfolioEntryRiskFormData);

        this.getCustomAttributeManagerService().fillWithValues(riskForm, PortfolioEntryRisk.class, riskId.equals(0L) ? null : riskId);

        return ok(views.html.core.portfolioentrystatusreporting.risk_manage.render(portfolioEntry, riskForm, PortfolioEntryRiskAndIssueDao.getPERiskTypeActiveAsVH()));
    }

    /**
     * Form to create/edit a issue.
     *
     * @param id
     *            the portfolio entry id
     * @param issueId
     *            the issue id (set to 0 for create case)
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result manageIssue(Long id, Long issueId) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // initiate the form with the template
        Form<PortfolioEntryIssueFormData> issueForm;

        // initiate the form data
        PortfolioEntryIssueFormData portfolioEntryIssueFormData;

        // edit case: get the portfolioEntry issue instance
        if (!issueId.equals(0L)) {
            PortfolioEntryIssue portfolioEntryIssue = PortfolioEntryRiskAndIssueDao.getPEIssueById(issueId);

            // security: the portfolioEntry must be related to the object
            if (!portfolioEntryIssue.portfolioEntry.id.equals(id)) {
                return forbidden(views.html.error.access_forbidden.render(""));
            }

            portfolioEntryIssueFormData = new PortfolioEntryIssueFormData(portfolioEntryIssue);

        } else { // create case: set default values
            portfolioEntryIssueFormData = new PortfolioEntryIssueFormData();
            portfolioEntryIssueFormData.isActive = true;
        }

        issueForm = issueFormTemplate.fill(portfolioEntryIssueFormData);

        this.getCustomAttributeManagerService().fillWithValues(issueForm, PortfolioEntryRisk.class, issueId.equals(0L) ? null : issueId);

        return ok(views.html.core.portfolioentrystatusreporting.issue_manage.render(portfolioEntry, issueForm, PortfolioEntryRiskAndIssueDao.getPEIssueTypeActiveAsVH()));
    }

    /**
     * Perform the save for a new/update risk.
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processManageRisk() {

        // bind the form
        Form<PortfolioEntryRiskFormData> boundForm = riskFormTemplate.bindFromRequest();

        // get the portfolioEntry
        Long id = Long.valueOf(request().body().asFormUrlEncoded().get("id")[0]);
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        if (boundForm.hasErrors() || this.getCustomAttributeManagerService().validateValues(boundForm, PortfolioEntryRisk.class)) {
            return ok(views.html.core.portfolioentrystatusreporting.risk_manage.render(portfolioEntry, boundForm,
                    PortfolioEntryRiskAndIssueDao.getPERiskTypeActiveAsVH()));
        }

        PortfolioEntryRiskFormData portfolioEntryRiskFormData = boundForm.get();

        PortfolioEntryRisk portfolioEntryRisk;

        // create case
        if (portfolioEntryRiskFormData.riskId == null) {

            portfolioEntryRisk = new PortfolioEntryRisk();
            portfolioEntryRisk.creationDate = new Date();
            portfolioEntryRisk.portfolioEntry = portfolioEntry;

            portfolioEntryRiskFormData.fill(portfolioEntryRisk);

            portfolioEntryRisk.save();

            Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_status_reporting.risk.add.successful"));

        } else { // edit case

            portfolioEntryRisk = PortfolioEntryRiskAndIssueDao.getPERiskById(portfolioEntryRiskFormData.riskId);

            // security: the portfolioEntry must be related to the object
            if (!portfolioEntryRisk.portfolioEntry.id.equals(id)) {
                return forbidden(views.html.error.access_forbidden.render(""));
            }

            portfolioEntryRiskFormData.fill(portfolioEntryRisk);

            portfolioEntryRisk.update();

            Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_status_reporting.risk.edit.successful"));
        }

        // save the custom attributes
        this.getCustomAttributeManagerService().validateAndSaveValues(boundForm, PortfolioEntryRisk.class, portfolioEntryRisk.id);

        return redirect(controllers.core.routes.PortfolioEntryStatusReportingController.registers(portfolioEntryRiskFormData.id, 0, 0, 0, false, false));
    }

    /**
     * Perform the save for a new/update issue.
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processManageIssue() {

        // bind the form
        Form<PortfolioEntryIssueFormData> boundForm = issueFormTemplate.bindFromRequest();

        // get the portfolioEntry
        Long id = Long.valueOf(request().body().asFormUrlEncoded().get("id")[0]);
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        if (boundForm.hasErrors() || this.getCustomAttributeManagerService().validateValues(boundForm, PortfolioEntryIssue.class)) {
            return ok(views.html.core.portfolioentrystatusreporting.issue_manage.render(portfolioEntry, boundForm,
                    PortfolioEntryRiskAndIssueDao.getPEIssueTypeActiveAsVH()));
        }

        PortfolioEntryIssueFormData portfolioEntryIssueFormData = boundForm.get();

        PortfolioEntryIssue portfolioEntryIssue;

        // create case
        if (portfolioEntryIssueFormData.issueId == null) {

            portfolioEntryIssue = new PortfolioEntryIssue();
            portfolioEntryIssue.creationDate = new Date();
            portfolioEntryIssue.portfolioEntry = portfolioEntry;

            portfolioEntryIssueFormData.fill(portfolioEntryIssue);

            portfolioEntryIssue.save();

            Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_status_reporting.issue.add.successful"));

        } else { // edit case

            portfolioEntryIssue = PortfolioEntryRiskAndIssueDao.getPEIssueById(portfolioEntryIssueFormData.issueId);

            // security: the portfolioEntry must be related to the object
            if (!portfolioEntryIssue.portfolioEntry.id.equals(id)) {
                return forbidden(views.html.error.access_forbidden.render(""));
            }

            portfolioEntryIssueFormData.fill(portfolioEntryIssue);
            portfolioEntryIssue.update();

            Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_status_reporting.issue.edit.successful"));

        }

        // save the custom attributes
        this.getCustomAttributeManagerService().validateAndSaveValues(boundForm, PortfolioEntryIssue.class, portfolioEntryIssue.id);

        return redirect(controllers.core.routes.PortfolioEntryStatusReportingController.registers(portfolioEntryIssueFormData.id, 0, 0, 0, false, false));
    }

    /**
     * Form to edit/create a new event for a portfolio entry.
     * 
     * @param id
     *            the portfolio entry id
     * @param eventId
     *            the event id (set to 0 for create case)
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result manageEvent(Long id, Long eventId) {

        // get the portfolioEntry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // initiate the form with the template
        Form<PortfolioEntryEventFormData> eventForm = eventFormTemplate;

        // edit case: get the portfolio entry event instance
        if (!eventId.equals(0L)) {

            PortfolioEntryEvent portfolioEntryEvent = PortfolioEntryEventDao.getPEEventById(eventId);

            // security: the portfolio entry must be related to the object
            if (!portfolioEntryEvent.portfolioEntry.id.equals(id)) {
                return forbidden(views.html.error.access_forbidden.render(""));
            }

            eventForm = eventFormTemplate.fill(new PortfolioEntryEventFormData(portfolioEntryEvent));

            // add the custom attributes values
            this.getCustomAttributeManagerService().fillWithValues(eventForm, PortfolioEntryEvent.class, eventId);

        } else {
            // add the custom attributes default values
            this.getCustomAttributeManagerService().fillWithValues(eventForm, PortfolioEntryEvent.class, null);
        }

        return ok(views.html.core.portfolioentrystatusreporting.event_manage.render(portfolioEntry, eventForm,
                PortfolioEntryEventDao.getPEEventTypeActiveAsVH(false)));
    }

    /**
     * Perform the save for a new/update event.
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processManageEvent() {

        // bind the form
        Form<PortfolioEntryEventFormData> boundForm = eventFormTemplate.bindFromRequest();

        // get the portfolioEntry
        Long id = Long.valueOf(request().body().asFormUrlEncoded().get("id")[0]);
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        if (boundForm.hasErrors() || this.getCustomAttributeManagerService().validateValues(boundForm, PortfolioEntryEvent.class)) {
            return ok(views.html.core.portfolioentrystatusreporting.event_manage.render(portfolioEntry, boundForm,
                    PortfolioEntryEventDao.getPEEventTypeActiveAsVH(false)));
        }

        PortfolioEntryEventFormData portfolioEntryEventFormData = boundForm.get();

        PortfolioEntryEvent portfolioEntryEvent;

        // create case
        if (portfolioEntryEventFormData.eventId == null) {

            portfolioEntryEvent = new PortfolioEntryEvent();
            portfolioEntryEvent.creationDate = new Date();
            portfolioEntryEvent.portfolioEntry = portfolioEntry;

            // try to get the current actor
            try {

                String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());
                portfolioEntryEvent.actor = ActorDao.getActorByUid(uid);
            } catch (Exception e) {
                return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());
            }

            portfolioEntryEventFormData.fill(portfolioEntryEvent);
            portfolioEntryEvent.save();

            Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_status_reporting.event.add.successful"));

        } else { // edit case

            portfolioEntryEvent = PortfolioEntryEventDao.getPEEventById(portfolioEntryEventFormData.eventId);

            // security: the portfolio entry must be related to the object
            if (!portfolioEntryEvent.portfolioEntry.id.equals(id)) {
                return forbidden(views.html.error.access_forbidden.render(""));
            }

            portfolioEntryEventFormData.fill(portfolioEntryEvent);
            portfolioEntryEvent.update();

            Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_status_reporting.event.edit.successful"));

        }

        // save the custom attributes
        this.getCustomAttributeManagerService().validateAndSaveValues(boundForm, PortfolioEntryEvent.class, portfolioEntryEvent.id);

        return redirect(controllers.core.routes.PortfolioEntryStatusReportingController.events(portfolioEntryEventFormData.id));
    }

    /**
     * Delete an event of a portfolio entry.
     * 
     * @param id
     *            the portfolio entry id
     * @param eventId
     *            the event id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result deleteEvent(Long id, Long eventId) {

        // get the event
        PortfolioEntryEvent event = PortfolioEntryEventDao.getPEEventById(eventId);

        // security: the portfolio entry must be related to the object
        if (!event.portfolioEntry.id.equals(id)) {
            return forbidden(views.html.error.access_forbidden.render(""));
        }

        // set the delete flag to true
        event.doDelete();

        Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry_status_reporting.event.delete.successful"));

        return redirect(controllers.core.routes.PortfolioEntryStatusReportingController.events(id));

    }

    /**
     * Display the list of timesheet logs.
     * 
     * @param id
     *            the portfolio entry id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result timesheets(Long id) {

        // get the portfolio entry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        try {

            // get the filter config
            String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());
            FilterConfig<TimesheetLogListView> filterConfig = this.getTableProvider().get().timesheetLog.filterConfig.getCurrent(uid, request());

            // get the table
            Pair<Table<TimesheetLogListView>, Pagination<TimesheetLog>> t = getTimesheetsTable(id, filterConfig);

            // get the syndicated timesheets
            List<DataSyndicationAgreementLink> agreementLinks = new ArrayList<>();
            DataSyndicationAgreementItem agreementItem = null;
            if (portfolioEntry.isSyndicated && dataSyndicationService.isActive()) {
                try {
                    agreementItem = dataSyndicationService.getAgreementItemByDataTypeAndDescriptor(PortfolioEntry.class.getName(), "TIMESHEET");
                    if (agreementItem != null) {
                        agreementLinks = dataSyndicationService.getAgreementLinksOfItemAndSlaveObject(agreementItem, PortfolioEntry.class.getName(), id);
                    }
                } catch (Exception e) {
                    Logger.error("impossible to get the syndicated timesheet data", e);
                }
            }

            return ok(views.html.core.portfolioentrystatusreporting.timesheets.render(portfolioEntry, t.getLeft(), t.getRight(), filterConfig, agreementLinks,
                    agreementItem));

        } catch (Exception e) {

            return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());

        }
    }

    /**
     * Delete an attachment
     *
     * @param id the attachment id
     */
    //@With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result deleteAttachment(Long id, Long attachmentId) {

        // get the attachment
		Attachment attachment = Attachment.getAttachmentFromId(attachmentId);
		
		// delete the attachment
		FileAttachmentHelper.deleteFileAttachment(attachmentId, getAttachmentManagerPlugin(), getUserSessionManagerPlugin());
		
		attachment.doDelete();
		
		Utilities.sendSuccessFlashMessage(Msg.get("core.portfolio_entry.attachment.delete"));
		
		return docs(id);
    }
    
    /**
     * Filter the attachments files.
     * 
     * @param id
     *            the portfolio entry id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result docsFilter(Long id) {

        try {

            // get the filter config
            String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());
            FilterConfig<PortfolioEntryAttachmentListView> filterConfig = this.getTableProvider().get().docsTableDefinition.filterConfig.persistCurrentInDefault(uid, request());

            if (filterConfig == null) {
                return ok(views.html.framework_views.parts.table.dynamic_tableview_no_more_compatible.render());
            } else {

                // get the table
                Pair<Table<PortfolioEntryAttachmentListView>, Pagination<Attachment>> t = getDocsTable(id, filterConfig);

                return ok(views.html.framework_views.parts.table.dynamic_tableview.render(t.getLeft(), t.getRight()));

            }

        } catch (Exception e) {
            return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());
        }
    }
    
    /**
     * Display the list of attachments.
     * 
     * @param id
     *            the portfolio entry id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result docs(Long id) {

        try {

            // get the filter config
            String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());
            FilterConfig<PortfolioEntryAttachmentListView> filterConfig = this.getTableProvider().get().docsTableDefinition.filterConfig.getCurrent(uid, request());

            // get the table
            Pair<Table<PortfolioEntryAttachmentListView>, Pagination<Attachment>> t = getDocsTable(id,filterConfig);

            //
            return ok(views.html.core.portfolioentrystatusreporting.docs.render(PortfolioEntryDao.getPEById(id), t.getLeft(), t.getRight(), filterConfig));

        } catch (Exception e) {

            return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());

        }
    }
    
    private List<Attachment> getAttachmentsByPE(Long portfolioEntryId, ExpressionList<Attachment> expressionList)
    {
    	List<PortfolioEntryReport> perList = PortfolioEntryReportDao.getPEReportAsListByPE(portfolioEntryId);
    	List<LifeCycleMilestoneInstance> lcmiList = LifeCycleMilestoneDao.findLifeCycleMilestoneInstance.where()
                .eq("deleted", false)
                .eq("lifeCycleInstance.portfolioEntry.id", portfolioEntryId)
                .eq("lifeCycleInstance.isActive", true)
                .findList();
    	List<PortfolioEntryPlanningPackage> pkgList = PortfolioEntryPlanningPackageDao.getPEPlanningPackageAsListByPE(portfolioEntryId);

    	expressionList.where()
		.disjunction()
			.conjunction()
				.eq("object_type","models.pmo.PortfolioEntryReport")
				.in("object_id", perList.stream().map(PortfolioEntryReport::getId).collect(Collectors.toList()))
			.endJunction()  
			.conjunction()
				.eq("object_type","models.pmo.PortfolioEntryPlanningPackage")
				.in("object_id", pkgList.stream().map(PortfolioEntryPlanningPackage::getId).collect(Collectors.toList()))
			.endJunction()
			.conjunction()
				.eq("object_type","models.governance.LifeCycleMilestoneInstance")
				.in("object_id", lcmiList.stream().map(LifeCycleMilestoneInstance::getId).collect(Collectors.toList()))
			.endJunction()    	
			.conjunction()
				.eq("object_type","models.pmo.PortfolioEntry")
				.eq("object_id", portfolioEntryId)
			.endJunction()
		.endJunction();
    	
    	return expressionList.findList();
    }
    
    
    /**
     * Get the attachment management table.
     *
     * @param filterConfig the table filter configuration
     */
    private Pair<Table<PortfolioEntryAttachmentListView>, Pagination<Attachment>> getDocsTable(Long portfolioEntryId,FilterConfig<PortfolioEntryAttachmentListView> filterConfig) {
    	ExpressionList<Attachment> expressionList = filterConfig.updateWithSearchExpression(Attachment.getAllBusinessObjectsAttachmentsAsExpression());
    	
    	filterConfig.updateWithSortExpression(expressionList);
    	
    	List<Attachment> attachmentsList = getAttachmentsByPE(portfolioEntryId, expressionList);

    	Pagination<Attachment> pagination;
    	List<PortfolioEntryAttachmentListView> attachmentByPEListView;

        pagination = new Pagination<>(getPreferenceManagerPlugin(),attachmentsList.size());
        pagination.setCurrentPage(filterConfig.getCurrentPage());
        attachmentsList = pagination.getEntriesForCurrentPage(attachmentsList);
        attachmentByPEListView = attachmentsList.stream().map((attachment) -> {
            if (getSecurityService().dynamic(IMafConstants.PORTFOLIO_ENTRY_VIEW_DYNAMIC_PERMISSION, "")) {
                FileAttachmentHelper.authorizeFileAttachementForDisplay(attachment.id, getUserSessionManagerPlugin());
                return new PortfolioEntryAttachmentListView(portfolioEntryId, attachment);
            }
            return null;
        }).collect(Collectors.toList());

        Set<String> columnsToHide = filterConfig.getColumnsToHide();
        if (!columnsToHide.contains("portfolioEntryId")) {
            columnsToHide.add("portfolioEntryId");
        }
        Table<PortfolioEntryAttachmentListView> table = getTableProvider().get().docsTableDefinition.templateTable.fillForFilterConfig(attachmentByPEListView, columnsToHide);

        return Pair.of(table, pagination);
    }


    /**
     * Filter the timesheet logs.
     * 
     * @param id
     *            the portfolio entry id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Result timesheetsFilter(Long id) {

        try {

            // get the filter config
            String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());
            FilterConfig<TimesheetLogListView> filterConfig = this.getTableProvider().get().timesheetLog.filterConfig.persistCurrentInDefault(uid, request());

            if (filterConfig == null) {
                return ok(views.html.framework_views.parts.table.dynamic_tableview_no_more_compatible.render());
            } else {

                // get the table
                Pair<Table<TimesheetLogListView>, Pagination<TimesheetLog>> t = getTimesheetsTable(id, filterConfig);

                return ok(views.html.framework_views.parts.table.dynamic_tableview.render(t.getLeft(), t.getRight()));

            }

        } catch (Exception e) {
            return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());
        }
    }

    /**
     * Export the content of the current timesheet logs table as Excel.
     * 
     * @param id
     *            the portfolio entry id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(IMafConstants.PORTFOLIO_ENTRY_DETAILS_DYNAMIC_PERMISSION)
    public Promise<Result> exportTimesheetsAsExcel(final Long id) {

        return Promise.promise(() -> {

            try {

                // Get the current user
                final String uid = getUserSessionManagerPlugin().getUserSessionId(ctx());

                // construct the table
                FilterConfig<TimesheetLogListView> filterConfig = getTableProvider().get().timesheetLog.filterConfig.getCurrent(uid, request());

                ExpressionList<TimesheetLog> expressionList = filterConfig
                        .updateWithSearchExpression(TimesheetDao.getTimesheetLogAsExprByPortfolioEntry(id));
                filterConfig.updateWithSortExpression(expressionList);

                List<TimesheetLogListView> timesheetLogListView = new ArrayList<TimesheetLogListView>();
                for (TimesheetLog timesheetLog : expressionList.findList()) {
                    timesheetLogListView.add(new TimesheetLogListView(timesheetLog));
                }

                Table<TimesheetLogListView> table = getTableProvider().get().timesheetLog.templateTable.fillForFilterConfig(timesheetLogListView,
                        filterConfig.getColumnsToHide());

                final byte[] excelFile = TableExcelRenderer.renderFormatted(table);

                final String fileName = String.format("timesheetsExport_%1$td_%1$tm_%1$ty_%1$tH-%1$tM-%1$tS.xlsx", new Date());
                final String successTitle = Msg.get("excel.export.success.title");
                final String successMessage = Msg.get("excel.export.success.message", fileName);
                final String failureTitle = Msg.get("excel.export.failure.title");
                final String failureMessage = Msg.get("excel.export.failure.message");

                // Execute asynchronously
                getSysAdminUtils().scheduleOnce(false, "Timesheets Excel Export", Duration.create(0, TimeUnit.MILLISECONDS), new Runnable() {
                    @Override
                    public void run() {

                        try {
                            OutputStream out = getPersonalStoragePlugin().createNewFile(uid, fileName);
                            IOUtils.copy(new ByteArrayInputStream(excelFile), out);
                            getNotificationManagerPlugin().sendNotification(uid, NotificationCategory.getByCode(Code.DOCUMENT), successTitle,
                                    successMessage, controllers.my.routes.MyPersonalStorage.index().url());
                        } catch (IOException e) {
                            log.error("Unable to export the excel file", e);
                            getNotificationManagerPlugin().sendNotification(uid, NotificationCategory.getByCode(Code.ISSUE), failureTitle, failureMessage,
                                    routes.PortfolioEntryStatusReportingController.timesheets(id).url());
                        }
                    }
                });

                return ok(Json.newObject());

            } catch (Exception e) {
                return ControllersUtils.logAndReturnUnexpectedError(e, log, getConfiguration(), getI18nMessagesPlugin());
            }
        });

    }

    /**
     * Get the timesheet entries table for a portfolio entry and a filter
     * config.
     * 
     * @param portfolioEntryId
     *            the portfolio entry id
     * @param filterConfig
     *            the filter config.
     */
    private Pair<Table<TimesheetLogListView>, Pagination<TimesheetLog>> getTimesheetsTable(Long portfolioEntryId,
            FilterConfig<TimesheetLogListView> filterConfig) {

        ExpressionList<TimesheetLog> expressionList = filterConfig
                .updateWithSearchExpression(TimesheetDao.getTimesheetLogAsExprByPortfolioEntry(portfolioEntryId));
        filterConfig.updateWithSortExpression(expressionList);

        Pagination<TimesheetLog> pagination = new Pagination<TimesheetLog>(this.getPreferenceManagerPlugin(), expressionList);
        pagination.setCurrentPage(filterConfig.getCurrentPage());

        List<TimesheetLogListView> timesheetLogListView = pagination.getListOfObjects().stream().map(TimesheetLogListView::new).collect(Collectors.toList());

        Set<String> columnsToHide = filterConfig.getColumnsToHide();

        Table<TimesheetLogListView> table = this.getTableProvider().get().timesheetLog.templateTable.fillForFilterConfig(timesheetLogListView, columnsToHide);

        return Pair.of(table, pagination);

    }

    /**
     * Get the template name and format for the status report.
     *
     * It is represented by the standard report in PDF if the corresponding
     * preference is empty, or a custom report else.
     */
    private Pair<String, Format> getReportStatusTemplateNameAndFormat() {
        String customTemplateNameAndFormat = getPreferenceManagerPlugin()
                .getPreferenceValueAsString(IMafConstants.CUSTOM_REPORT_TEMPLATE_FOR_STATUS_REPORT_PREFERENCE);
        if (customTemplateNameAndFormat == null || customTemplateNameAndFormat.equals("")) {
            return null;
        } else {
            String[] tmp = customTemplateNameAndFormat.split(",");
            return Pair.of(tmp[0], Format.valueOf(tmp[1]));
        }
    }

    /**
     * Get the notification manager service.
     */
    private INotificationManagerPlugin getNotificationManagerPlugin() {
        return notificationManagerPlugin;
    }

    /**
     * Get the personal storage service.
     */
    private IPersonalStoragePlugin getPersonalStoragePlugin() {
        return personalStoragePlugin;
    }

    /**
     * Get the user session manager service.
     */
    private IUserSessionManagerPlugin getUserSessionManagerPlugin() {
        return userSessionManagerPlugin;
    }

    /**
     * Get the preference manager service.
     */
    private IPreferenceManagerPlugin getPreferenceManagerPlugin() {
        return preferenceManagerPlugin;
    }

    /**
     * Get the reporting utils.
     */
    private IReportingUtils getReportingUtils() {
        return reportingUtils;
    }

    /**
     * Get the system admin utils.
     */
    private ISysAdminUtils getSysAdminUtils() {
        return sysAdminUtils;
    }

    /**
     * Get the i18n messages service.
     */
    private II18nMessagesPlugin getI18nMessagesPlugin() {
        return i18nMessagesPlugin;
    }

    /**
     * Get the attachment manager service.
     */
    private IAttachmentManagerPlugin getAttachmentManagerPlugin() {
        return attachmentManagerPlugin;
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
