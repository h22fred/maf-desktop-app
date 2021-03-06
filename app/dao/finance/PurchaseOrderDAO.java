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

import javax.persistence.PersistenceException;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Model.Finder;

import constants.IMafConstants;
import framework.services.account.IPreferenceManagerPlugin;
import framework.utils.DefaultSelectableValueHolderCollection;
import models.finance.GoodsReceipt;
import models.finance.PurchaseOrder;
import models.finance.PurchaseOrderLineItem;
import models.finance.PurchaseOrderLineShipmentStatusType;

/**
 * DAO for the {@link PurchaseOrder}, {@link PurchaseOrderLineItem},
 * {@link PurchaseOrderLineShipmentStatusType} objects.
 * 
 * @author Pierre-Yves Cloux
 */
public abstract class PurchaseOrderDAO {

    public static Finder<Long, PurchaseOrder> findPurchaseOrder = new Finder<>(PurchaseOrder.class);
    public static Finder<Long, PurchaseOrderLineItem> findPurchaseOrderLineItem = new Finder<>(PurchaseOrderLineItem.class);
    public static Finder<Long, PurchaseOrderLineShipmentStatusType> findPurchaseOrderLineShipmentStatusType = new Finder<>(
            PurchaseOrderLineShipmentStatusType.class);
    public static Finder<Long, GoodsReceipt> findGoodsReceipt = new Finder<>(GoodsReceipt.class);

    /**
     * Default constructor.
     */
    public PurchaseOrderDAO() {
    }

    /**
     * Define if the system is configured to use the purchase orders.
     * 
     * @param preferenceManagerPlugin
     *            the preference manager service
     */
    public static boolean isSystemPreferenceUsePurchaseOrder(IPreferenceManagerPlugin preferenceManagerPlugin) {
        return preferenceManagerPlugin.getPreferenceValueAsBoolean(IMafConstants.FINANCIAL_USE_PURCHASE_ORDER_PREFERENCE);
    }

    /**
     * Get a purchase order by ref id.
     * 
     * @param refId
     *            the ref id
     */
    public static PurchaseOrder getPurchaseOrderByRefId(String refId) {
        try {
            return PurchaseOrderDAO.findPurchaseOrder.where().eq("deleted", false).eq("refId", refId).findUnique();
        } catch (PersistenceException e) {
            return PurchaseOrderDAO.findPurchaseOrder.where().eq("deleted", false).eq("refId", refId).findList().get(0);
        }
    }

    /**
     * Get the active purchase order for a ref id.
     * 
     * note: in a normal case ref id must be unique, if this is not the case
     * (because the DB model allows it), we return the first that matches the
     * ref id
     * 
     * @param refId
     *            the purchase order ref id
     */
    public static PurchaseOrder getPurchaseOrderActiveByRefId(String refId) {
        List<PurchaseOrder> purchaseOrders = PurchaseOrderDAO.findPurchaseOrder.where().eq("deleted", false).eq("isCancelled", false).eq("refId", refId)
                .findList();
        if (purchaseOrders != null && !purchaseOrders.isEmpty()) {
            return purchaseOrders.get(0);
        }
        return null;
    }

    /**
     * Get all active purchase orders.
     */
    public static List<PurchaseOrder> getPurchaseOrderActiveAsList() {
        return PurchaseOrderDAO.findPurchaseOrder.where().eq("deleted", false).ne("refId", IMafConstants.PURCHASE_ORDER_REF_ID_FOR_BUDGET_TRACKING)
                .eq("isCancelled", false).findList();
    }

    /**
     * Search from all purchase orders for which the criteria matches with the
     * ref id.
     * 
     * @param key
     *            the search criteria (use % for wild cards)
     */
    public static List<PurchaseOrder> getPurchaseOrderAsListByRefIdLike(String key) {
        return PurchaseOrderDAO.findPurchaseOrder.where().eq("deleted", false).ne("refId", IMafConstants.PURCHASE_ORDER_REF_ID_FOR_BUDGET_TRACKING)
                .ilike("refId", key + "%").findList();
    }

