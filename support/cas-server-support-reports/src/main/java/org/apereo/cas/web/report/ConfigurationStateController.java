package org.apereo.cas.web.report;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.bus.BusProperties;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller that exposes the CAS internal state and beans
 * as JSON. The report is available at {@code /status/config}.
 *
 * @author Misagh Moayyed
 * @since 4.1
 */
@Controller("internalConfigController")
public class ConfigurationStateController {

    private static final String VIEW_CONFIG = "monitoring/viewConfig";

    @Autowired(required = false)
    private BusProperties busProperties;

    @Autowired
    private ConfigServerProperties configServerProperties;

    /**
     * Handle request.
     *
     * @param request  the request
     * @param response the response
     * @return the model and view
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/status/config")
    protected ModelAndView handleRequestInternal(
            final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {

        final Map<String, Object> model = new HashMap<>();
        final String path = request.getContextPath();
        if (busProperties != null && busProperties.isEnabled()) {
            model.put("refreshEndpoint", path + configServerProperties.getPrefix() + "/cas/bus/refresh");
            model.put("refreshMethod", "GET");
        } else {
            model.put("refreshEndpoint", path + "/status/refresh");
            model.put("refreshMethod", "POST");
        }

        return new ModelAndView(VIEW_CONFIG, model);
    }

}
