package com.testdatagen.config;

import com.testdatagen.model.entity.User;
import com.testdatagen.model.enums.UserRole;
import com.testdatagen.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 应用启动时初始化默认管理员用户。
 * 仅在 admin 用户不存在时创建。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;

    public DataInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(new BCryptPasswordEncoder().encode("admin123"));
            admin.setRole(UserRole.ADMIN);
            admin.setNickname("管理员");
            userRepository.save(admin);
            log.info("默认管理员用户已创建: admin / admin123");
        }
    }
}
