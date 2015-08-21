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
package services.job;

import framework.services.job.IJobDescriptor;
import modules.StaticAccessor;
import play.Logger;
import services.licensesmanagement.ILicensesManagementService;

/**
 * All available job descriptors.
 * 
 * @author Johann Kohler
 */
public interface JobDescriptors {

    /**
     * The UpdateConsumedLicenses job descriptor.
     * 
     * @author Johann Kohler
     * 
     */
    class UpdateConsumedLicensesJobDescriptor implements IJobDescriptor {

        @Override
        public String getId() {
            return "UpdateConsumedLicenses";
        }

        @Override
        public String getName(String languageCode) {
            return "Update the consumed licenses";
        }

        @Override
        public String getDescription(String languageCode) {
            return "Update the consumed licenses (active portfolio entries, users and storage) in eChannel.";
        }

        @Override
        public Frequency getFrequency() {
            return Frequency.DAILY;
        }

        @Override
        public int getStartHour() {
            return 2;
        }

        @Override
        public int getStartMinute() {
            return 0;
        }

        @Override
        public void trigger() {
            ILicensesManagementService licensesManagementService = StaticAccessor.getLicensesManagementService();
            licensesManagementService.updateConsumedPortfolioEntries();
            licensesManagementService.updateConsumedUsers();
            licensesManagementService.updateConsumedStorage();

        }

        @Override
        public String getTriggerUrl() {
            return null;
        }

    }

    /**
     * Send notification events.
     * 
     * @author Johann Kohler
     * 
     */
    class SendNotificationEventsJobDescriptor implements IJobDescriptor {

        @Override
        public String getId() {
            return "SendNotificationEvents";
        }

        @Override
        public String getName(String languageCode) {
            return "Send the notification events";
        }

        @Override
        public String getDescription(String languageCode) {
            return "Send the notification eventy to notify provided by eChannel.";
        }

        @Override
        public Frequency getFrequency() {
            return Frequency.HOURLY;
        }

        @Override
        public int getStartHour() {
            return 01;
        }

        @Override
        public int getStartMinute() {
            return 00;
        }

        @Override
        public void trigger() {

            Logger.info("start trigger " + this.getId());

            try {
                /*
                 * List<NotificationEvent> notificationEvents =
                 * echannelService.getNotificationEventsToNotify(); for
                 * (NotificationEvent notificationEvent : notificationEvents) {
                 * // TODO send the notification Logger.info("send: " +
                 * notificationEvent.title); }
                 */

            } catch (Exception e) {
                Logger.error(this.getId() + " unexpected error", e);
            }

            Logger.info("end trigger " + this.getId());

        }

        @Override
        public String getTriggerUrl() {
            return null;
        }

    }

}
