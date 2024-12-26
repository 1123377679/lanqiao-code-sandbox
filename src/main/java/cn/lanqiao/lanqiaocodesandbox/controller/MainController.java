package cn.lanqiao.lanqiaocodesandbox.controller;

import cn.lanqiao.lanqiaocodesandbox.JavaNativeCodeSandbox;
import cn.lanqiao.lanqiaocodesandbox.model.ExecuteCodeRequest;
import cn.lanqiao.lanqiaocodesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @ Author: 李某人
 * @ Date: 2024/12/25/0:56
 * @ Description:
 */
@RestController("/")
public class MainController {
    @Resource
    private JavaNativeCodeSandbox javaNativeCodeSandbox;
    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "auth";

    private static final String AUTH_REQUEST_SECRET = "secretKey";
    /**
     * 执行代码
     */
    @PostMapping("/executeCode")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        // 基本的认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null){
            throw new RuntimeException("请求参数为空");
        }
        return javaNativeCodeSandbox.executeCode(executeCodeRequest);
    }
}
