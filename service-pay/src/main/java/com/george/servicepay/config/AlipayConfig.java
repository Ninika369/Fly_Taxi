package com.george.servicepay.config;

import com.alipay.easysdk.factory.Factory;
import com.alipay.easysdk.kernel.Config;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * This class is used to set the configuration for establishing a valid connection with Alipay
 */
@Component
@ConfigurationProperties(prefix = "alipay")
@Data
@Slf4j
public class AlipayConfig {

    private String appId;

    private String appPrivateKey;

    private String publicKey;

    private String notifyUrl;

    private String protocol;

    private String gatewayHost;

    private String signType;

    @PostConstruct
    public void init(){
        Config config = new Config();
        // base configuration
        config.protocol = this.protocol;
        config.gatewayHost = this.gatewayHost;
        config.signType = this.signType;

        // Service configuration
        config.appId = this.appId;
        config.merchantPrivateKey = this.appPrivateKey;
        config.alipayPublicKey = this.publicKey;
        config.notifyUrl = this.notifyUrl;

        Factory.setOptions(config);
        log.info("Alipay configuration initialization is complete");
    }


}
