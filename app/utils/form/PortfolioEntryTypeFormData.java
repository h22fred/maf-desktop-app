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

import models.pmo.PortfolioEntryType;
import play.data.validation.Constraints.Required;
import play.data.validation.Constraints.ValidateWith;
import framework.services.configuration.II18nMessagesPlugin;
import framework.utils.MultiLanguagesString;
import framework.utils.MultiLanguagesStringValidator;

/**
 * A portfolio entry type form data is used to manage the fields when
 * adding/editing a portfolio entry type.
 * 
 * @author Johann Kohler
 */
public class PortfolioEntryTypeFormData {

    public Long id;

    public boolean selectable;

    @Required
    @ValidateWith(value = MultiLanguagesStringValidator.class, message = "form.input.multi_languages_string.required.error")
    public MultiLanguagesString name;

    public MultiLanguagesString description;

    /**
     * Default constructor.
     */
    public PortfolioEntryTypeFormData() {
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
        portfolioEntryType.name = this.name.getKeyIfValue();
        portfolioEntryType.description = this.description.getKeyIfValue();

    }
}
