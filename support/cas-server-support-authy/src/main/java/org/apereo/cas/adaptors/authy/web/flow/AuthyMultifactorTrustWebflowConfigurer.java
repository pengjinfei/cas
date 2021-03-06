package org.apereo.cas.adaptors.authy.web.flow;

import org.apereo.cas.web.flow.AbstractMultifactorTrustedDeviceWebflowConfigurer;
import org.springframework.webflow.definition.registry.FlowDefinitionRegistry;

/**
 * This is {@link AuthyMultifactorTrustWebflowConfigurer}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
public class AuthyMultifactorTrustWebflowConfigurer extends AbstractMultifactorTrustedDeviceWebflowConfigurer {

    private FlowDefinitionRegistry flowDefinitionRegistry;

    public void setFlowDefinitionRegistry(final FlowDefinitionRegistry flowDefinitionRegistry) {
        this.flowDefinitionRegistry = flowDefinitionRegistry;
    }

    @Override
    protected void doInitialize() throws Exception {
        registerMultifactorTrustedAuthentication(this.flowDefinitionRegistry);
    }

}
