package com.george.servicepay.controller;

import com.alipay.easysdk.factory.Factory;
import com.alipay.easysdk.payment.page.models.AlipayTradePagePayResponse;

import com.george.servicepay.service.AlipayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is used to handle all functions related to Alipay
 */
@RequestMapping("/alipay")
@Controller
@ResponseBody
public class AlipayController {

    /**
     * This method is used to send a valid payment link to Alipay
     * @param subject - the subject of the payment
     * @param outTradeNo - the number of the trade (usually the order id)
     * @param totalAmount - the price of the order
     * @return
     */
    @GetMapping("/pay")
    public String pay(String subject,String outTradeNo, String totalAmount){
        AlipayTradePagePayResponse response ;
        try {
            response = Factory.Payment.Page().pay(subject, outTradeNo, totalAmount,"");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
        return response.getBody();
    }

    @Autowired
    AlipayService alipayService;

    /**
     * This method is used to handle the case that Alipay returns a response after a successful payment
     * @param request
     * @return
     * @throws Exception
     */
    @PostMapping("/notify")
    public String notify(HttpServletRequest request) throws Exception {
        String tradeStatus = request.getParameter("trade_status");

        if (tradeStatus.trim().equals("TRADE_SUCCESS")){
            Map<String,String> param = new HashMap<>();

            Map<String, String[]> parameterMap = request.getParameterMap();
            for (String name: parameterMap.keySet()) {
                param.put(name,request.getParameter(name));
            }

            if (Factory.Payment.Common().verifyNotify(param)){
                String out_trade_no = param.get("out_trade_no");
                Long orderId = Long.parseLong(out_trade_no);

                // Prompt the order service to complete the next step
                alipayService.pay(orderId);

            }else {
                System.out.println("Alipay verification does not pass!");
            }

        }

        return "success";
    }
}