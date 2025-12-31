package com.gaoding.ska.customize.controller;

import com.gaoding.ska.customize.dto.ActivityCreateRequest;
import com.gaoding.ska.customize.dto.ActivityDTO;
import com.gaoding.ska.customize.service.ActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 活动管理控制器
 *
 * @author baiye
 * @since 2024/12/19
 */
@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    @Autowired
    private ActivityService activityService;

    @PostMapping
    public ResponseEntity<ActivityDTO> createActivity(@RequestBody ActivityCreateRequest request) {
        ActivityDTO activity = activityService.createActivity(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(activity);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ActivityDTO> getActivity(@PathVariable Long id) {
        ActivityDTO activity = activityService.getActivityById(id);
        return ResponseEntity.ok(activity);
    }

    @GetMapping
    public ResponseEntity<List<ActivityDTO>> getAllActivities(
            @RequestParam(required = false) Integer status) {
        List<ActivityDTO> activities;
        if (status != null) {
            activities = activityService.getActivitiesByStatus(status);
        } else {
            activities = activityService.getAllActivities();
        }
        return ResponseEntity.ok(activities);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ActivityDTO> updateActivity(
            @PathVariable Long id,
            @RequestBody ActivityCreateRequest request) {
        ActivityDTO activity = activityService.updateActivity(id, request);
        return ResponseEntity.ok(activity);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteActivity(@PathVariable Long id) {
        activityService.deleteActivity(id);
        return ResponseEntity.noContent().build();
    }

}

