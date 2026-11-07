package com.flowpilot.organizations.internal.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrganizationProperties.class)
class OrganizationsConfiguration {
}
