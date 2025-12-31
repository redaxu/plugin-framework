package com.gaoding.ska.customize.dto;

import java.util.Date;

/**
 * 创建活动请求DTO
 *
 * @author baiye
 * @since 2024/12/19
 */
public class ActivityCreateRequest {

    private String name;

    private String description;

    private Date startTime;

    private Date endTime;

    private Integer status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

}

