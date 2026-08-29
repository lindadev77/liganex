package tech.liganex.studio.module.openapp.dto;

import tech.liganex.studio.module.openapp.entity.Permission;

public record PermissionDTO(String code, String name, String description, boolean opened) {

    public static PermissionDTO from(Permission p) {
        return new PermissionDTO(p.getCode(), p.getName(), p.getDescription(),
                p.getOpened() != null && p.getOpened());
    }
}
