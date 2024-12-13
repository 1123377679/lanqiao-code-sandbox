package cn.lanqiao.lanqiaocodesandbox.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @ Author: 李某人
 * @ Date: 2024/12/10/21:43
 * @ Description:
 */
@RestController
public class HelloController {
    @RequestMapping("/hello")
    public String hello(){
        return "ok";
    }
}