    public static List<PurchaseOrder> getPurchaseOrderAsListByRefIdLike2(String key) {
        return PurchaseOrderDAO.findPurchaseOrder.where().eq("deleted", false).ne("refId", IMafConstants.PURCHASE_ORDER_REF_ID_FOR_BUDGET_TRACKING)
                .ilike("refId", key ).findList();
    }
    /**
     * Search from all active purchase orders for which the criteria matches
     * with the ref id.
     * 
     * @param key
     *            the search criteria (use % for wild cards)
     */
    public static List<PurchaseOrder> getPurchaseOrderActiveAsListByRefIdLike(String key) {
        return PurchaseOrderDAO.findPurchaseOrder.where().eq("deleted", false).ne("refId", IMafConstants.PURCHASE_ORDER_REF_ID_FOR_BUDGET_TRACKING)
                .eq("isCancelled", false).ilike("refId", key + "%").findList();
    }

    /**
     * Get all active purchase orders as value holder collection.
     */
    public static DefaultSelectableValueHolderCollection<Long> getPurchaseOrderActiveActiveAsVH() {
        return new DefaultSelectableValueHolderCollection<>(getPurchaseOrderActiveAsList());
    }

    /**
     * Search from all active purchase orders with the search process defined by
     * the method "searchActive" and return a value holder collection.
     * 
     * @param key
     *            the search criteria (use % for wild cards)
     */
    public static DefaultSelectableValueHolderCollection<Long> getPurchaseOrderActiveAsVHByRefIdLike(String key) {
        return new DefaultSelectableValueHolderCollection<>(getPurchaseOrderActiveAsListByRefIdLike(key));
    }

    /**
     * Get an purchase order by id.
     * 
     * @param id
     *            the purchase order id
     * @return an purchase order specified by id
     */
    public static PurchaseOrder getPurchaseOrderById(Long id) {
        return findPurchaseOrder.where().eq("deleted", false).eq("id", id).findUnique();
    }

    /**
     * Get the purchase orders list with filter.
     * 
     * @param isCancelled
     *            true to return only cancelled purchase order, false only not
     *            cancelled, null all.
     **/
    public static List<PurchaseOrder> getPurchaseOrderAsListByFilter(Boolean isCancelled) {

        ExpressionList<PurchaseOrder> e = PurchaseOrderDAO.findPurchaseOrder.where().eq("deleted", false).ne("refId",
                IMafConstants.PURCHASE_ORDER_REF_ID_FOR_BUDGET_TRACKING);

        if (isCancelled != null) {
            e = e.eq("isCancelled", isCancelled);
        }

        return e.findList();
    }

    /**
     * Get a line item by id.
     * 
     * @param id
     *            the line item id
     */
    public static PurchaseOrderLineItem getPurchaseOrderLineItemById(Long id) {
        return PurchaseOrderDAO.findPurchaseOrderLineItem.where().eq("deleted", false).eq("id", id).findUnique();
    }

    /**
     * Get a line item by ref id.
     * 
     * @param refId
     *            the ref id
     */
    public static PurchaseOrderLineItem getPurchaseOrderLineItemByRefId(String refId) {
        try {
            return PurchaseOrderDAO.findPurchaseOrderLineItem.where().eq("deleted", false).eq("refId", refId).findUnique();
        } catch (PersistenceException e) {
            return PurchaseOrderDAO.findPurchaseOrderLineItem.where().eq("deleted", false).eq("refId", refId).findList().get(0);
        }
    }

    /**
     * Get the expression list of the active line items for a currency and an
     * expenditure type.
     * 
     * @param currency
     *            the currency code
     * @param isOpex
     *            set to true for OPEX, else CAPEX
     */
    public static ExpressionList<PurchaseOrderLineItem> getPurchaseOrderLineItemActiveAsExprByCurrencyAndOpex(String currency, Boolean isOpex) {
        return PurchaseOrderDAO.findPurchaseOrderLineItem.where().eq("deleted", false).ne("refId", IMafConstants.PURCHASE_ORDER_REF_ID_FOR_BUDGET_TRACKING)
                .eq("isCancelled", false).eq("isOpex", isOpex).eq("currency.code", currency);
    }

