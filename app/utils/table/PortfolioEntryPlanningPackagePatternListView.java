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

import models.pmo.PortfolioEntryPlanningPackagePattern;
import constants.IMafConstants;
import framework.services.configuration.II18nMessagesPlugin;
import framework.utils.Color;
import framework.utils.IColumnFormatter;
import framework.utils.Msg;
import framework.utils.Table;
import framework.utils.formats.BooleanFormatter;
import framework.utils.formats.ObjectFormatter;
import framework.utils.formats.StringFormatFormatter;

/**
 * A portfolio entry planning package pattern list view is used to display a
 * package pattern row in a table.
 * 
 * @author Johann Kohler
 */
public class PortfolioEntryPlanningPackagePatternListView {
    public static Table<PortfolioEntryPlanningPackagePatternListView> templateTable = new Table<PortfolioEntryPlanningPackagePatternListView>() {
        {

            setIdFieldName("id");

            addColumn("changeOrder", "id", "", Table.ColumnDef.SorterType.NONE);
            setJavaColumnFormatter("changeOrder", new IColumnFormatter<PortfolioEntryPlanningPackagePatternListView>() {
                @Override
                public String apply(PortfolioEntryPlanningPackagePatternListView packagePatternListView, Object value) {
                    return "<a href=\""
                            + controllers.admin.routes.ConfigurationPlanningPackageController.changePackagePatternOrder(packagePatternListView.id, false)
                                    .url()
                            + "\"><span class=\"glyphicons glyphicons-down-arrow\"></span></a>&nbsp;"
                            + "<a href=\""
                            + controllers.admin.routes.ConfigurationPlanningPackageController.changePackagePatternOrder(packagePatternListView.id, true)
                                    .url() + "\"><span class=\"glyphicons glyphicons-up-arrow\"></span></a>";
                }
            });
            setColumnCssClass("changeOrder", IMafConstants.BOOTSTRAP_COLUMN_1);

            addColumn("isImportant", "isImportant", "object.portfolio_entry_planning_package.is_important.label", Table.ColumnDef.SorterType.NONE);
            setJavaColumnFormatter("isImportant", new BooleanFormatter<PortfolioEntryPlanningPackagePatternListView>());

            addColumn("name", "name", "object.portfolio_entry_planning_package.name.label", Table.ColumnDef.SorterType.NONE);
            setJavaColumnFormatter("name", new ObjectFormatter<PortfolioEntryPlanningPackagePatternListView>());

            addColumn("description", "description", "object.portfolio_entry_planning_package.description.label", Table.ColumnDef.SorterType.NONE);
            setJavaColumnFormatter("description", new ObjectFormatter<PortfolioEntryPlanningPackagePatternListView>());

            addColumn("color", "color", "object.portfolio_entry_planning_package.css_class.label", Table.ColumnDef.SorterType.NONE);
            setJavaColumnFormatter("color", new ObjectFormatter<PortfolioEntryPlanningPackagePatternListView>());

            addColumn("editActionLink", "id", "", Table.ColumnDef.SorterType.NONE);
            setJavaColumnFormatter("editActionLink", new StringFormatFormatter<PortfolioEntryPlanningPackagePatternListView>(IMafConstants.EDIT_URL_FORMAT,
                    new StringFormatFormatter.Hook<PortfolioEntryPlanningPackagePatternListView>() {
                        @Override
                        public String convert(PortfolioEntryPlanningPackagePatternListView packagePatternListView) {
                            return controllers.admin.routes.ConfigurationPlanningPackageController.managePackagePattern(
                                    packagePatternListView.packageGroupId, packagePatternListView.id).url();
                        }
                    }));
            setColumnCssClass("editActionLink", IMafConstants.BOOTSTRAP_COLUMN_1);
            setColumnValueCssClass("editActionLink", IMafConstants.BOOTSTRAP_TEXT_ALIGN_RIGHT);

            addColumn("deleteActionLink", "id", "", Table.ColumnDef.SorterType.NONE);
            setJavaColumnFormatter("deleteActionLink", new IColumnFormatter<PortfolioEntryPlanningPackagePatternListView>() {
                @Override
                public String apply(PortfolioEntryPlanningPackagePatternListView packagePatternListView, Object value) {
                    String deleteConfirmationMessage =
                            MessageFormat.format(IMafConstants.DELETE_URL_FORMAT_WITH_CONFIRMATION, Msg.get("default.delete.confirmation.message"));
                    String url = controllers.admin.routes.ConfigurationPlanningPackageController.deletePackagePattern(packagePatternListView.id).url();
                    return views.html.framework_views.parts.formats.display_with_format.render(url, deleteConfirmationMessage).body();
                }
            });
            setColumnCssClass("deleteActionLink", IMafConstants.BOOTSTRAP_COLUMN_1);
            setColumnValueCssClass("deleteActionLink", IMafConstants.BOOTSTRAP_TEXT_ALIGN_RIGHT);

            setEmptyMessageKey("object.portfolio_entry_planning_package.pattern.table.empty");

        }
    };

    /**
     * Default constructor.
     */
    public PortfolioEntryPlanningPackagePatternListView() {
    }

    public Long packageGroupId;
    public Long id;

    public String name;

    public String description;

    public boolean isImportant;

    public String color;

    /**
     * Construct a list view with a DB entry.
     * 
     * @param packagePattern
     *            the package pattern in the DB
     * @param messagesPlugin
     *            the i18n service
     */
    public PortfolioEntryPlanningPackagePatternListView(
            PortfolioEntryPlanningPackagePattern packagePattern,
            II18nMessagesPlugin messagesPlugin) {

        this.id = packagePattern.id;
        this.packageGroupId = packagePattern.portfolioEntryPlanningPackageGroup.id;

        this.name = packagePattern.name;
        this.description = packagePattern.description;
        this.isImportant = packagePattern.isImportant;
        this.color = Color.getLabel(packagePattern.cssClass, messagesPlugin);

    }
}
