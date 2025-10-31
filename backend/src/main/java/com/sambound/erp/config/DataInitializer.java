package com.sambound.erp.config;

import com.sambound.erp.entity.Permission;
import com.sambound.erp.entity.Role;
import com.sambound.erp.entity.User;
import com.sambound.erp.repository.PermissionRepository;
import com.sambound.erp.repository.RoleRepository;
import com.sambound.erp.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        initializePermissions();
        initializeRoles();
        initializeAdminUser();
    }

    private void initializePermissions() {
        String[] permissions = {
                "user:read", "user:write", "user:delete",
                "product:read", "product:write", "product:delete",
                "order:read", "order:write", "order:delete"
        };

        for (String permName : permissions) {
            if (!permissionRepository.existsByName(permName)) {
                Permission permission = Permission.builder()
                        .name(permName)
                        .description("权限: " + permName)
                        .build();
                permissionRepository.save(permission);
            }
        }
    }

    private void initializeRoles() {
        // 创建ADMIN角色
        if (!roleRepository.existsByName("ADMIN")) {
            Set<Permission> adminPermissions = new HashSet<>(permissionRepository.findAll());
            Role adminRole = Role.builder()
                    .name("ADMIN")
                    .description("管理员角色")
                    .permissions(adminPermissions)
                    .build();
            roleRepository.save(adminRole);
        }

        // 创建USER角色
        if (!roleRepository.existsByName("USER")) {
            Set<Permission> userPermissions = new HashSet<>();
            userPermissions.add(permissionRepository.findByName("user:read").orElse(null));
            userPermissions.add(permissionRepository.findByName("product:read").orElse(null));
            userPermissions.add(permissionRepository.findByName("order:read").orElse(null));

            Role userRole = Role.builder()
                    .name("USER")
                    .description("普通用户角色")
                    .permissions(userPermissions)
                    .build();
            roleRepository.save(userRole);
        }
    }

    private void initializeAdminUser() {
        if (!userRepository.existsByUsername("admin")) {
            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseThrow(() -> new RuntimeException("ADMIN角色未找到"));

            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .email("admin@example.com")
                    .fullName("系统管理员")
                    .enabled(true)
                    .accountNonExpired(true)
                    .accountNonLocked(true)
                    .credentialsNonExpired(true)
                    .roles(Set.of(adminRole))
                    .build();

            userRepository.save(admin);
        }
    }
}

