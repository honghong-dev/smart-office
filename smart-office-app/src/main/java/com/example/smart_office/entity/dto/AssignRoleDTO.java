package com.example.smart_office.entity.dto;

import lombok.Data;
import java.util.List;

@Data
public class AssignRoleDTO {
    private Long userId;
    private List<Long> roleIds;
}