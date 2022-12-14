package io.metersphere.config;

import io.metersphere.commons.utils.ShiroUtils;
import io.metersphere.security.ApiKeyFilter;
import io.metersphere.security.CsrfFilter;
import io.metersphere.security.UserModularRealmAuthenticator;
import io.metersphere.security.realm.LdapRealm;
import io.metersphere.security.realm.LocalRealm;
import org.apache.shiro.authc.pam.FirstSuccessfulStrategy;
import org.apache.shiro.authc.pam.ModularRealmAuthenticator;
import org.apache.shiro.cache.MemoryConstrainedCacheManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.session.mgt.SessionManager;
import org.apache.shiro.spring.LifecycleBeanPostProcessor;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import java.util.*;

@Configuration
@ConditionalOnProperty(prefix = "sso", name = "mode", havingValue = "local", matchIfMissing = true)
public class ShiroConfig implements EnvironmentAware {
    private Environment env;

    @Bean
    public ShiroFilterFactoryBean getShiroFilterFactoryBean(DefaultWebSecurityManager sessionManager) {
        ShiroFilterFactoryBean shiroFilterFactoryBean = new ShiroFilterFactoryBean();
        shiroFilterFactoryBean.setLoginUrl("/login");
        shiroFilterFactoryBean.setSecurityManager(sessionManager);
        shiroFilterFactoryBean.setUnauthorizedUrl("/403");
        shiroFilterFactoryBean.setSuccessUrl("/");

        shiroFilterFactoryBean.getFilters().put("apikey", new ApiKeyFilter());
        shiroFilterFactoryBean.getFilters().put("csrf", new CsrfFilter());
        Map<String, String> filterChainDefinitionMap = shiroFilterFactoryBean.getFilterChainDefinitionMap();

        ShiroUtils.loadBaseFilterChain(filterChainDefinitionMap);

        ShiroUtils.ignoreCsrfFilter(filterChainDefinitionMap);
        filterChainDefinitionMap.put("/workspace/list/all/*/*", "anon");
        filterChainDefinitionMap.put("/project/list/*/*", "anon");
        filterChainDefinitionMap.put("/test/case/list/*/*", "anon");

        filterChainDefinitionMap.put("/test/case/relate/test/*/*", "anon");
        filterChainDefinitionMap.put("/test/case/relate/delete/*/*", "anon");
        filterChainDefinitionMap.put("/test/case/relate/auto/list/*", "anon");

        filterChainDefinitionMap.put("/**", "apikey, csrf, authc");
        return shiroFilterFactoryBean;
    }

    @Bean(name = "shiroFilter")
    public FilterRegistrationBean<Filter> shiroFilter(ShiroFilterFactoryBean shiroFilterFactoryBean) throws Exception {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter((Filter) Objects.requireNonNull(shiroFilterFactoryBean.getObject()));
        registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        return registration;
    }

    @Bean
    public MemoryConstrainedCacheManager memoryConstrainedCacheManager() {
        return new MemoryConstrainedCacheManager();
    }

    /**
     * securityManager ?????????????????? Realm??????????????????????????????
     * ??????????????? handleContextRefresh
     * http://www.debugrun.com/a/NKS9EJQ.html
     */
    @Bean(name = "securityManager")
    public DefaultWebSecurityManager securityManager(SessionManager sessionManager, MemoryConstrainedCacheManager memoryConstrainedCacheManager) {
        DefaultWebSecurityManager dwsm = new DefaultWebSecurityManager();
        dwsm.setSessionManager(sessionManager);
        dwsm.setCacheManager(memoryConstrainedCacheManager);
        dwsm.setAuthenticator(modularRealmAuthenticator());
        return dwsm;
    }

    @Bean
    @DependsOn("lifecycleBeanPostProcessor")
    public LocalRealm localRealm() {
        return new LocalRealm();
    }

    @Bean
    @DependsOn("lifecycleBeanPostProcessor")
    public LdapRealm ldapRealm() {
        return new LdapRealm();
    }

    @Bean(name = "lifecycleBeanPostProcessor")
    public LifecycleBeanPostProcessor lifecycleBeanPostProcessor() {
        return new LifecycleBeanPostProcessor();
    }

    @Bean
    @DependsOn({"lifecycleBeanPostProcessor"})
    public DefaultAdvisorAutoProxyCreator getDefaultAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator daap = new DefaultAdvisorAutoProxyCreator();
        daap.setProxyTargetClass(true);
        return daap;
    }

    @Bean
    public ModularRealmAuthenticator modularRealmAuthenticator() {
        //???????????????ModularRealmAuthenticator
        UserModularRealmAuthenticator modularRealmAuthenticator = new UserModularRealmAuthenticator();
        modularRealmAuthenticator.setAuthenticationStrategy(new FirstSuccessfulStrategy());
        return modularRealmAuthenticator;
    }

    @Bean
    public AuthorizationAttributeSourceAdvisor getAuthorizationAttributeSourceAdvisor(DefaultWebSecurityManager sessionManager) {
        AuthorizationAttributeSourceAdvisor aasa = new AuthorizationAttributeSourceAdvisor();
        aasa.setSecurityManager(sessionManager);
        return aasa;
    }

    @Bean
    public SessionManager sessionManager(MemoryConstrainedCacheManager memoryConstrainedCacheManager) {
        Long sessionTimeout = env.getProperty("session.timeout", Long.class, 43200L); // ??????43200s, 12?????????
        return ShiroUtils.getSessionManager(sessionTimeout, memoryConstrainedCacheManager);
    }

    /**
     * ??????ApplicationContext ?????????????????? ??????shiroRealm
     */
    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        List<Realm> realmList = new ArrayList<>();
        LocalRealm localRealm = context.getBean(LocalRealm.class);
        LdapRealm ldapRealm = context.getBean(LdapRealm.class);
        // ??????realm
        realmList.add(localRealm);
        realmList.add(ldapRealm);
        context.getBean(DefaultWebSecurityManager.class).setRealms(realmList);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
