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

import java.util.List;

import javax.inject.Inject;

import be.objectify.deadbolt.java.actions.Dynamic;
import dao.pmo.PortfolioEntryDao;
import models.pmo.PortfolioEntry;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import security.CheckPortfolioEntryExists;
import security.DefaultDynamicResourceHandler;
import services.datasyndication.IDataSyndicationService;
import services.datasyndication.models.DataSyndicationAgreement;
import services.datasyndication.models.DataSyndicationAgreementItem;
import services.datasyndication.models.DataSyndicationAgreementLink;

/**
 * The controller which allows to manage the data syndication for a portfolio
 * entry.
 * 
 * @author Johann Kohler
 */
public class PortfolioEntryDataSyndicationController extends Controller {

    @Inject
    private IDataSyndicationService dataSyndicationService;

    /**
     * Display the data syndication agreements.
     * 
     * @param id
     *            the portfolio entry id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(DefaultDynamicResourceHandler.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result index(Long id) {

        // get the portfolio entry
        PortfolioEntry portfolioEntry = PortfolioEntryDao.getPEById(id);

        // get the agreement links as slave (consumer)
        List<DataSyndicationAgreementLink> slaveAgreementLinks = null;
        try {
            slaveAgreementLinks = dataSyndicationService.getAgreementLinksOfSlaveObject(PortfolioEntry.class.getName(), id);
        } catch (Exception e) {
            return ok(views.html.core.portfolioentrydatasyndication.communication_error.render(portfolioEntry));
        }

        // get the agreement links as master
        List<DataSyndicationAgreementLink> masterAgreementLinks = null;
        try {
            masterAgreementLinks = dataSyndicationService.getAgreementLinksOfMasterObject(PortfolioEntry.class.getName(), id);
        } catch (Exception e) {
            return ok(views.html.core.portfolioentrydatasyndication.communication_error.render(portfolioEntry));
        }

        // get the master agreements
        List<DataSyndicationAgreement> masterAgreements = null;
        try {
            masterAgreements = dataSyndicationService.getMasterAgreements();
        } catch (Exception e) {
            return ok(views.html.core.portfolioentrydatasyndication.communication_error.render(portfolioEntry));
        }

        // remove agreements that is not ONGOING or hasn't PE item
        for (DataSyndicationAgreement masterAgreement : masterAgreements) {
            boolean hasPEItem = false;
            for (DataSyndicationAgreementItem item : masterAgreement.items) {
                if (item.dataType.equals(PortfolioEntry.class.getName())) {
                    hasPEItem = true;
                    break;
                }
            }
            if (!masterAgreement.status.equals(DataSyndicationAgreement.Status.ONGOING) || !hasPEItem) {
                masterAgreements.remove(masterAgreement);
            }
        }

        return ok(views.html.core.portfolioentrydatasyndication.index.render(portfolioEntry, slaveAgreementLinks, masterAgreementLinks, masterAgreements));
    }

    /**
     * Display the details of an agreement link with management capabilities.
     * 
     * @param id
     *            the portfolio entry id
     * @param agreementLinkId
     *            the agreement link id
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(DefaultDynamicResourceHandler.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result viewAgreementLink(Long id, Long agreementLinkId) {
        // TODO check right
        return TODO;
    }

    /**
     * Form to submit an agreement link.
     * 
     * @param id
     *            the portfolio entry id
     * @param agreementId
     *            the agreement id (to initialize the form), 0 if not specified
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(DefaultDynamicResourceHandler.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result submitAgreementLink(Long id, Long agreementId) {
        // TODO check right
        return TODO;
    }

    /**
     * Process the form to submit an agreement link.
     */
    @With(CheckPortfolioEntryExists.class)
    @Dynamic(DefaultDynamicResourceHandler.PORTFOLIO_ENTRY_EDIT_DYNAMIC_PERMISSION)
    public Result processSubmitAgreementLink() {
        // TODO check right
        return TODO;
    }

}
