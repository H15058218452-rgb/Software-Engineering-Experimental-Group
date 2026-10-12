package com.example.shop;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.shop.entity.Seller;
import com.example.shop.mapper.SellerMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@MapperScan("com.example.shop.mapper")
public class ShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 首次启动时初始化固定卖家账号（用户名/密码可在 application.yml 配置）。
     */
    @Bean
    public ApplicationRunner sellerInitializer(SellerMapper sellerMapper,
                                               BCryptPasswordEncoder encoder,
                                               @Value("${app.default-seller.username}") String username,
                                               @Value("${app.default-seller.password}") String password) {
        return args -> {
            if (sellerMapper.selectCount(new QueryWrapper<Seller>().eq("username", username)) == 0) {
                Seller seller = new Seller();
                seller.setUsername(username);
                seller.setPasswordHash(encoder.encode(password));
                seller.setCreatedAt(java.time.LocalDateTime.now());
                seller.setUpdatedAt(java.time.LocalDateTime.now());
                sellerMapper.insert(seller);
            }
        };
    }
}
