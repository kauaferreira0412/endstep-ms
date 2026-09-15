package com.endstep.ms.repository;

import com.endstep.ms.entity.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AchievementRepository extends JpaRepository<Achievement, Long> {

    List<Achievement> findByMetricOrderByTargetAsc(String metric);

    List<Achievement> findAllByOrderByPositionAsc();
}
