package com.tgaws.business.sys.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限点实体（sys_permission）+ 树形节点（A18 权限树出参）。
 */
public class PermissionEntity {

    private Long id;
    private String permCode;
    private String permName;
    private Integer permType;
    private Long parentId;
    private String route;
    private Integer sort;
    /** 树形子节点（非表字段） */
    private List<PermissionEntity> children;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPermCode() { return permCode; }
    public void setPermCode(String permCode) { this.permCode = permCode; }
    public String getPermName() { return permName; }
    public void setPermName(String permName) { this.permName = permName; }
    public Integer getPermType() { return permType; }
    public void setPermType(Integer permType) { this.permType = permType; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public List<PermissionEntity> getChildren() { return children; }
    public void setChildren(List<PermissionEntity> children) { this.children = children; }

    /** 构建树：parentId=0 为根 */
    public static List<PermissionEntity> buildTree(List<PermissionEntity> flat) {
        List<PermissionEntity> roots = new ArrayList<>();
        for (PermissionEntity node : flat) {
            if (node.getParentId() == null || node.getParentId() == 0L) {
                roots.add(node);
                continue;
            }
            for (PermissionEntity parent : flat) {
                if (parent.getId().equals(node.getParentId())) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(node);
                    break;
                }
            }
        }
        return roots;
    }
}
