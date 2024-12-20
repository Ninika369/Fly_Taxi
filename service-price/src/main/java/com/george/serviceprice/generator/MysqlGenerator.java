package com.george.serviceprice.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.util.Collections;

/**
 * @Author: George Sun
 * @Date: 2024-11-01-22:17
 * @Description: Automatically generated variables for database
 */
public class MysqlGenerator {
    public static void main(String[] args) {
        FastAutoGenerator.create("jdbc:mysql://localhost:3306/service-price?characterEncoding=utf-8&serverTimezone=GMT%2B8",
                "root", "sunhaoxian")
                .globalConfig(builder -> {
                    builder.author("george").fileOverride().outputDir("/Users/sunhaoxian/Desktop/fly_taxi/FlyTaxi/service-price/src/main/java");
                })
                .packageConfig(builder -> {
                    builder.parent("com.george.serviceprice").pathInfo(Collections.singletonMap(OutputFile.mapperXml,
                            "/Users/sunhaoxian/Desktop/fly_taxi/FlyTaxi/service-price/src/main/java/com/george/serviceprice/mapper"));
                })
                .strategyConfig(builder -> {
                    builder.addInclude("price_rule");
                })
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();

    }
}
