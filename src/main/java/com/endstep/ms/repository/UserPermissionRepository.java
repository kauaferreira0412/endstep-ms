package com.endstep.ms.repository;

import com.endstep.ms.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

    List<UserPermission> findByUserId(Long userId);

    List<UserPermission> findByUserIdIn(Collection<Long> userIds);

    boolean existsByUserIdAndPermission(Long userId, String permission);
}
