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
package utils.table;

import java.text.MessageFormat;

import constants.IMafConstants;
import dao.delivery.DeliverableDAO;
import framework.utils.FilterConfig;
import framework.utils.FilterConfig.SortStatusType;
import framework.utils.IColumnFormatter;
import framework.utils.Msg;
import framework.utils.Table;
import framework.utils.formats.ObjectFormatter;
import framework.utils.formats.StringFormatFormatter;
import models.delivery.Deliverable;
import models.delivery.PortfolioEntryDeliverable;
import models.pmo.PortfolioEntry;
import models.pmo.PortfolioEntryPlanningPackage;

/**
 * A deliverable list view is used to display a delivrable row in a table.
 * 
 * @author Johann Kohler
 */
public class DeliverableListView {

    public static FilterConfig<DeliverableListView> filterConfig = getFilterConfig();

    /**
     * Get the filter config.
     */
    public static FilterConfig<DeliverableListView> getFilterConfig() {
        return new FilterConfig<DeliverableListView>() {
            {

                addColumnConfiguration("name", "deliverable.name", "object.deliverable.name.label", new TextFieldFilterComponent("*"), true, false,
                        SortStatusType.UNSORTED);

                addColumnConfiguration("description", "deliverable.description", "object.deliverable.description.label", new TextFieldFilterComponent("*"),
                        true, false, SortStatusType.UNSORTED);

                // TODO isDelegated

                // TODO owner

                addColumnConfiguration("planningPackage", "portfolioEntryPlanningPackage.name", "object.deliverable.planning_package.label",
                        new TextFieldFilterComponent("*"), true, false, SortStatusType.UNSORTED);

                addCustomAttributesColumns("id", Deliverable.class);

            }
        };
    }

    public static Table<DeliverableListView> templateTable = getTable();

    /**
     * Get the table.
     */
    public static Table<DeliverableListView> getTable() {
        return new Table<DeliverableListView>() {
            {
                setIdFieldName("id");

                addColumn("name", "name", "object.deliverable.name.label", Table.ColumnDef.SorterType.NONE);
                setJavaColumnFormatter("name", new ObjectFormatter<DeliverableListView>());

                addColumn("description", "description", "object.deliverable.description.label", Table.ColumnDef.SorterType.NONE);
                setJavaColumnFormatter("description", new ObjectFormatter<DeliverableListView>());

                addColumn("isDelegated", "isDelegated", "object.deliverable.is_delegated.label", Table.ColumnDef.SorterType.NONE);
                setJavaColumnFormatter("isDelegated", new ObjectFormatter<DeliverableListView>());

                addColumn("owner", "owner", "object.deliverable.owner.label", Table.ColumnDef.SorterType.NONE);
                setJavaColumnFormatter("owner", new IColumnFormatter<DeliverableListView>() {
                    @Override
                    public String apply(DeliverableListView deliverableListView, Object value) {
                        return views.html.modelsparts.display_portfolio_entry.render(deliverableListView.owner, true).body();
                    }
                });
                this.setColumnValueCssClass("owner", "rowlink-skip");

                addColumn("planningPackage", "planningPackage", "object.deliverable.planning_package.label", Table.ColumnDef.SorterType.NONE);
                setJavaColumnFormatter("planningPackage", new IColumnFormatter<DeliverableListView>() {
                    @Override
                    public String apply(DeliverableListView deliverableListView, Object value) {
                        return views.html.modelsparts.display_portfolio_entry_planning_package.render(deliverableListView.planningPackage).body();
                    }
                });
                this.setColumnValueCssClass("planningPackage", "rowlink-skip");

                addCustomAttributeColumns(Deliverable.class);

                addColumn("editActionLink", "id", "", Table.ColumnDef.SorterType.NONE);
                setJavaColumnFormatter("editActionLink",
                        new StringFormatFormatter<DeliverableListView>(IMafConstants.EDIT_URL_FORMAT, new StringFormatFormatter.Hook<DeliverableListView>() {
                    @Override
                    public String convert(DeliverableListView deliverableListView) {
                        return controllers.core.routes.PortfolioEntryDeliveryController
                                .manageDeliverable(deliverableListView.portfolioEntryId, deliverableListView.id).url();
                    }
                }));
                setColumnCssClass("editActionLink", IMafConstants.BOOTSTRAP_COLUMN_1);
                setColumnValueCssClass("editActionLink", IMafConstants.BOOTSTRAP_TEXT_ALIGN_RIGHT + " rowlink-skip");

                addColumn("deleteActionLink", "id", "", Table.ColumnDef.SorterType.NONE);
                setJavaColumnFormatter("deleteActionLink", new IColumnFormatter<DeliverableListView>() {
                    @Override
                    public String apply(DeliverableListView deliverableListView, Object value) {
                        String deleteConfirmationMessage = MessageFormat.format(IMafConstants.DELETE_URL_FORMAT_WITH_CONFIRMATION,
                                Msg.get("default.delete.confirmation.message"));
                        String url = controllers.core.routes.PortfolioEntryDeliveryController
                                .deleteDeliverable(deliverableListView.portfolioEntryId, deliverableListView.id).url();
                        return views.html.framework_views.parts.formats.display_with_format.render(url, deleteConfirmationMessage).body();
                    }
                });
                setColumnCssClass("deleteActionLink", IMafConstants.BOOTSTRAP_COLUMN_1);
                setColumnValueCssClass("deleteActionLink", IMafConstants.BOOTSTRAP_TEXT_ALIGN_RIGHT);

                setEmptyMessageKey("object.deliverable.table.empty");

            }
        };

    }

    /**
     * Default constructor.
     */
    public DeliverableListView() {
    }

    public Long id;

    public Long portfolioEntryId;

    public String name;

    public String description;

    public boolean isDelegated;

    public PortfolioEntry owner;

    public PortfolioEntryPlanningPackage planningPackage;

    /**
     * Construct a list view with a DB entry.
     * 
     * @param portfolioEntryDeliverable
     *            the delivrable relation with a portfolio entry
     */
    public DeliverableListView(PortfolioEntryDeliverable portfolioEntryDeliverable) {

        this.id = portfolioEntryDeliverable.getDeliverable().id;

        this.portfolioEntryId = portfolioEntryDeliverable.getPortfolioEntry().id;

        this.name = portfolioEntryDeliverable.getDeliverable().name;

        this.description = portfolioEntryDeliverable.getDeliverable().description;

        this.isDelegated = !portfolioEntryDeliverable.type.equals(PortfolioEntryDeliverable.Type.OWNER);

        if (portfolioEntryDeliverable.type.equals(PortfolioEntryDeliverable.Type.OWNER)) {
            this.owner = portfolioEntryDeliverable.getPortfolioEntry();
        } else {
            this.owner = DeliverableDAO.getDeliverableOwner(this.id);
        }

        this.planningPackage = portfolioEntryDeliverable.portfolioEntryPlanningPackage;

    }

}