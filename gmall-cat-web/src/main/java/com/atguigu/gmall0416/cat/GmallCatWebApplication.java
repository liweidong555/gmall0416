package com.atguigu.gmall0416.cat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.atguigu.gmall0416")
public class GmallCatWebApplication {

	public static void main(String[] args) {
		SpringApplication.run(GmallCatWebApplication.class, args);
	}
}
