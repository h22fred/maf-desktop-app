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
package controllers;

import java.util.UUID;

import framework.services.configuration.II18nMessagesPlugin;
import framework.utils.Utilities;
import play.Configuration;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;

/**
 * Generic actions.
 * 
 * @author Pierre-Yves Cloux
 * 
 */
public abstract class ControllersUtils {

    /**
     * Log the exception and return a generic error message.
     * 
     * @param e
     *            an exception
     * @param log
     *            a log instance
     * @param configuration
     *            the Play configuration service
     * @param messagePlugin
     *            the i18n messages service
     */
    public static Result logAndReturnUnexpectedError(Exception e, Logger.ALogger log, Configuration configuration, II18nMessagesPlugin messagePlugin) {
        return logAndReturnUnexpectedError(e, null, log, configuration, messagePlugin);
    }

    /**
     * Log the exception and return a generic error message.
     * 
     * @param e
     *            an exception
     * @param message
     *            a specific message to be added to the error log
     * @param log
     *            a log instance
     * @param configuration
     *            the Play configuration service
     * @param messagePlugin
     *            the i18n messages service
     */
    public static Result logAndReturnUnexpectedError(Exception e, String message, Logger.ALogger log, Configuration configuration,
            II18nMessagesPlugin messagePlugin) {
        try {
            String uuid = UUID.randomUUID().toString();
            log.error("Unexpected error with uuid " + uuid + (message != null ? " from " + message : ""), e);
            if (configuration.getBoolean("maf.unexpected.error.trace")) {
                String stackTrace = Utilities.getExceptionAsString(e);
                return Controller.internalServerError(
                        views.html.error.unexpected_error_with_stacktrace.render(messagePlugin.get("unexpected.error.title"), uuid, stackTrace));
            }
            return Controller.internalServerError(views.html.error.unexpected_error.render(messagePlugin.get("unexpected.error.title"), uuid));
        } catch (Exception exp) {
            System.err.println("Unexpected error in logAndReturnUnexpectedError : prevent looping");
            return Controller.internalServerError("Unexpected error");
        }
    }
}
