package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Main.class, args);
        Environment env = ctx.getEnvironment();
        String port = env.getProperty("server.port", "9900");
        System.out.println("\n========================================================================");
        System.out.println("🚀 OpsPilot 智能运维 Agent 启动成功！");
        System.out.println("👉 前端主界面 (Localhost): http://localhost:" + port);
        System.out.println("👉 前端主界面 (IPv4 直连): http://127.0.0.1:" + port);
        System.out.println("👉 H2 数据库治理控制台:    http://127.0.0.1:" + port + "/h2-console");
        System.out.println("========================================================================\n");
    }
}