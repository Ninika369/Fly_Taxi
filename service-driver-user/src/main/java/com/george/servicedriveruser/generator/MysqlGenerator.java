package com.george.servicedriveruser.generator;

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
        FastAutoGenerator.create("jdbc:mysql://localhost:3306/service-driver-user?characterEncoding=utf-8&serverTimezone=GMT%2B8",
                "root", "sunhaoxian")
                .globalConfig(builder -> {
                    builder.author("george").fileOverride().outputDir("/Users/sunhaoxian/Desktop/fly_taxi/FlyTaxi/service-driver-user/src/main/java");
                })
                .packageConfig(builder -> {
                    builder.parent("com.george.servicedriveruser").pathInfo(Collections.singletonMap(OutputFile.mapperXml,
                            "/Users/sunhaoxian/Desktop/fly_taxi/FlyTaxi/service-driver-user/src/main/java/com/george/servicedriveruser/mapper"));
                })
                .strategyConfig(builder -> {
                    builder.addInclude("driver_user_work_status");
                })
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();

    }
}
