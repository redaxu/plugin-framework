package com.gaoding.ska.customize.dao;

import com.gaoding.ska.customize.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 活动数据访问层
 *
 * @author baiye
 * @since 2024/12/19
 */
@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findByStatus(Integer status);

    List<Activity> findByNameContaining(String name);

}