    /**
     * Get the list of active line items of a purchase order for a currency and
     * an expenditure type.
     * 
     * @param purchaseOrderId
     *            the purchase order id
     * @param currency
     *            the currency code
     * @param isOpex
     *            set to true for OPEX, else CAPEX
     */
    public static List<PurchaseOrderLineItem> getPurchaseOrderLineItemActiveAsListByPOAndCurrencyAndOpex(Long purchaseOrderId, String currency,
            Boolean isOpex) {
        return getPurchaseOrderLineItemActiveAsExprByCurrencyAndOpex(currency, isOpex).eq("purchaseOrder.id", purchaseOrderId).findList();
    }

    /**
     * Return true if there is at least one purchase order line item for the
     * currency.
     * 
     * @param currency
     *            the currency code
     */
    public static boolean hasPurchaseOrderLineItemByCurrency(String currency) {
        return PurchaseOrderDAO.findPurchaseOrderLineItem.where().eq("deleted", false).eq("currency.code", currency).findRowCount() > 0;
    }

    /**
     * Get the line item of a purchase order with filters.
     * 
     * @param purchaseOrderId
     *            the purchase order id
     * @param isCancelled
     *            the flag to define if the purchase order is cancelled
     **/
    public static List<PurchaseOrderLineItem> getPurchaseOrderLineItemActiveAsListByPO(Long purchaseOrderId, Boolean isCancelled) {

        ExpressionList<PurchaseOrderLineItem> e = PurchaseOrderDAO.findPurchaseOrderLineItem.where().eq("deleted", false)
                .ne("refId", IMafConstants.PURCHASE_ORDER_REF_ID_FOR_BUDGET_TRACKING).eq("purchaseOrder.id", purchaseOrderId);

        if (isCancelled != null) {
            e = e.eq("isCancelled", isCancelled);
        }

        return e.findList();
    }

    /**
     * Get the whole list of PurchaseOrderLineShipmentStatusType object.
     * 
     * @return a list of all purchase order list shipment status type
     */
    public static List<PurchaseOrderLineShipmentStatusType> getPurchaseOrderLineShipmentStatusTypeAsList() {

        ExpressionList<PurchaseOrderLineShipmentStatusType> e = PurchaseOrderDAO.findPurchaseOrderLineShipmentStatusType.where().eq("deleted", false);

        return e.findList();
    }

    /**
     * Get a purchase order line shipment status type by id.
     * 
     * @param id
     *            the purchase order line shipment status type id
     * @return a purchase order line shipment status type specified by id
     */
    public static PurchaseOrderLineShipmentStatusType getPurchaseOrderLineShipmentStatutsTypeById(Long id) {
        return findPurchaseOrderLineShipmentStatusType.where().eq("deleted", false).eq("id", id).findUnique();
    }

    /**
     * Get a line item by ref id.
     * 
     * @param refId
     *            the ref id
     */
    public static PurchaseOrderLineShipmentStatusType getPurchaseOrderLineShipmentStatusTypeByRefId(String refId) {
        try {
            return PurchaseOrderDAO.findPurchaseOrderLineShipmentStatusType.where().eq("deleted", false).eq("refId", refId).findUnique();
        } catch (PersistenceException e) {
            return PurchaseOrderDAO.findPurchaseOrderLineShipmentStatusType.where().eq("deleted", false).eq("refId", refId).findList().get(0);
        }
    }

    /**
     * Return true if there is at least one goods receipt for the currency.
     * 
     * @param currency
     *            the currency code
     */
    public static boolean hasGoodsReceiptByCurrency(String currency) {
        return findGoodsReceipt.where().eq("deleted", false).eq("currency.code", currency).findRowCount() > 0;
    }

}
