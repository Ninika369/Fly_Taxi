package com.george.servicepay.config;

import com.alipay.easysdk.factory.Factory;
import com.alipay.easysdk.kernel.Config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * This class is used to set the configuration for establishing a valid connection with Alipay
 */
@Component
@ConfigurationProperties(prefix = "alipay")
@Data
public class AlipayConfig {

    private String appId;

    private String appPrivateKey;

    private String publicKey;

    private String notifyUrl;

    @PostConstruct
    public void init(){
        Config config = new Config();
        // base configuration
        config.protocol = "https";
        config.gatewayHost = "openapi-sandbox.dl.alipaydev.com";
        config.signType = "RSA2";

        // Service configuration
        config.appId = this.appId;
        config.merchantPrivateKey = this.appPrivateKey;
        config.alipayPublicKey = this.publicKey;
        config.notifyUrl = this.notifyUrl;

        Factory.setOptions(config);
        System.out.println("The Alipay configuration initialization is complete!");
    }


}
