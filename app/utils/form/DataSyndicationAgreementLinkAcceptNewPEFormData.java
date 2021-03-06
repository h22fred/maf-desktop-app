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

import java.util.Date;

import com.avaje.ebean.Ebean;

import controllers.core.PortfolioEntryController;
import dao.governance.LifeCycleProcessDao;
import dao.pmo.ActorDao;
import dao.pmo.PortfolioEntryDao;
import models.framework_models.parent.IModelConstants;
import models.pmo.PortfolioEntry;
import play.data.validation.Constraints.MaxLength;
import play.data.validation.Constraints.MinLength;
import play.data.validation.Constraints.Required;
import services.datasyndication.models.DataSyndicationAgreementLink;

/**
 * Form to accept a PE agreement link with creating a new PE.
 * 
 * @author Johann Kohler
 */
public class DataSyndicationAgreementLinkAcceptNewPEFormData {

    public Long agreementLinkId;

    @Required
    @MinLength(value = 3)
    @MaxLength(value = IModelConstants.MEDIUM_STRING)
    public String name;

    @Required
    public Long managerId;

    @Required
    public Long portfolioEntryTypeId;

    @Required
    public Long lifeCycleProcessId;

    /**
     * Default constructor.
     */
    public DataSyndicationAgreementLinkAcceptNewPEFormData() {
    }

    /**
     * Construct with a default name.
     * 
     * @param name
     *            the portfolio entry name
     */
    public DataSyndicationAgreementLinkAcceptNewPEFormData(String name) {
        this.name = name;
    }

    /**
     * Create and return the corresponding portfolio entry in the DB.
     * 
     * @param agreementLink
     *            the agreement link
     */
    public PortfolioEntry createPorfolioEntry(DataSyndicationAgreementLink agreementLink) {

        Ebean.beginTransaction();
        try {

            PortfolioEntry portfolioEntry = new PortfolioEntry();
            portfolioEntry.isSyndicated = true;
            portfolioEntry.name = this.name;
            portfolioEntry.description = agreementLink.description;
            portfolioEntry.creationDate = new Date();
            portfolioEntry.manager = ActorDao.getActorById(this.managerId);
            portfolioEntry.isPublic = true;
            portfolioEntry.portfolioEntryType = PortfolioEntryDao.getPETypeById(this.portfolioEntryTypeId);
            Integer lastGovernanceId = PortfolioEntryDao.getPEAsLastGovernanceId();
            portfolioEntry.governanceId = lastGovernanceId != null ? String.valueOf(lastGovernanceId + 1) : "1";
            portfolioEntry.erpRefId = "";
            portfolioEntry.save();

            PortfolioEntryController.createLifeCycleProcessTree(portfolioEntry, LifeCycleProcessDao.getLCProcessById(this.lifeCycleProcessId));

            Ebean.commitTransaction();

            return portfolioEntry;

        } catch (Exception e) {
            Ebean.rollbackTransaction();
            throw e;
        }

    }
}
