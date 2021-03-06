/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.data.gemfire.config.annotation;

import static org.springframework.data.gemfire.util.CollectionUtils.nullSafeMap;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.geode.cache.Cache;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.util.StringUtils;

/**
 * Spring {@link Configuration} class used to construct, configure and initialize a peer {@link Cache} instance
 * in a Spring application context.
 *
 * @author John Blum
 * @see org.apache.geode.cache.Cache
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.context.annotation.Import
 * @see org.springframework.data.gemfire.config.annotation.AbstractCacheConfiguration
 * @see org.springframework.data.gemfire.config.annotation.AdministrativeConfiguration
 * @since 1.9.0
 */
@Configuration
@Import(AdministrativeConfiguration.class)
@SuppressWarnings("unused")
public class PeerCacheConfiguration extends AbstractCacheConfiguration {

    protected static final boolean DEFAULT_ENABLE_AUTO_RECONNECT = false;
    protected static final boolean DEFAULT_USE_CLUSTER_CONFIGURATION = false;

    protected static final String DEFAULT_NAME = "SpringBasedPeerCacheApplication";

    private boolean enableAutoReconnect = DEFAULT_ENABLE_AUTO_RECONNECT;
    private boolean useClusterConfiguration = DEFAULT_USE_CLUSTER_CONFIGURATION;

    private Integer lockLease;
    private Integer lockTimeout;
    private Integer messageSyncInterval;
    private Integer searchTimeout;

    @Autowired(required = false)
    private List<PeerCacheConfigurer> peerCacheConfigurers = Collections.emptyList();

    /**
     * Bean declaration for a single, peer {@link Cache} instance.
     *
     * @return a new instance of a peer {@link Cache}.
     * @see org.springframework.data.gemfire.CacheFactoryBean
     * @see org.apache.geode.cache.GemFireCache
     * @see org.apache.geode.cache.Cache
     * @see #constructCacheFactoryBean()
     */
    @Bean
    public CacheFactoryBean gemfireCache() {

        CacheFactoryBean gemfireCache = constructCacheFactoryBean();

        gemfireCache.setEnableAutoReconnect(enableAutoReconnect());
        gemfireCache.setLockLease(lockLease());
        gemfireCache.setLockTimeout(lockTimeout());
        gemfireCache.setMessageSyncInterval(messageSyncInterval());
        gemfireCache.setPeerCacheConfigurers(resolvePeerCacheConfigurers());
        gemfireCache.setSearchTimeout(searchTimeout());
        gemfireCache.setUseBeanFactoryLocator(useBeanFactoryLocator());
        gemfireCache.setUseClusterConfiguration(useClusterConfiguration());

        return gemfireCache;
    }

    /* (non-Javadoc) */
    private List<PeerCacheConfigurer> resolvePeerCacheConfigurers() {

        return Optional.ofNullable(this.peerCacheConfigurers)
            .filter(peerCacheConfigurers -> !peerCacheConfigurers.isEmpty())
            .orElseGet(() ->
                Optional.of(this.getBeanFactory())
                    .filter(beanFactory -> beanFactory instanceof ListableBeanFactory)
                    .map(beanFactory -> {

                        Map<String, PeerCacheConfigurer> beansOfType = ((ListableBeanFactory) beanFactory)
                            .getBeansOfType(PeerCacheConfigurer.class, true, true);

                        return nullSafeMap(beansOfType).values().stream().collect(Collectors.toList());

                    })
                    .orElseGet(Collections::emptyList)
            );
    }

    /**
     * Constructs a new instance of {@link CacheFactoryBean} used to create a peer {@link Cache}.
     *
     * @param <T> {@link Class} sub-type of {@link CacheFactoryBean}.
     * @return a new instance of {@link CacheFactoryBean}.
     * @see org.springframework.data.gemfire.CacheFactoryBean
     */
    @Override
    @SuppressWarnings("unchecked")
    protected <T extends CacheFactoryBean> T newCacheFactoryBean() {
        return (T) new CacheFactoryBean();
    }

