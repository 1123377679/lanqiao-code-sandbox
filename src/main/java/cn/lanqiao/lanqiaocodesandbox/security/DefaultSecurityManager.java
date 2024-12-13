package cn.lanqiao.lanqiaocodesandbox.security;

import java.security.Permission;

/**
 * @ Author: 李某人
 * @ Date: 2024/12/12/20:15
 * @ Description: 默认的安全管理器
 */
public class DefaultSecurityManager extends SecurityManager{
    /**
     * 检查所有的权限
     * @param perm   the requested permission.
     */
    @Override
    public void checkPermission(Permission perm) {
        System.out.println("默认不做任何处理");
        System.out.println(perm);
        // super.checkPermission(perm);
    }
}
