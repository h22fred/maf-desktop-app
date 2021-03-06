package handlers;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import controllers.ControllersUtils;
import framework.handlers.AbstractErrorHandler;
import framework.security.ISecurityService;
import framework.services.account.IPreferenceManagerPlugin;
import framework.services.account.IUserAccount;
import framework.services.api.AbstractApiController;
import framework.services.api.ApiError;
import framework.services.api.IApiControllerUtilsService;
import framework.services.notification.INotificationManagerPlugin;
import framework.services.plugins.IPluginManagerService;
import play.Configuration;
import play.Environment;
import play.Logger;
import play.api.OptionalSourceMapper;
import play.api.routing.Router;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.Context;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;
import services.budgettracking.IBudgetTrackingService;
import services.datasyndication.IDataSyndicationService;
import services.echannel.IEchannelService;

/**
 * Handler registered to deal with errors.
 * 
 * @author Pierre-Yves Cloux
 */
@Singleton
public class MafHttpErrorHandler extends AbstractErrorHandler {
    private static Logger.ALogger log = Logger.of(MafHttpErrorHandler.class);

    @Inject
    private IDataSyndicationService dataSyndicationService;
    @Inject
    private ISecurityService securityService;
    @Inject
    private IApiControllerUtilsService apiControllerUtilsService;
    @Inject
    private INotificationManagerPlugin notificationService;
    @Inject
    private IEchannelService echannelService;
    @Inject
    private IPreferenceManagerPlugin preferenceManagerPlugin;
    @Inject
    private IBudgetTrackingService budgetTrackingService;
    @Inject
    private IPluginManagerService pluginManagerService;

    /**
     * Default constructor.
     * 
     * @param configuration
     *            the Play configuration service
     * @param environment
     *            the Play environment service
     * @param optionalSourceMapper
     *            the Play optional source mapper
     * @param providerRouter
     *            the route provider
     */
    @Inject
    public MafHttpErrorHandler(Configuration configuration, Environment environment, OptionalSourceMapper optionalSourceMapper,
            Provider<Router> providerRouter) {
        super(configuration, environment, optionalSourceMapper, providerRouter);
    }

    @Override
    public Promise<Result> onClientError(RequestHeader requestHeader, int statusCode, String error) {
        injectCommonServicesIncontext(Http.Context.current());
        if (statusCode == play.mvc.Http.Status.NOT_FOUND) {
            return Promise.promise(new Function0<Result>() {
                public Result apply() throws Throwable {
                    if (requestHeader.path().startsWith(AbstractApiController.STANDARD_API_ROOT_URI)) {
                        return getApiControllerUtilsService().getJsonErrorResponse(new ApiError(404, "Not found"), Controller.ctx().response());
                    } else {
                        return play.mvc.Results.notFound(views.html.error.not_found.render(requestHeader.uri()));
                    }
                }
            });
        }
        if (statusCode == play.mvc.Http.Status.BAD_REQUEST) {
            injectCommonServicesIncontext(Http.Context.current());
            return Promise.promise(new Function0<Result>() {
                public Result apply() throws Throwable {
                    if (requestHeader.path().startsWith(AbstractApiController.STANDARD_API_ROOT_URI)) {
                        return getApiControllerUtilsService().getJsonErrorResponse(new ApiError(400, error), Controller.ctx().response());
                    } else {
                        return play.mvc.Results.badRequest(views.html.error.bad_request.render());
                    }
                }

            });
        }
        return Promise.<Result> pure(play.mvc.Results.status(statusCode, "an unexpected error occured: " + error));
    }

    @Override
    public Promise<Result> onServerError(RequestHeader requestHeader, Throwable t) {
        injectCommonServicesIncontext(Http.Context.current());
        return Promise.promise(new Function0<Result>() {
            public Result apply() throws Throwable {
                return (Result) ControllersUtils.logAndReturnUnexpectedError((Exception) t, getUserMailIfAvailableInContext(), log, getConfiguration(),
                        getMessagesPlugin());
            }
        });
    }

    @Override
    protected void injectCommonServicesIncontext(Context context) {
        super.injectCommonServicesIncontext(context);
        context.args.put(IDataSyndicationService.class.getName(), dataSyndicationService);
        context.args.put(ISecurityService.class.getName(), securityService);
        context.args.put(INotificationManagerPlugin.class.getName(), notificationService);
        context.args.put(IEchannelService.class.getName(), echannelService);
        context.args.put(IPreferenceManagerPlugin.class.getName(), preferenceManagerPlugin);
        context.args.put(IBudgetTrackingService.class.getName(), budgetTrackingService);
        context.args.put(IPluginManagerService.class.getName(), pluginManagerService);
    }

    /**
     * Get the API controller utils service.
     */
    private IApiControllerUtilsService getApiControllerUtilsService() {
        return apiControllerUtilsService;
    }

    /**
     * Return the currently logged user mail (if any).
     * 
     * @return an email address
     */
    private String getUserMailIfAvailableInContext() {
        try {
            ;
            IUserAccount userAccount = securityService.getCurrentUser();
            if (userAccount != null) {
                return userAccount.getMail();
            }
        } catch (Exception e) {
        }
        return "Unknown user";
    }
}
