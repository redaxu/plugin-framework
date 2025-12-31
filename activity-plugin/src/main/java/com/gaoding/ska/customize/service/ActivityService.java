package com.gaoding.ska.customize.service;

import com.gaoding.ska.customize.dao.ActivityRepository;
import com.gaoding.ska.customize.dto.ActivityCreateRequest;
import com.gaoding.ska.customize.dto.ActivityDTO;
import com.gaoding.ska.customize.entity.Activity;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 活动服务类
 *
 * @author baiye
 * @since 2024/12/19
 */
@Service
@Transactional
public class ActivityService {

    @Autowired
    private ActivityRepository activityRepository;

    public ActivityDTO createActivity(ActivityCreateRequest request) {
        Activity activity = new Activity();
        BeanUtils.copyProperties(request, activity);
        Date now = new Date();
        activity.setCreateTime(now);
        activity.setUpdateTime(now);
        Activity saved = activityRepository.save(activity);
        return toDTO(saved);
    }

    public ActivityDTO getActivityById(Long id) {
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Activity not found with id: " + id));
        return toDTO(activity);
    }

    public List<ActivityDTO> getAllActivities() {
        List<Activity> activities = activityRepository.findAll();
        return activities.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<ActivityDTO> getActivitiesByStatus(Integer status) {
        List<Activity> activities = activityRepository.findByStatus(status);
        return activities.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ActivityDTO updateActivity(Long id, ActivityCreateRequest request) {
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Activity not found with id: " + id));
        BeanUtils.copyProperties(request, activity, "id", "createTime");
        activity.setUpdateTime(new Date());
        Activity updated = activityRepository.save(activity);
        return toDTO(updated);
    }

    public void deleteActivity(Long id) {
        if (!activityRepository.existsById(id)) {
            throw new RuntimeException("Activity not found with id: " + id);
        }
        activityRepository.deleteById(id);
    }

    private ActivityDTO toDTO(Activity activity) {
        ActivityDTO dto = new ActivityDTO();
        BeanUtils.copyProperties(activity, dto);
        return dto;
    }

}

