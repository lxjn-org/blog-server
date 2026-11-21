package com.lxjn.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class BlogServerApplication {

    /** 启动博客管理服务。 */
    public static void main(String[] args) {
        SpringApplication.run(BlogServerApplication.class, args);
    }
}
