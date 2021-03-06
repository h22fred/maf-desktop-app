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
package utils.form;

import framework.services.configuration.II18nMessagesPlugin;
import framework.utils.CustomConstraints.MultiLanguagesStringMaxLength;
import framework.utils.CustomConstraints.MultiLanguagesStringRequired;
import framework.utils.MultiLanguagesString;
import models.framework_models.parent.IModelConstants;
import models.pmo.PortfolioEntryType;

/**
 * A portfolio entry type form data is used to manage the fields when
 * adding/editing a portfolio entry type.
 * 
 * @author Johann Kohler
 */
public class PortfolioEntryTypeFormData {

    public Long id;

    public boolean selectable;

    public boolean isRelease;

    @MultiLanguagesStringRequired
    @MultiLanguagesStringMaxLength(value = IModelConstants.MEDIUM_STRING)
    public MultiLanguagesString name;

    @MultiLanguagesStringMaxLength(value = IModelConstants.VLARGE_STRING)
    public MultiLanguagesString description;

    /**
     * Default constructor.
     */
    public PortfolioEntryTypeFormData() {
    }

    /**
     * Construction with initial value.
     * 
     * @param isRelease
     *            true if the type is a release
     */
    public PortfolioEntryTypeFormData(boolean isRelease) {
        this.isRelease = isRelease;
    }

    /**
     * Construct the form data with a DB entry.
     * 
     * @param portfolioEntryType
     *            the portfolio entry type in the DB
     * @param i18nMessagesPlugin
     *            the i18n manager
     */
    public PortfolioEntryTypeFormData(PortfolioEntryType portfolioEntryType, II18nMessagesPlugin i18nMessagesPlugin) {

        this.id = portfolioEntryType.id;
        this.selectable = portfolioEntryType.selectable;
        this.isRelease = portfolioEntryType.isRelease;
        this.name = MultiLanguagesString.getByKey(portfolioEntryType.name, i18nMessagesPlugin);
        this.description = MultiLanguagesString.getByKey(portfolioEntryType.description, i18nMessagesPlugin);

    }

    /**
     * Fill the DB entry with the form values.
     * 
     * @param portfolioEntryType
     *            the portfolio entry type in the DB
     */
    public void fill(PortfolioEntryType portfolioEntryType) {

        portfolioEntryType.selectable = this.selectable;
        portfolioEntryType.isRelease = this.isRelease;
        portfolioEntryType.name = this.name.getKeyIfValue();
        portfolioEntryType.description = this.description.getKeyIfValue();

    }
}
