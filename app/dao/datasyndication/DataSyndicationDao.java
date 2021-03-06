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
package dao.datasyndication;

import java.util.List;

import com.avaje.ebean.Model.Finder;
import com.fasterxml.jackson.databind.ObjectMapper;

import models.datasyndication.DataSyndication;
import play.Logger;

/**
 * DAO for the {@link DataSyndication} object.
 * 
 * @author Johann Kohler
 */
public abstract class DataSyndicationDao {

    public static Finder<Long, DataSyndication> findDataSyndication = new Finder<>(DataSyndication.class);

    /**
     * Default constructor.
     */
    public DataSyndicationDao() {
    }

    /**
     * Get a data syndication by id.
     * 
     * @param id
     *            the data syndication id
     */
    public static DataSyndication getDataSyndicationById(Long id) {
        return findDataSyndication.where().eq("deleted", false).eq("id", id).findUnique();
    }

    /**
     * Get a data syndication for an item of a link.
     * 
     * @param dataSyndicationAgreementLinkId
     *            the agreement link id
     * @param dataSyndicationAgreementItemId
     *            the agreement item id
     */
    public static DataSyndication getDataSyndicationByLinkAndItem(Long dataSyndicationAgreementLinkId, Long dataSyndicationAgreementItemId) {
        return findDataSyndication.where().eq("deleted", false).eq("dataSyndicationAgreementLinkId", dataSyndicationAgreementLinkId)
                .eq("dataSyndicationAgreementItemId", dataSyndicationAgreementItemId).findUnique();
    }

    /**
     * Get the data part of a data syndication for an item of a link.
     * 
     * @param dataSyndicationAgreementLinkId
     *            the agreement link id
     * @param dataSyndicationAgreementItemId
     *            the agreement item id
     */
    public static List<List<Object>> getDataSyndicationAsDataByLinkAndItem(Long dataSyndicationAgreementLinkId, Long dataSyndicationAgreementItemId) {

        DataSyndication dataSyndication = getDataSyndicationByLinkAndItem(dataSyndicationAgreementLinkId, dataSyndicationAgreementItemId);

        if (dataSyndication != null) {
            try {
                DataJsonMapping dataJsonMapping = new ObjectMapper().readValue("{ \"data\" : " + dataSyndication.data + " }", DataJsonMapping.class);
                return dataJsonMapping.data;
            } catch (Exception e) {
                Logger.error("impossible to convert the data to a List<List<Object>>", e);
            }
        }

        return null;

    }

    /**
     * JSON Mapping class for the data attribute of a DataSyndication.
     * 
     * @author Johann Kohler
     *
     */
    public static class DataJsonMapping {

        public List<List<Object>> data;

        /**
         * Default constructor.
         */
        public DataJsonMapping() {
        }

    }

}