    /**
     * Configures peer {@link Cache} specific settings.
     *
     * @param importMetadata {@link AnnotationMetadata} containing peer cache meta-data used to
     * configure the peer {@link Cache}.
     * @see org.springframework.core.type.AnnotationMetadata
     * @see #isCacheServerOrPeerCacheApplication(AnnotationMetadata)
     */
    @Override
    protected void configureCache(AnnotationMetadata importMetadata) {

        super.configureCache(importMetadata);

        if (isCacheServerOrPeerCacheApplication(importMetadata)) {

            Map<String, Object> peerCacheApplicationAttributes =
                importMetadata.getAnnotationAttributes(getAnnotationTypeName());

            if (peerCacheApplicationAttributes != null) {

                setEnableAutoReconnect(resolveProperty(cachePeerProperty("enable-auto-reconnect"),
					Boolean.TRUE.equals(peerCacheApplicationAttributes.get("enableAutoReconnect"))));

                setLockLease(resolveProperty(cachePeerProperty("lock-lease"),
					(Integer) peerCacheApplicationAttributes.get("lockLease")));

                setLockTimeout(resolveProperty(cachePeerProperty("lock-timeout"),
					(Integer) peerCacheApplicationAttributes.get("lockTimeout")));

                setMessageSyncInterval(resolveProperty(cachePeerProperty("message-sync-interval"),
					(Integer) peerCacheApplicationAttributes.get("messageSyncInterval")));

                setSearchTimeout(resolveProperty(cachePeerProperty("search-timeout"),
					(Integer) peerCacheApplicationAttributes.get("searchTimeout")));

                setUseClusterConfiguration(resolveProperty(cachePeerProperty("use-cluster-configuration"),
					Boolean.TRUE.equals(peerCacheApplicationAttributes.get("useClusterConfiguration"))));

                Optional.ofNullable((String) peerCacheApplicationAttributes.get("locators"))
					.filter(PeerCacheConfiguration::hasValue)
					.ifPresent(this::setLocators);

                Optional.ofNullable(resolveProperty(cachePeerProperty("locators"), (String) null))
					.filter(StringUtils::hasText)
					.ifPresent(this::setLocators);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<? extends Annotation> getAnnotationType() {
        return PeerCacheApplication.class;
    }

    /* (non-Javadoc) */
    void setEnableAutoReconnect(boolean enableAutoReconnect) {
        this.enableAutoReconnect = enableAutoReconnect;
    }

    protected boolean enableAutoReconnect() {
        return this.enableAutoReconnect;
    }

    /* (non-Javadoc) */
    void setLockLease(Integer lockLease) {
        this.lockLease = lockLease;
    }

    protected Integer lockLease() {
        return this.lockLease;
    }

    /* (non-Javadoc) */
    void setLockTimeout(Integer lockTimeout) {
        this.lockTimeout = lockTimeout;
    }

    protected Integer lockTimeout() {
        return this.lockTimeout;
    }

    /* (non-Javadoc) */
    void setMessageSyncInterval(Integer messageSyncInterval) {
        this.messageSyncInterval = messageSyncInterval;
    }

    protected Integer messageSyncInterval() {
        return this.messageSyncInterval;
    }

    /* (non-Javadoc) */
    void setSearchTimeout(Integer searchTimeout) {
        this.searchTimeout = searchTimeout;
    }

    protected Integer searchTimeout() {
        return this.searchTimeout;
    }

    /* (non-Javadoc) */
    void setUseClusterConfiguration(boolean useClusterConfiguration) {
        this.useClusterConfiguration = useClusterConfiguration;
    }

    protected boolean useClusterConfiguration() {
        return this.useClusterConfiguration;
    }

    @Override
    public String toString() {
        return DEFAULT_NAME;
    }
}
