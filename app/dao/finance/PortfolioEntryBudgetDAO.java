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
package dao.finance;

import java.util.List;

import models.finance.PortfolioEntryBudget;
import models.finance.PortfolioEntryBudgetLine;
import models.sql.TotalAmount;
import com.avaje.ebean.Model.Finder;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Query;
import com.avaje.ebean.RawSql;
import com.avaje.ebean.RawSqlBuilder;

import framework.utils.Pagination;

/**
 * DAO for the {@link PortfolioEntryBudget} and {@link PortfolioEntryBudgetLine}
 * objects.
 * 
 * @author Pierre-Yves Cloux
 */
public abstract class PortfolioEntryBudgetDAO {

    public static Finder<Long, PortfolioEntryBudget> findPortfolioEntryBudget = new Finder<>(PortfolioEntryBudget.class);

    public static Finder<Long, PortfolioEntryBudgetLine> findPortfolioEntryBudgetLine = new Finder<>(PortfolioEntryBudgetLine.class);

    /**
     * Default constructor.
     */
    public PortfolioEntryBudgetDAO() {
    }

    /**
     * Get a portfolio entry budget by id.
     * 
     * @param id
     *            the portfolio entry budget id
     */
    public static PortfolioEntryBudget getPEBudgetById(Long id) {
        return PortfolioEntryBudgetDAO.findPortfolioEntryBudget.where().eq("deleted", false).eq("id", id).findUnique();
    }

    /**
     * Get the total amount of a portfolio entry budget (for the default
     * currency).
     * 
     * @param portfolioEntryBudgetId
     *            the portfolio entry budget id
     * @param isOpex
     *            expenditure type
     */
    public static Double getPEBudgetAsAmountById(Long portfolioEntryBudgetId, boolean isOpex) {

        String sql =
                "SELECT SUM(pebl.amount) as totalAmount FROM portfolio_entry_budget_line pebl WHERE pebl.deleted = 0 AND pebl.is_opex =" + isOpex
                        + " AND pebl.currency_code='" + CurrencyDAO.getCurrencyDefault().code + "' AND pebl.portfolio_entry_budget_id = '"
                        + portfolioEntryBudgetId + "'";
        Double totalAmountBudget = Ebean.find(TotalAmount.class).setRawSql(RawSqlBuilder.parse(sql).create()).findUnique().totalAmount;
        if (totalAmountBudget != null) {
            return totalAmountBudget;
        } else {
            return 0.0;
        }
    }

    /**
     * Get budget line by id.
     * 
     * @param id
     *            the budget line id
     */
    public static PortfolioEntryBudgetLine getPEBudgetLineById(Long id) {
        return PortfolioEntryBudgetDAO.findPortfolioEntryBudgetLine.where().eq("deleted", false).eq("id", id).findUnique();
    }

    /**
     * Get the total budget of initiatives in the default currency for a budget
     * bucket.
     * 
     * @param budgetBucketId
     *            the budget bucket id
     * @param isOpex
     *            set to true for OPEX budget, else CAPEX
     */
    public static Double getBudgetAsAmountByBucketAndOpex(Long budgetBucketId, boolean isOpex) {

        String sql =
                "SELECT SUM(pebl.amount) as totalAmount FROM portfolio_entry_budget_line pebl"
                        + " JOIN portfolio_entry_budget peb ON pebl.portfolio_entry_budget_id = peb.id"
                        + " JOIN life_cycle_instance_planning lcip ON peb.id = lcip.portfolio_entry_budget_id"
                        + " JOIN life_cycle_instance lci ON lcip.life_cycle_instance_id = lci.id"
                        + " JOIN portfolio_entry pe ON lci.portfolio_entry_id = pe.id" + " WHERE pebl.deleted = 0 AND pebl.is_opex = " + isOpex
                        + " AND pebl.currency_code = '" + CurrencyDAO.getCurrencyDefault().code + "' AND pebl.budget_bucket_id = " + budgetBucketId
                        + " AND peb.deleted = 0 AND pe.deleted = 0 AND lci.deleted = 0 AND lci.is_active=1 AND lcip.deleted = 0 AND lcip.is_frozen = 0";

        RawSql rawSql = RawSqlBuilder.parse(sql).create();

        Query<TotalAmount> query = Ebean.find(TotalAmount.class);

        Double totalAmount = query.setRawSql(rawSql).findUnique().totalAmount;

        if (totalAmount == null) {
            return 0.0;
        }

        return totalAmount;
    }

    /**
     * Get the active budget lines of a budget bucket as expression list.
     * 
     * @param budgetBucketId
     *            the budget bucket id
     */
    public static ExpressionList<PortfolioEntryBudgetLine> getPEBudgetLineActiveAsExprByBucket(Long budgetBucketId) {
        return PortfolioEntryBudgetDAO.findPortfolioEntryBudgetLine.where().eq("deleted", false).eq("budgetBucket.id", budgetBucketId)
                .eq("portfolioEntryBudget.deleted", false).eq("portfolioEntryBudget.lifeCycleInstancePlannings.deleted", false)
                .eq("portfolioEntryBudget.lifeCycleInstancePlannings.isFrozen", false)
                .eq("portfolioEntryBudget.lifeCycleInstancePlannings.lifeCycleInstance.deleted", false)
                .eq("portfolioEntryBudget.lifeCycleInstancePlannings.lifeCycleInstance.isActive", true)
                .eq("portfolioEntryBudget.lifeCycleInstancePlannings.lifeCycleInstance.portfolioEntry.deleted", false);
    }

    /**
     * Get the active budget lines of a budget bucket as pagination.
     * 
     * @param budgetBucketId
     *            the budget bucket id
     */
    public static Pagination<PortfolioEntryBudgetLine> getPEBudgetLineActiveAsPaginationByBucket(Long budgetBucketId) {
        return new Pagination<>(getPEBudgetLineActiveAsExprByBucket(budgetBucketId));
    }

    /**
     * @param budgetBucketid
     *            the budget bucket id
     * 
     * @return portfolio entry budget lines list
     **/
    public static List<PortfolioEntryBudgetLine> getPEBudgetLineAsListByBucket(Long budgetBucketid) {

        return getPEBudgetLineActiveAsExprByBucket(budgetBucketid).findList();
    }

    /**
     * Get the list portfolio entry budget lines of a portfolio entry.
     * 
     * @param portfolioEntryId
     *            the portfolio entry id
     **/
    public static List<PortfolioEntryBudgetLine> getPEBudgetLineAsListByPE(Long portfolioEntryId) {

        return PortfolioEntryBudgetDAO.findPortfolioEntryBudgetLine.where().eq("deleted", false)
                .eq("portfolioEntryBudget.lifeCycleInstancePlannings.lifeCycleInstance.portfolioEntry.id", portfolioEntryId)
                .eq("portfolioEntryBudget.deleted", false).eq("portfolioEntryBudget.lifeCycleInstancePlannings.deleted", false)
                .eq("portfolioEntryBudget.lifeCycleInstancePlannings.isFrozen", false)
                .eq("portfolioEntryBudget.lifeCycleInstancePlannings.lifeCycleInstance.deleted", false)
                .eq("portfolioEntryBudget.lifeCycleInstancePlannings.lifeCycleInstance.isActive", true)
                .eq("portfolioEntryBudget.lifeCycleInstancePlannings.lifeCycleInstance.portfolioEntry.deleted", false).findList();

    }
}
