package com.atguigu.gmall0416.manage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import tk.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan(basePackages = "com.atguigu.gmall0416.manage.mapper")
@ComponentScan(basePackages = "com.atguigu.gmall0416")
public class GmallManageServiceApplication {

	public static void main(String[] args) {

		SpringApplication.run(GmallManageServiceApplication.class, args);
	}

}
